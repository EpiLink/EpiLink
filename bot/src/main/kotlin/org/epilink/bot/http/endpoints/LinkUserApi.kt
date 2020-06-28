/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.http.endpoints

import guru.zoroark.ratelimit.rateLimited
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.sessions.clear
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import org.epilink.bot.LinkEndpointException
import org.epilink.bot.StandardErrorCodes
import org.epilink.bot.db.*
import org.epilink.bot.debug
import org.epilink.bot.discord.LinkRoleManager
import org.epilink.bot.http.*
import org.epilink.bot.http.data.RegistrationAuthCode
import org.epilink.bot.http.data.UserInformation
import org.epilink.bot.http.sessions.ConnectedSession
import org.epilink.bot.http.sessions.RegisterSession
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Route for user APIs
 */
interface LinkUserApi {
    /**
     * Installs the route here (the route for "/api/v1" is automatically added, so you must call this at the
     * root route)
     */
    fun install(route: Route)

    /**
     * Logs in the caller with the given database user, username and avatar URL. Also clears the registration session
     * for this call, if any.
     */
    fun loginAs(call: ApplicationCall, user: LinkUser, username: String, avatar: String?)
}

internal class LinkUserApiImpl : LinkUserApi, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.api.user")
    private val roleManager: LinkRoleManager by inject()
    private val microsoftBackEnd: LinkMicrosoftBackEnd by inject()
    private val sessionChecks: LinkSessionChecks by inject()
    private val idManager: LinkIdManager by inject()
    private val dbFacade: LinkDatabaseFacade by inject()

    override fun install(route: Route) {
        with(route) { user() }
    }

    fun Route.user() = route("/api/v1/user") {
        rateLimited(limit = 20, timeBeforeReset = Duration.ofMinutes(1)) {
            intercept(ApplicationCallPipeline.Features) {
                if (sessionChecks.verifyUser(this)) proceed()
            }

            @ApiEndpoint("GET /api/v1/user")
            @OptIn(UsesTrueIdentity::class) // returns whether user is identifiable or not
            get {
                val session = call.sessions.get<ConnectedSession>()!!
                logger.debug { "Returning user data session information for ${session.discordId} (${session.discordUsername})" }
                call.respond(HttpStatusCode.OK, ApiSuccessResponse(data = session.toUserInformation(call.user)))
            }

            @ApiEndpoint("GET /api/v1/user/idaccesslogs")
            get("idaccesslogs") {
                val user = call.user
                logger.info("Generating access logs for a user")
                logger.debug { "Generating access logs for ${user.discordId}" }
                call.respond(
                    HttpStatusCode.OK,
                    ApiSuccessResponse(data = idManager.getIdAccessLogs(user))
                )
            }

            @ApiEndpoint("POST /api/v1/user/logout")
            post("logout") {
                call.sessions.clear<ConnectedSession>()
                call.respond(HttpStatusCode.OK, apiSuccess("Successfully logged out"))
            }

            @ApiEndpoint("POST /api/v1/user/identity")
            @OptIn(UsesTrueIdentity::class)
            post("identity") {
                val auth = call.receive<RegistrationAuthCode>()
                logger.info("Relinking a user account")
                val user = call.user
                logger.debug {
                    "User ${user.discordId} has asked for a relink with authcode ${auth.code}."
                }
                val microsoftToken = microsoftBackEnd.getMicrosoftToken(auth.code, auth.redirectUri)
                if (dbFacade.isUserIdentifiable(user)) {
                    throw LinkEndpointException(StandardErrorCodes.IdentityAlreadyKnown, isEndUserAtFault = true)
                }
                val userInfo = microsoftBackEnd.getMicrosoftInfo(microsoftToken)
                idManager.relinkMicrosoftIdentity(user, userInfo.email, userInfo.guid)
                roleManager.invalidateAllRoles(user.discordId, true)
                call.respond(apiSuccess("Successfully relinked Microsoft account"))
            }

            @ApiEndpoint("DELETE /api/v1/user/identity")
            @OptIn(UsesTrueIdentity::class)
            delete("identity") {
                val user = call.user
                if (dbFacade.isUserIdentifiable(user)) {
                    idManager.deleteUserIdentity(user)
                    roleManager.invalidateAllRoles(user.discordId)
                    call.respond(apiSuccess("Successfully deleted identity"))
                } else {
                    throw LinkEndpointException(StandardErrorCodes.IdentityAlreadyUnknown, isEndUserAtFault = true)
                }
            }
        }
    }

    /**
     * Setup the sessions to log in the passed user object
     */
    override fun loginAs(
        call: ApplicationCall,
        user: LinkUser,
        username: String,
        avatar: String?
    ) = with(call) {
        logger.debug { "Logging ${user.discordId} ($username) in" }
        sessions.clear<RegisterSession>()
        sessions.set(ConnectedSession(user.discordId, username, avatar))
    }

    @UsesTrueIdentity
    private suspend fun ConnectedSession.toUserInformation(user: LinkUser) =
        UserInformation(discordId, discordUsername, discordAvatar, dbFacade.isUserIdentifiable(user))
}