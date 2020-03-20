package org.epilink.bot.http

import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.content.TextContent
import io.ktor.http.*
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.*
import io.ktor.sessions.*
import org.epilink.bot.LinkException
import org.epilink.bot.LinkServerEnvironment
import org.epilink.bot.config.LinkTokens
import org.epilink.bot.db.Disallowed
import org.epilink.bot.db.User
import org.epilink.bot.http.classes.*
import org.epilink.bot.http.sessions.ConnectedSession
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
    private val authStubMsft = "https://login.microsoftonline.com/${secrets.msftTenant}/oauth2/v2.0/authorize?" +
            listOf(
                "client_id=${secrets.msftOAuthClientId}",
                "response_type=code",
                "prompt=select_account",
                "scope=User.Read"
            ).joinToString("&")

    private val authStubDiscord = "https://discordapp.com/api/oauth2/authorize?" +
            listOf(
                "response_type=code",
                "client_id=${secrets.discordOAuthClientId}",
                // Allows access to user information (w/o email address)
                "scope=identify",
                "prompt=consent"
            ).joinToString("&")

    /**
     * The HTTP client that is used by most actions when other servers need to be contacted for information (e.g. OAuth)
     */
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

        route("user") {
            intercept(ApplicationCallPipeline.Features) {
                if (call.sessions.get<ConnectedSession>() == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@intercept finish()
                }
                proceed()
            }

            @ApiEndpoint("GET /api/v1/user")
            get {
                val session = call.sessions.get<ConnectedSession>()!!
                call.respondText("Connected as " + session.discordId, ContentType.Text.Plain)
            }
        }

        route("register") {
            @ApiEndpoint("GET /api/v1/register/info")
            get("info") {
                val session = call.sessions.getOrSet { RegisterSession() }
                call.respondRegistrationStatus(session)
            }

            @ApiEndpoint("DELETE /api/v1/register")
            delete {
                call.sessions.clear<RegisterSession>()
                call.respond(HttpStatusCode.OK)
            }

            @ApiEndpoint("POST /api/v1/register")
            post {
                val session = call.sessions.get<RegisterSession>()
                with(session) {
                    if (this == null)
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse(false, "Missing session header", null)
                        )
                    else if (discordId == null || email == null || microsoftUid == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse(false, "Incomplete registration process", session)
                        )
                    } else {
                        val options: AdditionalRegistrationOptions =
                            call.receive()
                        try {
                            val u = env.database.createUser(this, options.keepIdentity)
                            call.loginAs(u)
                            call.respond(ApiResponse(true, "Account created, logged in."))
                        } catch (e: LinkException) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse(false, e.message)
                            )
                        }
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
                    else -> call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse(false, "Invalid service: $service", null)
                    )
                }
            }

        }
    }

    /**
     * Create an [InstanceInformation] object based on this back end's environment and configuration
     */
    private fun getInstanceInformation(): InstanceInformation =
        InstanceInformation(
            title = env.name,
            logo = null, // TODO add a cfg entry for the logo
            authorizeStub_msft = authStubMsft,
            authorizeStub_discord = authStubDiscord
        )

    /**
     * Take a Discord authorization code, consume it and apply the information retrieve form it to the current
     * registration session
     */
    private suspend fun processDiscordAuthCode(call: ApplicationCall, authcode: RegistrationAuthCode) {
        val session = call.sessions.getOrSet { RegisterSession() }
        // Get token
        val token = getDiscordToken(authcode.code, authcode.redirectUri)
        // Get information
        val (id, username, avatarUrl) = getDiscordInfo(token)
        val user = env.database.getUser(id)
        if (user != null) {
            call.loginAs(user)
            call.respond(ApiResponse(true, "Logged in", RegistrationContinuation("login", null)))
        } else {
            val newSession = session.copy(discordUsername = username, discordId = id, discordAvatarUrl = avatarUrl)
            call.sessions.set(newSession)
            call.respond(
                ApiResponse(
                    true,
                    "Connected to Discord",
                    RegistrationContinuation("continue", newSession.toRegistrationInformation())
                )
            )
        }
    }

    /**
     * Consume the authcode and return a token.
     *
     * @param authcode The authorization code to consume
     * @param redirectUri The redirect_uri that was used in the authorization request that returned the authorization
     * code
     */
    private suspend fun getDiscordToken(authcode: String, redirectUri: String): String {
        val res = client.post<String>("https://discordapp.com/api/v6/oauth2/token") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            body = TextContent(
                ParametersBuilder().apply {
                    appendOauthParameters(
                        secrets.discordOAuthClientId!!,
                        secrets.discordOAuthSecret!!,
                        authcode,
                        redirectUri
                    )
                }.build().formUrlEncode(),
                ContentType.Application.FormUrlEncoded
            )
        }
        val data: Map<String, Any?> = ObjectMapper().readValue(res)
        return data["access_token"] as String? ?: error("Did not receive any access token from Discord")
    }

    /**
     * Retrieve a user's own information using a Discord OAuth2 token.
     */
    private suspend fun getDiscordInfo(token: String): DiscordInfo {
        val data = client.getJson("https://discordapp.com/api/v6/users/@me", bearer = token)
        val userid = data["id"] as String? ?: error("Missing Discord ID")
        val username = data["username"] as String? ?: error("Missing Discord username")
        val discriminator = data["discriminator"] as String? ?: error("Missing Discord discriminator")
        val displayableUsername = "$username#$discriminator"
        val avatarHash = data["avatar"] as String?
        val avatar =
            if (avatarHash != null) "https://cdn.discordapp.com/avatars/$userid/$avatarHash.png?size=256" else null
        return DiscordInfo(userid, displayableUsername, avatar)
    }

    /**
     * Take a Microsoft authorization code, consume it and apply the information retrieve form it to the current
     * registration session
     */
    private suspend fun processMicrosoftAuthCode(call: ApplicationCall, authcode: RegistrationAuthCode) {
        val session = call.sessions.getOrSet { RegisterSession() }
        // Get token
        val token = getMicrosoftToken(authcode.code, authcode.redirectUri)
        // Get information
        val (id, email) = getMicrosoftInfo(token)
        val adv = env.database.isAllowedToCreateAccount(null, id)
        if (adv is Disallowed) {
            call.respond(ApiResponse(false, adv.reason, null))
            return
        }
        val newSession = session.copy(email = email, microsoftUid = id)
        call.sessions.set(newSession)
        call.respond(
            ApiResponse(
                true,
                "Connected to Microsoft",
                RegistrationContinuation("continue", newSession.toRegistrationInformation())
            )
        )
    }

    /**
     * Consume the authcode and return a token.
     *
     * @param authcode The authorization code to consume
     * @param redirectUri The redirect_uri that was used in the authorization request that returned the authorization
     * code
     */
    private suspend fun getMicrosoftToken(code: String, redirectUri: String): String {
        val res = client.post<String>("https://login.microsoftonline.com/${secrets.msftTenant}/oauth2/v2.0/token") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            body = TextContent(
                ParametersBuilder().apply {
                    append("scope", "User.Read")
                    appendOauthParameters(secrets.msftOAuthClientId!!, secrets.msftOAuthSecret!!, code, redirectUri)
                }.build().formUrlEncode(),
                ContentType.Application.FormUrlEncoded
            )
        }
        val data: Map<String, Any?> = ObjectMapper().readValue(res)
        return data["access_token"] as String? ?: error("Did not receive any access token from Microsoft")
    }

    /**
     * Retrieve a user's own information with Microsoft Graph using a Microsoft OAuth2 token.
     */
    private suspend fun getMicrosoftInfo(token: String): MicrosoftInfo {
        // https://graph.microsoft.com/v1.0/me
        val data = client.getJson("https://graph.microsoft.com/v1.0/me", bearer = token)
        val email = data["mail"] as String?
            ?: (data["userPrincipalName"] as String?)?.takeIf { it.contains("@") }
            ?: error("User does not have an email address")
        val id = data["id"] as String? ?: error("User does not have an ID")
        return MicrosoftInfo(id, email)
    }

    /**
     * Setup the sessions to log in the passed user object
     */
    private fun ApplicationCall.loginAs(user: User) {
        sessions.clear<RegisterSession>()
        sessions.set(ConnectedSession(user.discordId))
    }

    private suspend fun ApplicationCall.respondRegistrationStatus(session: RegisterSession) {
        respond(ApiResponse(success = true, data = session.toRegistrationInformation()))
    }

    /**
     * Utility function for transforming a session into the JSON object that is returned by the back-end's APIs
     */
    private fun RegisterSession.toRegistrationInformation() =
        RegistrationInformation(email = email, discordAvatarUrl = discordAvatarUrl, discordUsername = discordUsername)
}

/**
 * Utility function for appending classic OAuth parameters to a ParametersBuilder object all at once.
 */
private fun ParametersBuilder.appendOauthParameters(
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
private suspend fun HttpClient.getJson(url: String, bearer: String): Map<String, Any?> {
    val result = get<String>(url) {
        header("Authorization", "Bearer $bearer")
        header(HttpHeaders.Accept, ContentType.Application.Json)
    }
    return ObjectMapper().readValue(result)
}
