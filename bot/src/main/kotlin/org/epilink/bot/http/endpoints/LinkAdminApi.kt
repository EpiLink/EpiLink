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
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import org.epilink.bot.*
import org.epilink.bot.StandardErrorCodes.InsufficientPermissions
import org.epilink.bot.db.LinkServerDatabase
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.discord.LinkDiscordClientFacade
import org.epilink.bot.discord.LinkDiscordMessages
import org.epilink.bot.http.*
import org.epilink.bot.http.data.IdRequest
import org.epilink.bot.http.data.IdRequestResult
import org.epilink.bot.http.sessions.ConnectedSession
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.core.qualifier.named

interface LinkAdminApi {
    fun install(route: Route)
}

internal class LinkAdminApiImpl : LinkAdminApi, KoinComponent {
    private val sessionChecks: LinkSessionChecks by inject()
    private val db: LinkServerDatabase by inject()
    private val discord: LinkDiscordClientFacade by inject()
    private val messages: LinkDiscordMessages by inject()
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
            val session = call.sessions.get<ConnectedSession>()!! // Already validated by interception
            val admin = db.getUser(session.discordId)!! // Already validated by interception
            val target = db.getUser(request.target)
            when {
                target == null ->
                    call.respond(BadRequest, StandardErrorCodes.InvalidAdminRequest.toResponse("Target does not exist"))
                !db.isUserIdentifiable(request.target) ->
                    call.respond(BadRequest, StandardErrorCodes.TargetIsNotIdentifiable.toResponse()) // TODO test
                request.reason.isEmpty() ->
                    call.respond(BadRequest, StandardErrorCodes.IncompleteAdminRequest.toResponse("Missing reason")) // TODO test
                else -> {
                    // Get the identity of the admin
                    // TODO put the id access mechanism in a separate injectable class
                    val adminTid = db.accessIdentity(admin, true, "EpiLink Admin Service", "You requested another user's identity: your identity was retrieved for logging purposes.")
                    messages.getIdentityAccessEmbed(true, "EpiLink Admin Service", "You requested another user's identity: your identity was retrieved for logging purposes.")?.let {
                        discord.sendDirectMessage(session.discordId, it)
                    }
                    // Get the identity and notify
                    val userTid = db.accessIdentity(target, false, adminTid, request.reason)
                    messages.getIdentityAccessEmbed(false, adminTid, request.reason)?.let {
                        discord.sendDirectMessage(request.target, it)
                    }
                    call.respond(ApiSuccessResponse(data = IdRequestResult(request.target, userTid)))
                }
            }
        }
    }
}