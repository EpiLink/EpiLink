/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.http.endpoints

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import org.epilink.bot.StandardErrorCodes.*
import org.epilink.bot.config.WebServerConfiguration
import org.epilink.bot.db.*
import org.epilink.bot.discord.BanManager
import org.epilink.bot.discord.RoleManager
import org.epilink.bot.http.*
import org.epilink.bot.http.data.*
import org.epilink.bot.toResponse
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant
import java.util.*

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

        @ApiEndpoint("POST /api/v1/admin/idrequest")
        @OptIn(UsesTrueIdentity::class)
        post("idrequest") {
            val request = call.receive<IdRequest>()
            if (request.reason.isEmpty()) {
                call.respond(BadRequest, IncompleteAdminRequest.toResponse("Missing reason.", "adm.mir"))
                return@post
            }
            val admin = call.admin
            val target = dbf.getUser(request.target)
            when {
                target == null ->
                    call.respond(BadRequest, TargetUserDoesNotExist.toResponse())
                !dbf.isUserIdentifiable(target) ->
                    call.respond(BadRequest, TargetIsNotIdentifiable.toResponse())
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

        route("user/{targetId}") {
            @ApiEndpoint("GET /api/v1/admin/user/{targetId}")
            @OptIn(UsesTrueIdentity::class)
            get {
                val targetId = call.parameters["targetId"]!!
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
            delete {
                val targetId = call.parameters["targetId"]!!
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

        route("ban/{idpHash}") {
            @ApiEndpoint("GET /api/v1/admin/ban/{idpHash}")
            get {
                val idpHash = call.parameters["idpHash"]!!
                val idpHashBytes = Base64.getUrlDecoder().decode(idpHash)
                val bans = dbf.getBansFor(idpHashBytes)
                val data = UserBans(bans.any { banLogic.isBanActive(it) }, bans.map { it.toBanInfo() })
                call.respond(ApiSuccessResponse.of(data))
            }

            @ApiEndpoint("POST /api/v1/admin/ban/{idpHash}")
            @OptIn(UsesTrueIdentity::class) // Retrieves the ID of the admin
            post {
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
                    val result = banManager.ban(call.parameters["idpHash"]!!, expiry, identity, request.reason)
                    call.respond(OK, ApiSuccessResponse.of(result.toBanInfo()))
                }
            }

            route("{banId}") {
                @ApiEndpoint("GET /api/v1/admin/ban/{idpHash}/{banId}")
                get {
                    val idpHash = call.parameters["idpHash"]!!
                    val idpHashBytes = Base64.getUrlDecoder().decode(idpHash)
                    val banId = call.parameters["banId"]!!.toIntOrNull()
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
                post("revoke") {
                    val idpHash = call.parameters["idpHash"]!!
                    val banId = call.parameters["banId"]!!.toIntOrNull()
                    if (banId == null) {
                        call.respond(BadRequest, InvalidId.toResponse("Invalid ban ID format", "adm.ibi"))
                    } else {
                        banManager.revokeBan(idpHash, banId)
                        call.respond(OK, apiSuccess("Ban revoked.", "adm.brk"))
                    }
                }
            }
        }

        @OptIn(UsesTrueIdentity::class)
        post("gdprreport/{targetId}") {
            val targetId = call.parameters["targetId"]!!
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

        route("search") {
            get("hash16/{searchTerm}") {
                val targetId = call.parameters["searchTerm"]!!.lowercase()
                if (targetId.any { it !in hexCharacters }) {
                    call.respond(BadRequest, InvalidAdminRequest.toResponse("Invalid hex string", "adm.ihs"))
                } else {
                    val results = dbf.searchUserByPartialHash(targetId).map { it.discordId }
                    call.respond(OK, ApiSuccessResponse.of(data = mapOf("results" to results)))
                }
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
