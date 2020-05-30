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
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import org.epilink.bot.StandardErrorCodes
import org.epilink.bot.StandardErrorCodes.*
import org.epilink.bot.db.*
import org.epilink.bot.http.ApiEndpoint
import org.epilink.bot.http.ApiSuccessResponse
import org.epilink.bot.http.LinkSessionChecks
import org.epilink.bot.http.data.*
import org.epilink.bot.http.sessions.ConnectedSession
import org.epilink.bot.toResponse
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*

interface LinkAdminApi {
    fun install(route: Route)
}

internal class LinkAdminApiImpl : LinkAdminApi, KoinComponent {
    private val sessionChecks: LinkSessionChecks by inject()
    private val db: LinkServerDatabase by inject()
    private val dbf: LinkDatabaseFacade by inject()
    private val idAccessor: LinkIdAccessor by inject()
    private val banLogic: LinkBanLogic by inject()
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
            val session = call.sessions.get<ConnectedSession>()!! // Already validated by interception
            val target = db.getUser(request.target)
            when {
                target == null ->
                    call.respond(BadRequest, InvalidAdminRequest.toResponse("Target does not exist"))
                !db.isUserIdentifiable(request.target) ->
                    call.respond(BadRequest, TargetIsNotIdentifiable.toResponse())
                else -> {
                    // Get the identity of the admin
                    val adminTid = idAccessor.accessIdentity(
                        session.discordId,
                        true,
                        "EpiLink Admin Service",
                        "You requested another user's identity: your identity was retrieved for logging purposes."
                    )
                    val userTid = idAccessor.accessIdentity(request.target, false, adminTid, request.reason)
                    call.respond(ApiSuccessResponse(data = IdRequestResult(request.target, userTid)))
                }
            }
        }

        @ApiEndpoint("GET /api/v1/admin/user/{targetId}")
        @OptIn(UsesTrueIdentity::class)
        get("user/{targetId}") {
            val targetId = call.parameters["targetId"]!!
            val user = db.getUser(targetId)
            if (user == null) {
                call.respond(NotFound, TargetUserDoesNotExist.toResponse())
            } else {
                val identifiable = db.isUserIdentifiable(targetId)
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
            @ApiEndpoint("GET /api/v1/admin/user/{msftHash}")
            get {
                val msftHash = call.parameters["msftHash"]!!
                val msftHashBytes = Base64.getUrlDecoder().decode(msftHash)
                val bans = dbf.getBansFor(msftHashBytes)
                val data = UserBans(bans.any { banLogic.isBanActive(it) }, bans.map { it.toBanInfo() })
                call.respond(ApiSuccessResponse(data = data))
            }
        }
    }

    private fun LinkBan.toBanInfo(): BanInfo =
        BanInfo(banId, revoked, author, reason, issued.toString(), expiresOn?.toString())

    private fun ByteArray.encodeUrlSafeBase64() =
        Base64.getUrlEncoder().encodeToString(this)
}