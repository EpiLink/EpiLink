package org.epilink.bot.http

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.features.ClientRequestException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.ParametersBuilder
import io.ktor.http.formUrlEncode
import org.epilink.bot.LinkEndpointException
import org.epilink.bot.StandardErrorCodes.DiscordApiFailure
import org.epilink.bot.StandardErrorCodes.InvalidAuthCode
import org.koin.core.KoinComponent
import org.koin.core.inject

/**
 * The back-end, specifically for interacting with the Discord API
 */
class LinkDiscordBackEnd(
    private val clientId: String,
    private val secret: String
) : KoinComponent {
    private val client: HttpClient by inject()

    private val authStubDiscord = "https://discordapp.com/api/oauth2/authorize?" +
            listOf(
                "response_type=code",
                "client_id=${clientId}",
                // Allows access to user information (w/o email address)
                "scope=identify",
                "prompt=consent"
            ).joinToString("&")

    /**
     * Consume the authcode and return a token.
     *
     * @param authcode The authorization code to consume
     * @param redirectUri The redirect_uri that was used in the authorization request that returned the authorization
     * code
     * @throws LinkEndpointException If something wrong happens while contacting the Discord APIs.
     */
    suspend fun getDiscordToken(authcode: String, redirectUri: String): String {
        val res = runCatching {
            client.post<String>("https://discordapp.com/api/v6/oauth2/token") {
                header(HttpHeaders.Accept, ContentType.Application.Json)
                body = TextContent(
                    ParametersBuilder().apply {
                        appendOauthParameters(clientId, secret, authcode, redirectUri)
                        append("scope", "identify")
                    }.build().formUrlEncode(),
                    ContentType.Application.FormUrlEncoded
                )
            }
        }.getOrElse { ex ->
            if (ex is ClientRequestException) {
                val data = ObjectMapper().readValue<Map<String, Any?>>(ex.response.call.receive<String>())
                when (val error = data["error"] as? String) {
                    "invalid_grant" -> throw LinkEndpointException(
                        InvalidAuthCode,
                        "Invalid authorization code",
                        true,
                        ex
                    )
                    else -> throw LinkEndpointException(
                        DiscordApiFailure,
                        "Discord OAuth failed: $error (" + (data["error_description"] ?: "no description") + ")",
                        false,
                        ex
                    )
                }
            } else {
                throw LinkEndpointException(
                    DiscordApiFailure,
                    "Failed to contact Discord API for obtaining token.",
                    ex is ClientRequestException && ex.response.status.value == 400, ex
                )
            }
        }
        val data: Map<String, Any?> = ObjectMapper().readValue(res)
        return data["access_token"] as String?
            ?: throw LinkEndpointException(DiscordApiFailure, "Did not receive any access token from Discord")
    }

    /**
     * Retrieve a user's own information using a Discord OAuth2 token.
     *
     * @throws LinkEndpointException If something wrong happens while contacting the Discord APIs
     */
    suspend fun getDiscordInfo(token: String): DiscordUserInfo {
        val data = runCatching {
            client.getJson("https://discordapp.com/api/v6/users/@me", bearer = token)
        }.getOrElse {
            throw LinkEndpointException(
                DiscordApiFailure, "Failed to contact Discord servers for user information retrieval.",
                false,
                it
            )
        }
        val userId = data["id"] as String?
            ?: throw LinkEndpointException(DiscordApiFailure, "Missing Discord ID in Discord API response")
        val username = data["username"] as String?
            ?: throw LinkEndpointException(DiscordApiFailure, "Missing Discord username in Discord API response")
        val discriminator = data["discriminator"] as String?
            ?: throw LinkEndpointException(DiscordApiFailure, "Missing Discord discriminator in Discord API response")
        val displayableUsername = "$username#$discriminator"
        val avatarHash = data["avatar"] as String?
        val avatar =
            if (avatarHash != null) "https://cdn.discordapp.com/avatars/$userId/$avatarHash.png?size=256" else null
        return DiscordUserInfo(userId, displayableUsername, avatar)
    }

    /**
     * Returns the stub of the authorization OAuth2 URL, without the redirect_uri
     */
    fun getAuthorizeStub(): String = authStubDiscord
}