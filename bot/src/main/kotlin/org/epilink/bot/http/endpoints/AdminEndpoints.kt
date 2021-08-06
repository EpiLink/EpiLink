/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.http.endpoints

import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.locations.Location
import io.ktor.locations.delete
import io.ktor.locations.get
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import org.epilink.bot.StandardErrorCodes
import org.epilink.bot.StandardErrorCodes.InvalidAdminRequest
import org.epilink.bot.StandardErrorCodes.InvalidId
import org.epilink.bot.StandardErrorCodes.InvalidInstant
import org.epilink.bot.StandardErrorCodes.TargetUserDoesNotExist
import org.epilink.bot.config.WebServerConfiguration
import org.epilink.bot.db.Ban
import org.epilink.bot.db.BanLogic
import org.epilink.bot.db.DatabaseFacade
import org.epilink.bot.db.GdprReport
import org.epilink.bot.db.IdentityManager
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.discord.BanManager
import org.epilink.bot.discord.RoleManager
import org.epilink.bot.http.ApiEndpoint
import org.epilink.bot.http.ApiSuccessResponse
import org.epilink.bot.http.SessionChecker
import org.epilink.bot.http.admin
import org.epilink.bot.http.apiSuccess
import org.epilink.bot.http.data.BanInfo
import org.epilink.bot.http.data.BanRequest
import org.epilink.bot.http.data.IdRequest
import org.epilink.bot.http.data.IdRequestResult
import org.epilink.bot.http.data.RegisteredUserInfo
import org.epilink.bot.http.data.UserBans
import org.epilink.bot.toResponse
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant
import java.util.Base64
import io.ktor.locations.post as postl

/**
 * Component that implements administration routes (that is, routes under /admin/). Administration routes
 * not only require authentication (i.e. logged in user) but also proper authorization (i.e. is actually an admin)
 */
interface AdminEndpoints {
    /**
     * Install the admin routes into this route. Call this at the root: the implementation will take care of
     * putting itself under /api/v1/admin.
     */
    fun install(route: Route)
}

