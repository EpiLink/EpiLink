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
import io.ktor.application.call
import io.ktor.features.ContentTransformationException
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
import io.ktor.sessions.getOrSet
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import org.epilink.bot.StandardErrorCodes.AccountCreationNotAllowed
import org.epilink.bot.StandardErrorCodes.IncompleteRegistrationRequest
import org.epilink.bot.StandardErrorCodes.UnknownService
import org.epilink.bot.config.WebServerConfiguration
import org.epilink.bot.db.DatabaseFacade
import org.epilink.bot.db.Disallowed
import org.epilink.bot.db.PermissionChecks
import org.epilink.bot.db.UserCreator
import org.epilink.bot.debug
import org.epilink.bot.discord.RoleManager
import org.epilink.bot.http.ApiEndpoint
import org.epilink.bot.http.ApiSuccessResponse
import org.epilink.bot.http.DiscordBackEnd
import org.epilink.bot.http.IdentityProvider
import org.epilink.bot.http.apiSuccess
import org.epilink.bot.http.data.AdditionalRegistrationOptions
import org.epilink.bot.http.data.RegistrationAuthCode
import org.epilink.bot.http.data.RegistrationContinuation
import org.epilink.bot.http.data.RegistrationInformation
import org.epilink.bot.http.sessions.RegisterSession
import org.epilink.bot.toResponse
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

/**
 * Route for the registration endpoints
 */
interface RegistrationApi {
    /**
     * Installs the route here (the route for "/api/v1" is automatically added, so you must call this at the
     * root route)
     */
    fun install(route: Route)
}

@OptIn(KoinApiExtension::class)
internal class RegistrationApiImpl : RegistrationApi, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.api.registration")
    private val discordBackEnd: DiscordBackEnd by inject()
    private val idProvider: IdentityProvider by inject()
    private val roleManager: RoleManager by inject()
    private val userApi: UserApi by inject()
    private val userCreator: UserCreator by inject()
    private val perms: PermissionChecks by inject()
    private val dbFacade: DatabaseFacade by inject()
    private val wsCfg: WebServerConfiguration by inject()

    override fun install(route: Route) {
        with(route) { registration() }
    }

    @Suppress("LongMethod")
    private fun Route.registration() = limitedRoute("/api/v1/register", wsCfg.rateLimitingProfile.registrationApi) {
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
        @Suppress("ComplexCondition") // No other choice, otherwise null checks get messy
        post {
            with(call.sessions.get<RegisterSession>()) {
                if (this == null) {
                    logger.debug {
                        "Missing/unknown session header for call from reg. session " +
                            call.request.header("RegistrationSessionId")
                    }
                    call.respond(
                        HttpStatusCode.BadRequest,
                        IncompleteRegistrationRequest.toResponse(
                            "Missing session header",
                            "reg.msh"
                        )
                    )
                } else if (discordId == null || discordUsername == null || email == null || idpId == null) {
                    logger.debug {
                        """
                            Incomplete registration process for session ${call.request.header("RegistrationSessionId")}
                            discordId = $discordId
                            discordUsername = $discordUsername
                            email = $email
                            idpId = $idpId
                        """.trimIndent()
                    }
                    call.respond(
                        HttpStatusCode.BadRequest,
                        IncompleteRegistrationRequest.toResponse()
                    )
                } else {
                    val regSessionId = call.request.header("RegistrationSessionId")
                    logger.debug { "Completing registration session for $regSessionId" }
                    val options: AdditionalRegistrationOptions =
                        call.receiveCatching() ?: return@post
                    val u = userCreator.createUser(discordId, idpId, email, options.keepIdentity)
                    roleManager.invalidateAllRolesLater(u.discordId, true)
                    with(userApi) { loginAs(call, u, discordUsername, discordAvatarUrl) }
                    logger.debug { "Completed registration session. $regSessionId logged in and reg session cleared." }
                    call.respond(
                        HttpStatusCode.Created,
                        apiSuccess("Account created, logged in.", "reg.acc")
                    )
                }
            }
        }

        @ApiEndpoint("POST /register/authcode/discord")
        @ApiEndpoint("POST /register/authcode/idProvider")
        post("authcode/{service}") {
            when (val service = call.parameters["service"]) {
                null -> error("Invalid service") // Should not happen
                "discord" -> processDiscordAuthCode(call, call.receive())
                "idProvider" -> processIdProviderAuthCode(call, call.receive())
                else -> {
                    logger.debug { "Attempted to register under unknown service $service" }
                    call.respond(
                        HttpStatusCode.NotFound,
                        UnknownService.toResponse(
                            "Invalid service: $service",
                            "reg.isv",
                            mapOf("service" to service)
                        )
                    )
                }
            }
        }
    }

    /**
     * Take a Identity Provider authorization code, consume it and apply the information retrieve form it to the current
     * registration session
     */
    private suspend fun processIdProviderAuthCode(call: ApplicationCall, authcode: RegistrationAuthCode) {
        val session = call.sessions.getOrSet { RegisterSession() }
        logger.debug { "Get IDP identity token from authcode ${authcode.code}" }
        // Get information
        val (id, email) = idProvider.getUserIdentityInfo(authcode.code, authcode.redirectUri)
        logger.debug { "Processing IDP info for registration for $id ($email)" }
        val adv = perms.isIdentityProviderUserAllowedToCreateAccount(id, email)
        if (adv is Disallowed) {
            logger.debug { "IDP user $id ($email) is not allowed to create an account: " + adv.reason }
            call.respond(
                HttpStatusCode.BadRequest,
                AccountCreationNotAllowed.toResponse(adv.reason, adv.reasonI18n, adv.reasonI18nData)
            )
            return
        }
        logger.debug { "IDP registration information OK: continue " }
        val newSession = session.copy(email = email, idpId = id)
        call.sessions.set(newSession)
        call.respond(
            ApiSuccessResponse.of(RegistrationContinuation("continue", newSession.toRegistrationInformation()))
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
        val user = dbFacade.getUser(id)
        if (user != null) {
            logger.debug { "User already exists: logging in" }
            with(userApi) { loginAs(call, user, username, avatarUrl) }
            call.respond(
                ApiSuccessResponse.of(
                    "Logged in",
                    "reg.lgi",
                    data = RegistrationContinuation("login", null)
                )
            )
        } else {
            val adv = perms.isDiscordUserAllowedToCreateAccount(id)
            if (adv is Disallowed) {
                logger.debug { "Discord user $id cannot create account: " + adv.reason }
                call.respond(
                    HttpStatusCode.BadRequest,
                    AccountCreationNotAllowed.toResponse(adv.reason, adv.reasonI18n, adv.reasonI18nData)
                )
                return
            }
            logger.debug { "Discord registration information OK: continue " }
            val newSession = session.copy(discordUsername = username, discordId = id, discordAvatarUrl = avatarUrl)
            call.sessions.set(newSession)
            call.respond(
                ApiSuccessResponse.of(RegistrationContinuation("continue", newSession.toRegistrationInformation()))
            )
        }
    }

    private suspend fun ApplicationCall.respondRegistrationStatus(session: RegisterSession) {
        respond(ApiSuccessResponse.of(session.toRegistrationInformation()))
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
