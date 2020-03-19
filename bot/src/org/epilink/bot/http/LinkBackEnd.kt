package org.epilink.bot.http

import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.auth.OAuth2RequestParameters
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.content.TextContent
import io.ktor.http.*
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.sessions.clear
import io.ktor.sessions.getOrSet
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import org.epilink.bot.LinkServerEnvironment
import org.epilink.bot.config.LinkTokens
import org.epilink.bot.db.User
import org.epilink.bot.http.classes.InstanceInformation
import org.epilink.bot.http.classes.RegistrationAuthCode
import org.epilink.bot.http.classes.RegistrationContinuation
import org.epilink.bot.http.classes.RegistrationInformation
import org.epilink.bot.http.sessions.RegisterSession

/**
 * The back-end, defining API endpoints and more
 */
class LinkBackEnd(
    /**
     * The HTTP server that created this back end
     */
    private val server: LinkHttpServer,
    /**
     * The environment the back end lives in
     */
    private val env: LinkServerEnvironment,
    /**
     * The algorithm to use for JWT
     */
    private val jwtAlgorithm: Algorithm,
    /**
     * The duration of a session (for use with [makeJwt])
     */
    private val sessionDuration: Long,

    /**
     * Tokens
     */
    private val secrets: LinkTokens
) {
    // TODO config entry for custom tenant instead of just common
    private val authStubMsft =
        "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?" +
                listOf(
                    "client_id=${secrets.msftOAuthClientId}",
                    "response_type=code",
                    "prompt=select_account",
                    "scope=User.Read"
                ).joinToString("&")

    private val authStubDiscord =
        "https://discordapp.com/api/oauth2/authorize?" +
                listOf(
                    "response_type=code",
                    "client_id=${secrets.discordOAuthClientId}",
                    // Allows access to user information (w/o email address)
                    "scope=identify",
                    "prompt=consent"
                ).joinToString("&")

    private val client = HttpClient(Apache)

    /**
     * Defines the API endpoints. Served under /api/v1
     *
     * Anything responded in here SHOULD use [ApiResponse] in JSON form.
     */
    fun Route.epilinkApiV1() {
        route("meta") {
            @ApiEndpoint("GET /api/v1/meta/info")
            get("info") {
                call.respond(ApiResponse(true, data = getInstanceInformation()))
            }
        }

        /*
         * Just a hello world for now, answering in JSON
         */
        get("hello") {
            call.respond(
                ApiResponse(true, "Hello World", null)
            )
        }

        route("register") {
            @ApiEndpoint("GET /api/v1/register/info")
            get("info") {
                val session =
                    call.sessions.getOrSet { RegisterSession() }
                call.respondRegistrationStatus(session)
            }

            @ApiEndpoint("DELETE /api/v1/register")
            delete {
                call.sessions.clear<RegisterSession>()
                call.respond(HttpStatusCode.OK)
            }

            @ApiEndpoint("POST /register/authcode/discord")
            @ApiEndpoint("POST /register/authcode/msft")
            post("authcode/{service}") {
                val service =
                    call.parameters["service"]
                when (service) {
                    null -> error("Invalid service") // Should not happen
                    "discord" -> processDiscordAuthCode(call, call.receive())
                    "msft" -> processMicrosoftAuthCode(call, call.receive())
                    else -> call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse(false, "Invalid service: $service", null)
                    )
                }
            }

        }
    }

    private fun getInstanceInformation(): InstanceInformation =
        InstanceInformation(
            title = env.name,
            logo = null, // TODO add a cfg entry for the logo
            authorizeStub_msft = authStubMsft,
            authorizeStub_discord = authStubDiscord
        )

    private suspend fun processDiscordAuthCode(
        call: ApplicationCall,
        authcode: RegistrationAuthCode
    ) {
        val session = call.sessions.getOrSet { RegisterSession() }
        // Get token
        val token = getDiscordToken(authcode.code)
        // Get information
        val (id, username, avatarUrl) = getDiscordInfo(token)
        val user = env.database.getUser(id)
        if (user != null) {
            call.loginAs(user)
            call.respond(
                ApiResponse(
                    true, "Logged in", RegistrationContinuation(
                        next = "login",
                        attachment = null
                    )
                )
            )
        } else {
            val newSession = session.copy(
                discordUsername = username,
                discordId = id,
                discordAvatarUrl = avatarUrl
            )
            call.sessions.set(newSession)
            call.respond(
                ApiResponse(
                    true, "Connected to Discord", RegistrationContinuation(
                        next = "continue",
                        attachment = newSession.toRegistrationInformation()
                    )
                )
            )
        }
    }

    private suspend fun getDiscordToken(authcode: String): String {
        val res =
            client.post<String>("https://discordapp.com/api/v6/oauth2/token") {
                header(HttpHeaders.Accept, ContentType.Application.Json)
                body = TextContent(
                    ParametersBuilder().apply {
                        append("client_id", secrets.discordOAuthClientId!!)
                        append("client_secret", secrets.discordOAuthSecret!!)
                        append("grant_type", "authorization_code")
                        append("code", authcode)
                    }.build().formUrlEncode(),
                    ContentType.Application.FormUrlEncoded
                )
            }
        val data: Map<String, Any?> = ObjectMapper().readValue(res)
        return data["access_token"] as String?
            ?: error("Did not receive any access token from Discord")
    }

    private suspend fun getDiscordInfo(token: String): DiscordInfo {
        val data = client.getJson(
            "https://discordapp.com/api/v6/users/@me",
            bearer = token
        )
        val userid = data["id"] as String? ?: error("Missing Discord ID")
        val username =
            data["username"] as String? ?: error("Missing Discord username")
        val discriminator = data["discriminator"] as String?
            ?: error("Missing Discord discriminator")
        val displayableUsername = "$username#$discriminator"
        val avatarHash = data["avatar"] as String?
        val avatar =
            if (avatarHash != null)
                "https://cdn.discordapp.com/avatars/$userid/$avatarHash.png?size=256"
            else null
        return DiscordInfo(userid, displayableUsername, avatar)
    }

    private suspend fun processMicrosoftAuthCode(
        call: ApplicationCall,
        authcode: RegistrationAuthCode
    ) {
        val session = call.sessions.getOrSet { RegisterSession() }
        // Get token
        val token = getMicrosoftToken(authcode.code, authcode.redirectUri)
        // Get information
        val (id, email) = getMicrosoftInfo(token)
        // TODO check for banned user, already existing, etc.
        val newSession = session.copy(
            email = email,
            microsoftUid = id
        )
        call.sessions.set(newSession)
        call.respond(
            ApiResponse(
                true, "Connected to Microsoft", RegistrationContinuation(
                    next = "continue",
                    attachment = newSession.toRegistrationInformation()
                )
            )
        )
    }

    private suspend fun getMicrosoftToken(code: String, redirectUri: String): String {
        // TODO also inject tenant here once that's added instead of using common
        val res =
            client.post<String>("https://login.microsoftonline.com/common/oauth2/v2.0/token") {
                header(HttpHeaders.Accept, ContentType.Application.Json)
                body = TextContent(
                    ParametersBuilder().apply {
                        append("scope", "User.Read")
                        append("grant_type", "authorization_code")
                        append("client_id", secrets.msftOAuthClientId!!)
                        append("client_secret", secrets.msftOAuthSecret!!)
                        append("code", code)
                        append("redirect_uri", redirectUri)
                    }.build().formUrlEncode(),
                    ContentType.Application.FormUrlEncoded
                )
            }
        val data: Map<String, Any?> = ObjectMapper().readValue(res)
        return data["access_token"] as String?
            ?: error("Did not receive any access token from Microsoft")
    }

    private suspend fun getMicrosoftInfo(token: String): MicrosoftInfo {
        // https://graph.microsoft.com/v1.0/me
        val data = client.getJson(
            "https://graph.microsoft.com/v1.0/me",
            bearer = token
        )
        val email = data["mail"] as String?
            ?: (data["userPrincipalName"] as String?)?.takeIf { it.contains("@") }
            ?: error("User does not have an email address")
        val id = data["id"] as String? ?: error("User does not have an ID")
        return MicrosoftInfo(id, email)
    }

    private suspend fun ApplicationCall.loginAs(user: User) {
        sessions.clear<RegisterSession>()
        TODO()
    }

    private suspend fun ApplicationCall.respondRegistrationStatus(
        session: RegisterSession
    ) {
        respond(
            ApiResponse(
                success = true,
                data = session.toRegistrationInformation()
            )
        )
    }

    private fun RegisterSession.toRegistrationInformation() =
        RegistrationInformation(
            email = email,
            discordAvatarUrl = discordAvatarUrl,
            discordUsername = discordUsername
        )
}

private suspend fun HttpClient.getJson(
    url: String,
    bearer: String
): Map<String, Any?> {
    val result = get<String>(url) {
        header("Authorization", "Bearer $bearer")
    }
    return ObjectMapper().readValue(result)
}
