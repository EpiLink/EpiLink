/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.http.endpoints

import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.header
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.delete
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.sessions.clear
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import org.epilink.bot.StandardErrorCodes
import org.epilink.bot.UserEndpointException
import org.epilink.bot.config.WebServerConfiguration
import org.epilink.bot.db.DatabaseFacade
import org.epilink.bot.db.IdentityManager
import org.epilink.bot.db.User
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.debug
import org.epilink.bot.discord.RoleManager
import org.epilink.bot.http.ApiEndpoint
import org.epilink.bot.http.ApiSuccessResponse
import org.epilink.bot.http.IdentityProvider
import org.epilink.bot.http.SessionChecker
import org.epilink.bot.http.apiSuccess
import org.epilink.bot.http.data.RegistrationAuthCode
import org.epilink.bot.http.data.UserInformation
import org.epilink.bot.http.sessions.ConnectedSession
import org.epilink.bot.http.sessions.RegisterSession
import org.epilink.bot.http.user
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.slf4j.LoggerFactory

/**
 * Route for user APIs
 */
interface UserApi {
    /**
     * Installs the route here (the route for "/api/v1" is automatically added, so you must call this at the
     * root route)
     */
    fun install(route: Route)

    /**
     * Logs in the caller with the given database user, username and avatar URL. Also clears the registration session
     * for this call, if any.
     */
    fun loginAs(call: ApplicationCall, user: User, username: String, avatar: String?)
}

@OptIn(KoinApiExtension::class)
internal class UserApiImpl : UserApi, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.api.user")
    private val roleManager: RoleManager by inject()
    private val idProvider: IdentityProvider by inject()
    private val sessionChecker: SessionChecker by inject()
    private val idManager: IdentityManager by inject()
    private val dbFacade: DatabaseFacade by inject()
    private val wsCfg: WebServerConfiguration by inject()
    private val admins: List<String> by inject(named("admins"))

    override fun install(route: Route) {
        with(route) { user() }
    }

    @Suppress("LongMethod")
    fun Route.user() = limitedRoute("/api/v1/user", wsCfg.rateLimitingProfile.userApi, {
        request.header("SessionId") ?: ""
    }) {
        intercept(ApplicationCallPipeline.Features) {
            if (sessionChecker.verifyUser(this)) proceed()
        }

        @ApiEndpoint("GET /api/v1/user")
        @OptIn(UsesTrueIdentity::class) // returns whether user is identifiable or not
        get {
            @Suppress("UnsafeCallOnNullableType") // session is already checked by interceptor
            val session = call.sessions.get<ConnectedSession>()!!
            logger.debug {
                "Returning user data session information for ${session.discordId} (${session.discordUsername})"
            }
            call.respond(HttpStatusCode.OK, ApiSuccessResponse.of(session.toUserInformation(call.user)))
        }

        @ApiEndpoint("GET /api/v1/user/idaccesslogs")
        get("idaccesslogs") {
            val user = call.user
            logger.info("Generating access logs for a user")
            logger.debug { "Generating access logs for ${user.discordId}" }
            call.respond(
                HttpStatusCode.OK,
                ApiSuccessResponse.of(idManager.getIdAccessLogs(user))
            )
        }

        @ApiEndpoint("POST /api/v1/user/logout")
        post("logout") {
            call.sessions.clear<ConnectedSession>()
            call.respond(HttpStatusCode.OK, apiSuccess("Successfully logged out.", "use.slo"))
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
            // Consume the authorization code, just in case
            val (guid, email) = idProvider.getUserIdentityInfo(auth.code, auth.redirectUri)
            if (dbFacade.isUserIdentifiable(user)) {
                throw UserEndpointException(StandardErrorCodes.IdentityAlreadyKnown)
            }
            idManager.relinkIdentity(user, email, guid)
            roleManager.invalidateAllRolesLater(user.discordId, true)
            call.respond(apiSuccess("Successfully linked Identity Provider account.", "use.slm"))
        }

        @ApiEndpoint("DELETE /api/v1/user/identity")
        @OptIn(UsesTrueIdentity::class)
        delete("identity") {
            val user = call.user
            if (dbFacade.isUserIdentifiable(user)) {
                idManager.deleteUserIdentity(user)
                roleManager.invalidateAllRolesLater(user.discordId)
                call.respond(apiSuccess("Successfully deleted identity", "use.sdi"))
            } else {
                throw UserEndpointException(StandardErrorCodes.IdentityAlreadyUnknown)
            }
        }
    }

    /**
     * Setup the sessions to log in the passed user object
     */
    override fun loginAs(
        call: ApplicationCall,
        user: User,
        username: String,
        avatar: String?
    ) = with(call) {
        logger.debug { "Logging ${user.discordId} ($username) in" }
        sessions.clear<RegisterSession>()
        sessions.set(ConnectedSession(user.discordId, username, avatar))
    }

    @UsesTrueIdentity
    private suspend fun ConnectedSession.toUserInformation(user: User) =
        UserInformation(
            discordId,
            discordUsername,
            discordAvatar,
            dbFacade.isUserIdentifiable(user),
            user.discordId in admins
        )
}