private val hexCharacters = setOf('a', 'b', 'c', 'd', 'e', 'f', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0')

@Location("user/{targetId}")
data class UserByTargetId(val targetId: String)

@Location("ban/{idpHash}")
data class BansByIdpHash(val idpHash: String) {
    @Location("{banId}")
    data class Ban(val parent: BansByIdpHash, val banId: String) {
        @Location("revoke")
        // TODO banId may be replaceable by an int directly here?
        data class Revoke(val parent: Ban)
    }
}

@Location("gdprreport/{targetIdp}")
data class GdprReportLocation(val targetIdp: String)

@Location("search/hash16/{searchTerm}")
data class SearchByHash(val searchTerm: String)

@OptIn(KoinApiExtension::class)
internal class AdminEndpointsImpl : AdminEndpoints, KoinComponent {
    private val sessionChecker: SessionChecker by inject()
    private val dbf: DatabaseFacade by inject()
    private val idManager: IdentityManager by inject()
    private val banLogic: BanLogic by inject()
    private val banManager: BanManager by inject()
    private val gdprReport: GdprReport by inject()
    private val roleManager: RoleManager by inject()
    private val wsCfg: WebServerConfiguration by inject()

    override fun install(route: Route) {
        with(route) { admin() }
    }

    private fun Route.admin() = limitedRoute("/api/v1/admin/", wsCfg.rateLimitingProfile.adminApi) {
        @OptIn(UsesTrueIdentity::class) // checks if the "admin" has his true identity recorded
        intercept(ApplicationCallPipeline.Features) {
            if (sessionChecker.verifyUser(this) && sessionChecker.verifyAdmin(this)) {
                proceed()
            }
        }

        idRequest()
        user()
        ban()
        gdprReport()
        search()
    }

    private fun Route.idRequest() {
        @ApiEndpoint("POST /api/v1/admin/idrequest")
        @OptIn(UsesTrueIdentity::class)
        post("idrequest") {
            val request = call.receive<IdRequest>()
            if (request.reason.isEmpty()) {
                call.respond(
                    BadRequest,
                    StandardErrorCodes.IncompleteAdminRequest.toResponse("Missing reason.", "adm.mir")
                )
                return@post
            }
            val admin = call.admin
            val target = dbf.getUser(request.target)
            when {
                target == null ->
                    call.respond(BadRequest, StandardErrorCodes.TargetUserDoesNotExist.toResponse())
                !dbf.isUserIdentifiable(target) ->
                    call.respond(BadRequest, StandardErrorCodes.TargetIsNotIdentifiable.toResponse())
                else -> {
                    // Get the identity of the admin
                    val adminTid = idManager.accessIdentity(
                        admin,
                        true,
                        "EpiLink Admin Service",
                        "You requested another user's identity: your identity was retrieved for logging purposes."
                    )
                    val userTid = idManager.accessIdentity(target, false, adminTid, request.reason)
                    call.respond(ApiSuccessResponse.of(IdRequestResult(request.target, userTid)))
                }
            }
        }
    }

    private fun Route.user() {
        @ApiEndpoint("GET /api/v1/admin/user/{targetId}")
        @OptIn(UsesTrueIdentity::class)
        get<UserByTargetId> { userByTargetId ->
            val targetId = userByTargetId.targetId
            val user = dbf.getUser(targetId)
            if (user == null) {
                call.respond(NotFound, TargetUserDoesNotExist.toResponse())
            } else {
                val identifiable = dbf.isUserIdentifiable(user)
                val info = RegisteredUserInfo(
                    targetId,
                    user.idpIdHash.encodeUrlSafeBase64(),
                    user.creationDate.toString(),
                    identifiable
                )
                call.respond(ApiSuccessResponse.of(info))
            }
        }

        @ApiEndpoint("DELETE /api/v1/admin/user/{targetId}")
        delete<UserByTargetId> { userByTargetId ->
            val targetId = userByTargetId.targetId
            val user = dbf.getUser(targetId)
            if (user == null) {
                call.respond(NotFound, TargetUserDoesNotExist.toResponse())
            } else {
                dbf.deleteUser(user)
                roleManager.invalidateAllRolesLater(targetId)
                call.respond(apiSuccess("User deleted", "adm.ud"))
            }
        }
    }

    private fun Route.ban() {
        @ApiEndpoint("GET /api/v1/admin/ban/{idpHash}")
        get<BansByIdpHash> { bbih ->
            val idpHash = bbih.idpHash
            val idpHashBytes = Base64.getUrlDecoder().decode(idpHash)
            val bans = dbf.getBansFor(idpHashBytes)
            val data = UserBans(bans.any { banLogic.isBanActive(it) }, bans.map { it.toBanInfo() })
            call.respond(ApiSuccessResponse.of(data))
        }

        @ApiEndpoint("POST /api/v1/admin/ban/{idpHash}")
        @OptIn(UsesTrueIdentity::class) // Retrieves the ID of the admin
        postl<BansByIdpHash> { banByIdHash ->
            val request: BanRequest = call.receive()
            val expiry = request.expiresOn?.let { runCatching { Instant.parse(it) }.getOrNull() }
            if (expiry == null && request.expiresOn != null) {
                call.respond(BadRequest, InvalidInstant.toResponse("Invalid expiry timestamp.", "adm.iet"))
            } else {
                val admin = call.admin
                val identity = idManager.accessIdentity(
                    admin,
                    true,
                    "EpiLink Admin Service",
                    "You requested a ban on someone else: your identity was retrieved for logging purposes."
                )
                val result = banManager.ban(banByIdHash.idpHash, expiry, identity, request.reason)
                call.respond(OK, ApiSuccessResponse.of(result.toBanInfo()))
            }
        }

        @ApiEndpoint("GET /api/v1/admin/ban/{idpHash}/{banId}")
        get<BansByIdpHash.Ban> { banLocation ->
            val idpHash = banLocation.parent.idpHash
            val idpHashBytes = Base64.getUrlDecoder().decode(idpHash)
            val banId = banLocation.banId.toIntOrNull()
            if (banId == null) {
                call.respond(BadRequest, InvalidId.toResponse("Invalid ban ID.", "adm.ibi"))
            } else {
                val ban = dbf.getBan(banId)
                when {
                    ban == null ->
                        call.respond(
                            NotFound,
                            InvalidId.toResponse("No ban with given ID found.", "adm.nbi")
                        )
                    !ban.idpIdHash.contentEquals(idpHashBytes) ->
                        call.respond(
                            NotFound,
                            InvalidId.toResponse(
                                "Identity Provider ID hash does not correspond.",
                                "adm.hnc"
                            )
                        )
                    else ->
                        call.respond(OK, ApiSuccessResponse.of(ban.toBanInfo()))
                }
            }
        }

        @ApiEndpoint("POST /api/v1/admin/ban/{idpHash}/{banId}/revoke")
        postl<BansByIdpHash.Ban.Revoke> { revoke ->
            val idpHash = revoke.parent.parent.idpHash
            val banId = revoke.parent.banId.toIntOrNull()
            if (banId == null) {
                call.respond(BadRequest, InvalidId.toResponse("Invalid ban ID format", "adm.ibi"))
            } else {
                banManager.revokeBan(idpHash, banId)
                call.respond(OK, apiSuccess("Ban revoked.", "adm.brk"))
            }
        }
    }

    @OptIn(UsesTrueIdentity::class)
    private fun Route.gdprReport() {
        postl<GdprReportLocation> { location ->
            val targetId = location.targetIdp
            val target = dbf.getUser(targetId)
            if (target == null) {
                call.respond(NotFound, TargetUserDoesNotExist.toResponse())
            } else {
                val adminId = idManager.accessIdentity(
                    call.admin,
                    true,
                    "EpiLink Admin Service",
                    "Your identity was retrieved for logging purposes because you requested a GDPR report on someone."
                )
                val report = gdprReport.getFullReport(target, adminId)
                call.respond(TextContent(report, ContentType.Text.Markdown, OK))
            }
        }
    }

    private fun Route.search() {
        get<SearchByHash> { location ->
            val targetId = location.searchTerm.lowercase()
            if (targetId.any { it !in hexCharacters }) {
                call.respond(BadRequest, InvalidAdminRequest.toResponse("Invalid hex string", "adm.ihs"))
            } else {
                val results = dbf.searchUserByPartialHash(targetId).map { it.discordId }
                call.respond(OK, ApiSuccessResponse.of(data = mapOf("results" to results)))
            }
        }
    }

    private fun Ban.toBanInfo(): BanInfo =
        BanInfo(banId, revoked, author, reason, issued.toString(), expiresOn?.toString())

    private fun ByteArray.encodeUrlSafeBase64() =
        Base64.getUrlEncoder().encodeToString(this)
}

private val markdownContentType = ContentType("text", "markdown")

/**
 * Markdown (text/markdown) content type
 */
@Suppress("unused")
val ContentType.Text.Markdown: ContentType
    get() = markdownContentType
