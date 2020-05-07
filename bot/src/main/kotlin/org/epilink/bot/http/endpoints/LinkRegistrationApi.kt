package org.epilink.bot.http.endpoints

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.features.ContentTransformationException
import io.ktor.request.header
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.sessions.*
import org.epilink.bot.StandardErrorCodes
import org.epilink.bot.db.Disallowed
import org.epilink.bot.db.LinkServerDatabase
import org.epilink.bot.debug
import org.epilink.bot.discord.LinkRoleManager
import org.epilink.bot.http.*
import org.epilink.bot.http.data.AdditionalRegistrationOptions
import org.epilink.bot.http.data.RegistrationAuthCode
import org.epilink.bot.http.data.RegistrationContinuation
import org.epilink.bot.http.data.RegistrationInformation
import org.epilink.bot.http.sessions.RegisterSession
import org.epilink.bot.ratelimiting.rateLimited
import org.epilink.bot.toErrorData
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.slf4j.LoggerFactory
import java.time.Duration

interface LinkRegistrationApi {
    fun install(route: Route)
}

class LinkRegistrationApiImpl : LinkRegistrationApi, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.api")

    private val discordBackEnd: LinkDiscordBackEnd by inject()

    private val microsoftBackEnd: LinkMicrosoftBackEnd by inject()

    private val db: LinkServerDatabase by inject()

    private val roleManager: LinkRoleManager by inject()

    private val back: LinkBackEnd by inject()

    override fun install(route: Route) =
        with(route) { registration() }

    private fun Route.registration() {
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
                                ApiErrorResponse(
                                    "Missing session header",
                                    StandardErrorCodes.IncompleteRegistrationRequest.toErrorData()
                                )
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
                                    StandardErrorCodes.IncompleteRegistrationRequest.toErrorData()
                                )
                            )
                        } else {
                            val regSessionId = call.request.header("RegistrationSessionId")
                            logger.debug { "Completing registration session for $regSessionId" }
                            val options: AdditionalRegistrationOptions =
                                call.receiveCatching() ?: return@post
                            val u = db.createUser(discordId, microsoftUid, email, options.keepIdentity)
                            roleManager.invalidateAllRoles(u.discordId)
                            with(back) { call.loginAs(u, discordUsername, discordAvatarUrl) }
                            logger.debug { "Completed registration session. $regSessionId logged in and reg session cleared." }
                            call.respond(HttpStatusCode.Created,
                                apiSuccess("Account created, logged in.")
                            )
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
                                ApiErrorResponse(
                                    "Invalid service: $service",
                                    StandardErrorCodes.UnknownService.toErrorData()
                                )
                            )
                        }
                    }
                }
            }
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
                ApiErrorResponse(
                    adv.reason,
                    StandardErrorCodes.AccountCreationNotAllowed.toErrorData()
                )
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
            with(back) { call.loginAs(user, username, avatarUrl) }
            call.respond(
                ApiSuccessResponse(
                    "Logged in",
                    RegistrationContinuation("login", null)
                )
            )
        } else {
            val adv = db.isDiscordUserAllowedToCreateAccount(id)
            if (adv is Disallowed) {
                logger.debug { "Discord user $id cannot create account: " + adv.reason }
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiErrorResponse(
                        adv.reason,
                        StandardErrorCodes.AccountCreationNotAllowed.toErrorData()
                    )
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

    private suspend fun ApplicationCall.respondRegistrationStatus(session: RegisterSession) {
        respond(ApiSuccessResponse(null, session.toRegistrationInformation()))
    }

    private suspend inline fun <reified T : Any> ApplicationCall.receiveCatching(): T? = try {
        receive()
    } catch (ex: ContentTransformationException) {
        logger.error("Incorrect input from user", ex)
        null
    }

    /**
     * Utility function for transforming a session into the JSON object that is returned by the back-end's APIs
     */
    private fun RegisterSession.toRegistrationInformation() =
        RegistrationInformation(email = email, discordAvatarUrl = discordAvatarUrl, discordUsername = discordUsername)
}