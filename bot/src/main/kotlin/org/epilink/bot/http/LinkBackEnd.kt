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
import io.ktor.http.content.TextContent
import io.ktor.jackson.jackson
import io.ktor.request.ContentTransformationException
import io.ktor.request.header
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.sessions.*
import kotlinx.coroutines.coroutineScope
import org.epilink.bot.*
import org.epilink.bot.StandardErrorCodes.*
import org.epilink.bot.config.LinkWebServerConfiguration
import org.epilink.bot.db.Disallowed
import org.epilink.bot.db.LinkServerDatabase
import org.epilink.bot.db.LinkUser
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.discord.LinkRoleManager
import org.epilink.bot.http.data.*
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
}

/**
 * The back-end, defining API endpoints and more
 */
internal class LinkBackEndImpl : LinkBackEnd, KoinComponent {

    private val logger = LoggerFactory.getLogger("epilink.api")

    /**
     * The environment the back end lives in
     */
    private val env: LinkServerEnvironment by inject()

    private val db: LinkServerDatabase by inject()

    private val roleManager: LinkRoleManager by inject()

    private val discordBackEnd: LinkDiscordBackEnd by inject()

    private val microsoftBackEnd: LinkMicrosoftBackEnd by inject()

    private val storageProvider: SessionStorageProvider by inject()

    private val legal: LinkLegalTexts by inject()

    private val wsCfg: LinkWebServerConfiguration by inject()

