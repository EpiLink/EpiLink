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
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import org.epilink.bot.StandardErrorCodes.*
import org.epilink.bot.db.*
import org.epilink.bot.discord.LinkBanManager
import org.epilink.bot.http.*
import org.epilink.bot.http.data.*
import org.epilink.bot.toResponse
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.Instant
import java.util.*

/**
 * Component that implements administration routes (that is, routes under /admin/). Administration routes
 * not only require authentication (i.e. logged in user) but also proper authorization (i.e. is actually an admin)
 */
interface LinkAdminApi {
    /**
     * Install the admin routes into this route. Call this at the root: the implementation will take care of
     * putting itself under /api/v1/admin.
     */
    fun install(route: Route)
}

internal class LinkAdminApiImpl : LinkAdminApi, KoinComponent {
    private val sessionChecks: LinkSessionChecks by inject()
    private val dbf: LinkDatabaseFacade by inject()
    private val idAccessor: LinkIdAccessor by inject()
    private val banLogic: LinkBanLogic by inject()
    private val banManager: LinkBanManager by inject()

    override fun install(route: Route) {
        with(route) { admin() }
    }

    private fun Route.admin() = route("/api/v1/admin/") {
        @OptIn(UsesTrueIdentity::class) // checks if the "admin" has his true identity recorded
        intercept(ApplicationCallPipeline.Features) {
            if (sessionChecks.verifyUser(this) && sessionChecks.verifyAdmin(this)) {
                proceed()
            }
        }

        @ApiEndpoint("POST /api/v1/admin/idrequest")
        @OptIn(UsesTrueIdentity::class)
        post("idrequest") {
            val request = call.receive<IdRequest>()
            if (request.reason.isEmpty()) {
                call.respond(BadRequest, IncompleteAdminRequest.toResponse("Missing reason"))
                return@post
            }
            val admin = call.admin
            val target = dbf.getUser(request.target)
            when {
                target == null ->
                    call.respond(BadRequest, InvalidAdminRequest.toResponse("Target does not exist"))
                !dbf.isUserIdentifiable(target) ->
                    call.respond(BadRequest, TargetIsNotIdentifiable.toResponse())
                else -> {
                    // Get the identity of the admin
                    val adminTid = idAccessor.accessIdentity(
                        admin,
                        true,
                        "EpiLink Admin Service",
                        "You requested another user's identity: your identity was retrieved for logging purposes."
                    )
                    val userTid = idAccessor.accessIdentity(target, false, adminTid, request.reason)
                    call.respond(ApiSuccessResponse(data = IdRequestResult(request.target, userTid)))
                }
            }
        }

        @ApiEndpoint("GET /api/v1/admin/user/{targetId}")
        @OptIn(UsesTrueIdentity::class)
        get("user/{targetId}") {
            val targetId = call.parameters["targetId"]!!
            val user = dbf.getUser(targetId)
            if (user == null) {
                call.respond(NotFound, TargetUserDoesNotExist.toResponse())
            } else {
                val identifiable = dbf.isUserIdentifiable(user)
                val info = RegisteredUserInfo(
                    targetId,
                    user.msftIdHash.encodeUrlSafeBase64(),
                    user.creationDate.toString(),
                    identifiable
                )
                call.respond(ApiSuccessResponse(data = info))
            }
        }

        route("ban/{msftHash}") {
            @ApiEndpoint("GET /api/v1/admin/ban/{msftHash}")
            get {
                val msftHash = call.parameters["msftHash"]!!
                val msftHashBytes = Base64.getUrlDecoder().decode(msftHash)
                val bans = dbf.getBansFor(msftHashBytes)
                val data = UserBans(bans.any { banLogic.isBanActive(it) }, bans.map { it.toBanInfo() })
                call.respond(ApiSuccessResponse(data = data))
            }

            @ApiEndpoint("POST /api/v1/admin/ban/{msftHash}")
            @OptIn(UsesTrueIdentity::class) // Retrieves the ID of the admin
            post {
                val request: BanRequest = call.receive()
                val expiry = request.expiresOn?.let { runCatching { Instant.parse(it) }.getOrNull() }
                if (expiry == null && request.expiresOn != null) {
                    call.respond(BadRequest, InvalidInstant.toResponse("Invalid expiry timestamp."))
                } else {
                    val admin = call.admin
                    val identity = idAccessor.accessIdentity(
                        admin,
                        true,
                        "EpiLink Admin Service",
                        "You requested a ban on someone else: your identity was retrieved for logging purposes."
                    )
                    val result = banManager.ban(call.parameters["msftHash"]!!, expiry, identity, request.reason)
                    call.respond(OK, ApiSuccessResponse("Ban created.", result.toBanInfo()))
                }
            }

            route("{banId}") {
                @ApiEndpoint("GET /api/v1/admin/ban/{msftHash}/{banId}")
                get {
                    val msftHash = call.parameters["msftHash"]!!
                    val msftHashBytes = Base64.getUrlDecoder().decode(msftHash)
                    val banId = call.parameters["banId"]!!.toIntOrNull()
                    if (banId == null) {
                        call.respond(BadRequest, InvalidId.toResponse("Invalid ban ID format"))
                    } else {
                        val ban = dbf.getBan(banId)
                        when {
                            ban == null ->
                                call.respond(NotFound, InvalidId.toResponse("No ban with given ID found"))
                            !ban.msftIdHash.contentEquals(msftHashBytes) ->
                                call.respond(NotFound, InvalidId.toResponse("Microsoft ID hash does not correspond"))
                            else ->
                                call.respond(OK, ApiSuccessResponse(data = ban.toBanInfo()))
                        }
                    }
                }

                @ApiEndpoint("POST /api/v1/admin/ban/{msftHash}/{banId}/revoke")
                post("revoke") {
                    val msftHash = call.parameters["msftHash"]!!
                    val banId = call.parameters["banId"]!!.toIntOrNull()
                    if (banId == null) {
                        call.respond(BadRequest, InvalidId.toResponse("Invalid ban ID format"))
                    } else {
                        banManager.revokeBan(msftHash, banId)
                        call.respond(OK, apiSuccess("Ban revoked."))
                    }
                }
            }
        }
    }

    private fun LinkBan.toBanInfo(): BanInfo =
        BanInfo(banId, revoked, author, reason, issued.toString(), expiresOn?.toString())

    private fun ByteArray.encodeUrlSafeBase64() =
        Base64.getUrlEncoder().encodeToString(this)
}