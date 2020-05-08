/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.http

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.application.*
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.ParametersBuilder
import io.ktor.jackson.jackson
import io.ktor.request.header
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.sessions.*
import kotlinx.coroutines.coroutineScope
import org.epilink.bot.*
import org.epilink.bot.StandardErrorCodes.*
import org.epilink.bot.db.LinkServerDatabase
import org.epilink.bot.db.LinkUser
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.discord.LinkRoleManager
import org.epilink.bot.http.data.RegistrationAuthCode
import org.epilink.bot.http.data.UserInformation
import org.epilink.bot.http.endpoints.LinkMetaApi
import org.epilink.bot.http.endpoints.LinkRegistrationApi
import org.epilink.bot.http.sessions.ConnectedSession
import org.epilink.bot.http.sessions.RegisterSession
import org.epilink.bot.ratelimiting.RateLimiting
import org.epilink.bot.ratelimiting.rateLimited
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Interface for the back-end
 */
interface LinkBackEnd {
    /**
     * Ktor module for the back-end API
     */
    fun Application.epilinkApiModule()

    fun ApplicationCall.loginAs(user: LinkUser, username: String, avatar: String?)
}

/**
 * The back-end, defining API endpoints and more
 */
internal class LinkBackEndImpl : LinkBackEnd, KoinComponent {

    private val logger = LoggerFactory.getLogger("epilink.api")

    private val db: LinkServerDatabase by inject()

    private val roleManager: LinkRoleManager by inject()

    private val microsoftBackEnd: LinkMicrosoftBackEnd by inject()

    private val cacheClient: CacheClient by inject()

    private val registrationApi: LinkRegistrationApi by inject()

    private val metaApi: LinkMetaApi by inject()

    override fun Application.epilinkApiModule() {
        // TODO put feature initialization in a separate function
        /*
         * Used for automatically converting stuff to JSON when calling
         * "respond" with generic objects.
         */
        install(ContentNegotiation) {
            jackson {}
        }

        /*
         * Used for sessions
         */
        install(Sessions) {
            header<RegisterSession>(
                "RegistrationSessionId",
                cacheClient.newSessionStorage("el_reg_")
            )
            header<ConnectedSession>(
                "SessionId",
                cacheClient.newSessionStorage("el_ses_")
            )
        }

        install(RateLimiting)

        routing {
            route("/api/v1") {
                epilinkApiV1()
                registrationApi.install(this)
                metaApi.install(this)
            }
        }
    }

    /**
     * Defines the API endpoints. Served under /api/v1
     *
     * Anything responded in here SHOULD use [ApiResponse] in JSON form.
     */
    private fun Route.epilinkApiV1() {

        // Make sure that exceptions are not left for Ktor to try and figure out.
        // Ktor would figure it out, but we a) need to log them b) respond with an ApiResponse
        intercept(ApplicationCallPipeline.Monitoring) {
            try {
                coroutineScope {
                    proceed()
                }
            } catch (ex: LinkEndpointException) {
                if (ex.isEndUserAtFault) {
                    logger.info("Encountered an endpoint exception ${ex.errorCode.description}", ex)
                    call.respond(HttpStatusCode.BadRequest, ex.toApiResponse())
                } else {
                    logger.error("Encountered a back-end caused endpoint exception (${ex.errorCode}", ex)
                    call.respond(HttpStatusCode.InternalServerError, ex.toApiResponse())
                }
            } catch (ex: Exception) {
                logger.error(
                    "Uncaught exception encountered while processing v1 API call. Catch it and return a proper thing!",
                    ex
                )
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiErrorResponse("An unknown error occurred. Please report this.", UnknownError.toErrorData())
                )
            }
        }

        route("user") {
            rateLimited(limit = 20, timeBeforeReset = Duration.ofMinutes(1)) {
                intercept(ApplicationCallPipeline.Features) {
                    val session = call.sessions.get<ConnectedSession>()
                    if (session == null || db.getUser(session.discordId) == null /* see #121 */) {
                        call.sessions.clear<ConnectedSession>()
                        logger.info("Attempted access with no or invalid SessionId (${call.request.header("SessionId")})")
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            ApiErrorResponse("You are not authenticated.", MissingAuthentication.toErrorData())
                        )
                        return@intercept finish()
                    }
                    proceed()
                }

                @ApiEndpoint("GET /api/v1/user")
                @OptIn(UsesTrueIdentity::class) // returns whether user is identifiable or not
                get {
                    val session = call.sessions.get<ConnectedSession>()!!
                    logger.debug { "Returning user data session information for ${session.discordId} (${session.discordUsername})" }
                    call.respond(HttpStatusCode.OK, ApiSuccessResponse(data = session.toUserInformation()))
                }

                @ApiEndpoint("GET /api/v1/user/idaccesslogs")
                get("idaccesslogs") {
                    val session = call.sessions.get<ConnectedSession>()!!
                    logger.info("Generating access logs for a user")
                    logger.debug { "Generating access logs for ${session.discordId} (${session.discordUsername})" }
                    call.respond(HttpStatusCode.OK, ApiSuccessResponse(data = db.getIdAccessLogs(session.discordId)))
                }

                @ApiEndpoint("POST /api/v1/user/logout")
                post("logout") {
                    call.sessions.clear<ConnectedSession>()
                    call.respond(HttpStatusCode.OK, apiSuccess("Successfully logged out"))
                }

                @ApiEndpoint("POST /api/v1/user/identity")
                @OptIn(UsesTrueIdentity::class)
                post("identity") {
                    val session = call.sessions.get<ConnectedSession>()!!
                    val auth = call.receive<RegistrationAuthCode>()
                    logger.info("Relinking a user account")
                    logger.debug {
                        "User ${session.discordId} (${session.discordUsername}) has asked for a relink with authcode ${auth.code}."
                    }
                    val microsoftToken = microsoftBackEnd.getMicrosoftToken(auth.code, auth.redirectUri)
                    if (db.isUserIdentifiable(session.discordId)) {
                        throw LinkEndpointException(IdentityAlreadyKnown, isEndUserAtFault = true)
                    }
                    val userInfo = microsoftBackEnd.getMicrosoftInfo(microsoftToken)
                    db.relinkMicrosoftIdentity(session.discordId, userInfo.email, userInfo.guid)
                    roleManager.invalidateAllRoles(session.discordId)
                    call.respond(apiSuccess("Successfully relinked Microsoft account"))
                }

                @ApiEndpoint("DELETE /api/v1/user/identity")
                @OptIn(UsesTrueIdentity::class)
                delete("identity") {
                    val session = call.sessions.get<ConnectedSession>()!!
                    if (db.isUserIdentifiable(session.discordId)) {
                        db.deleteUserIdentity(session.discordId)
                        roleManager.invalidateAllRoles(session.discordId)
                        call.respond(apiSuccess("Successfully deleted identity"))
                    } else {
                        throw LinkEndpointException(IdentityAlreadyUnknown, isEndUserAtFault = true)
                    }
                }
            }
        }
    }

    /**
     * Setup the sessions to log in the passed user object
     */
    override fun ApplicationCall.loginAs(user: LinkUser, username: String, avatar: String?) {
        logger.debug { "Logging ${user.discordId} ($username) in" }
        sessions.clear<RegisterSession>()
        sessions.set(ConnectedSession(user.discordId, username, avatar))
    }

    @UsesTrueIdentity
    private suspend fun ConnectedSession.toUserInformation() =
        UserInformation(discordId, discordUsername, discordAvatar, db.isUserIdentifiable(discordId))
}