    override fun Application.epilinkApiModule() {
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
                storageProvider.createStorage("el_reg_")
            )
            header<ConnectedSession>(
                "SessionId",
                storageProvider.createStorage("el_ses_")
            )
        }

        install(RateLimiting)

        routing {
            route("/api/v1") {
                epilinkApiV1()
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

        route("meta") {
            rateLimited(limit = 50, timeBeforeReset = Duration.ofMinutes(1)) {
                @ApiEndpoint("GET /api/v1/meta/info")
                get("info") {
                    call.respond(ApiSuccessResponse(data = getInstanceInformation()))
                }

                @ApiEndpoint("GET /api/v1/meta/tos")
                get("tos") {
                    call.respond(HttpStatusCode.OK, TextContent(legal.tosText, ContentType.Text.Html))
                }

                @ApiEndpoint("GET /api/v1/meta/privacy")
                get("privacy") {
                    call.respond(HttpStatusCode.OK, TextContent(legal.policyText, ContentType.Text.Html))
                }
            }
        }

        route("user") {
            rateLimited(limit = 20, timeBeforeReset = Duration.ofMinutes(1)) {
                intercept(ApplicationCallPipeline.Features) {
                    if (call.sessions.get<ConnectedSession>() == null) {
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
                    roleManager.updateRolesOnAllGuildsLater(session.discordId)
                    call.respond(apiSuccess("Successfully relinked Microsoft account"))
                }

                @ApiEndpoint("DELETE /api/v1/user/identity")
                @OptIn(UsesTrueIdentity::class)
                delete("identity") {
                    val session = call.sessions.get<ConnectedSession>()!!
                    if (db.isUserIdentifiable(session.discordId)) {
                        db.deleteUserIdentity(session.discordId)
                        roleManager.updateRolesOnAllGuildsLater(session.discordId)
                        call.respond(apiSuccess("Successfully deleted identity"))
                    } else {
                        throw LinkEndpointException(IdentityAlreadyUnknown, isEndUserAtFault = true)
                    }
                }
            }
        }

        route("register") {
            rateLimited(limit = 10, timeBeforeReset = Duration.ofMinutes(1)) {
                @ApiEndpoint("GET /api/v1/register/info")
                get("info") {
                    val session = call.sessions.getOrSet { RegisterSession() }
                    call.respondRegistrationStatus(session)
                }

                @ApiEndpoint("DELETE /api/v1/register")
                delete {
                    logger.debug { "Clearing registry session ${call.request.header("RegistrationSessionId")}" }
                    call.sessions.clear<RegisterSession>()
                    call.respond(HttpStatusCode.OK)
                }

                @ApiEndpoint("POST /api/v1/register")
                post {
                    with(call.sessions.get<RegisterSession>()) {
                        if (this == null) {
                            logger.debug {
                                "Missing/unknown session header for call from reg. session ${call.request.header("RegistrationSessionId")}"
                            }
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ApiErrorResponse("Missing session header", IncompleteRegistrationRequest.toErrorData())
                            )
                        } else if (discordId == null || discordUsername == null || email == null || microsoftUid == null) {
                            logger.debug {
                                """
                            Incomplete registration process for session ${call.request.header("RegistrationSessionId")}
                            discordId = $discordId
                            discordUsername = $discordUsername
                            email = $email
                            microsoftUid = $microsoftUid
                            """.trimIndent()
                            }
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ApiErrorResponse(
                                    "Incomplete registration process",
                                    IncompleteRegistrationRequest.toErrorData()
                                )
                            )
                        } else {
                            val regSessionId = call.request.header("RegistrationSessionId")
                            logger.debug { "Completing registration session for $regSessionId" }
                            val options: AdditionalRegistrationOptions =
                                call.receiveCatching() ?: return@post
                            val u = db.createUser(discordId, microsoftUid, email, options.keepIdentity)
                            roleManager.updateRolesOnAllGuildsLater(u.discordId)
                            call.loginAs(u, discordUsername, discordAvatarUrl)
                            logger.debug { "Completed registration session. $regSessionId logged in and reg session cleared." }
                            call.respond(HttpStatusCode.Created, apiSuccess("Account created, logged in."))
                        }
                    }
                }

                @ApiEndpoint("POST /register/authcode/discord")
                @ApiEndpoint("POST /register/authcode/msft")
                post("authcode/{service}") {
                    when (val service = call.parameters["service"]) {
                        null -> error("Invalid service") // Should not happen
                        "discord" -> processDiscordAuthCode(call, call.receive())
                        "msft" -> processMicrosoftAuthCode(call, call.receive())
                        else -> {
                            logger.debug { "Attempted to register under unknown service $service" }
                            call.respond(
                                HttpStatusCode.NotFound,
                                ApiErrorResponse("Invalid service: $service", UnknownService.toErrorData())
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend inline fun <reified T : Any> ApplicationCall.receiveCatching(): T? = try {
        receive()
    } catch (ex: ContentTransformationException) {
        logger.error("Incorrect input from user", ex)
        null
    }

    /**
     * Create an [InstanceInformation] object based on this back end's environment and configuration
     */
    private fun getInstanceInformation(): InstanceInformation =
        InstanceInformation(
            title = env.name,
            logo = null, // TODO add a cfg entry for the logo
            authorizeStub_msft = microsoftBackEnd.getAuthorizeStub(),
            authorizeStub_discord = discordBackEnd.getAuthorizeStub(),
            idPrompt = legal.idPrompt,
            footerUrls = wsCfg.footers
        )

    /**
     * Take a Discord authorization code, consume it and apply the information retrieve form it to the current
     * registration session
     */
    private suspend fun processDiscordAuthCode(call: ApplicationCall, authcode: RegistrationAuthCode) {
        val session = call.sessions.getOrSet { RegisterSession() }
        // Get token
        logger.debug { "Get Discord token from authcode ${authcode.code}" }
        val token = discordBackEnd.getDiscordToken(authcode.code, authcode.redirectUri)
        // Get information
        val (id, username, avatarUrl) = discordBackEnd.getDiscordInfo(token)
        logger.debug { "Processing Discord info for registration for $id ($username)" }
        val user = db.getUser(id)
        if (user != null) {
            logger.debug { "User already exists: logging in" }
            call.loginAs(user, username, avatarUrl)
            call.respond(ApiSuccessResponse("Logged in", RegistrationContinuation("login", null)))
        } else {
            val adv = db.isDiscordUserAllowedToCreateAccount(id)
            if (adv is Disallowed) {
                logger.debug { "Discord user $id cannot create account: " + adv.reason }
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiErrorResponse(adv.reason, AccountCreationNotAllowed.toErrorData())
                )
                return
            }
            logger.debug { "Discord registration information OK: continue " }
            val newSession = session.copy(discordUsername = username, discordId = id, discordAvatarUrl = avatarUrl)
            call.sessions.set(newSession)
            call.respond(
                ApiSuccessResponse(
                    "Connected to Discord",
                    RegistrationContinuation("continue", newSession.toRegistrationInformation())
                )
            )
        }
    }

    /**
     * Take a Microsoft authorization code, consume it and apply the information retrieve form it to the current
     * registration session
     */
    private suspend fun processMicrosoftAuthCode(call: ApplicationCall, authcode: RegistrationAuthCode) {
        val session = call.sessions.getOrSet { RegisterSession() }
        logger.debug { "Get Microsoft token from authcode ${authcode.code}" }
        // Get token
        val token = microsoftBackEnd.getMicrosoftToken(authcode.code, authcode.redirectUri)
        // Get information
        val (id, email) = microsoftBackEnd.getMicrosoftInfo(token)
        logger.debug { "Processing Microsoft info for registration for $id ($email)" }
        val adv = db.isMicrosoftUserAllowedToCreateAccount(id, email)
        if (adv is Disallowed) {
            logger.debug { "Microsoft user $id ($email) is not allowed to create an account: " + adv.reason }
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorResponse(adv.reason, AccountCreationNotAllowed.toErrorData())
            )
            return
        }
        logger.debug { "Microsoft registration information OK: continue " }
        val newSession = session.copy(email = email, microsoftUid = id)
        call.sessions.set(newSession)
        call.respond(
            ApiSuccessResponse(
                "Connected to Microsoft",
                RegistrationContinuation("continue", newSession.toRegistrationInformation())
            )
        )
    }

    /**
     * Setup the sessions to log in the passed user object
     */
    private fun ApplicationCall.loginAs(user: LinkUser, username: String, avatar: String?) {
        logger.debug { "Logging ${user.discordId} ($username) in" }
        sessions.clear<RegisterSession>()
        sessions.set(ConnectedSession(user.discordId, username, avatar))
    }

    private suspend fun ApplicationCall.respondRegistrationStatus(session: RegisterSession) {
        respond(ApiSuccessResponse(null, session.toRegistrationInformation()))
    }

    /**
     * Utility function for transforming a session into the JSON object that is returned by the back-end's APIs
     */
    private fun RegisterSession.toRegistrationInformation() =
        RegistrationInformation(email = email, discordAvatarUrl = discordAvatarUrl, discordUsername = discordUsername)

    @UsesTrueIdentity
    private suspend fun ConnectedSession.toUserInformation() =
        UserInformation(discordId, discordUsername, discordAvatar, db.isUserIdentifiable(discordId))
}

/**
 * Utility function for appending classic OAuth parameters to a ParametersBuilder object all at once.
 */
fun ParametersBuilder.appendOauthParameters(
    clientId: String, secret: String, authcode: String, redirectUri: String
) {
    append("grant_type", "authorization_code")
    append("client_id", clientId)
    append("client_secret", secret)
    append("code", authcode)
    append("redirect_uri", redirectUri)
}

/**
 * Utility function for performing a GET on the given url (with the given bearer as the "Authorization: Bearer" header),
 * turning the JSON result into a map and returning that map.
 */
suspend fun HttpClient.getJson(url: String, bearer: String): Map<String, Any?> {
    val result = get<String>(url) {
        header("Authorization", "Bearer $bearer")
        header(HttpHeaders.Accept, ContentType.Application.Json)
    }
    return ObjectMapper().readValue(result)
}
