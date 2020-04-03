package org.epilink.bot.http

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.features.ClientRequestException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.ParametersBuilder
import io.ktor.http.formUrlEncode
import org.epilink.bot.LinkDisplayableException
import org.epilink.bot.LinkException
import org.koin.core.KoinComponent
import org.koin.core.inject

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
     * @throws LinkException If something wrong happens while contacting the Discord APIs.
     */
    suspend fun getDiscordToken(authcode: String, redirectUri: String): String {
        val res = runCatching {
            client.post<String>("https://discordapp.com/api/v6/oauth2/token") {
                header(HttpHeaders.Accept, ContentType.Application.Json)
                body = TextContent(
                    ParametersBuilder().apply {
                        appendOauthParameters(clientId, secret, authcode, redirectUri)
                    }.build().formUrlEncode(),
                    ContentType.Application.FormUrlEncoded
                )
            }
        }.getOrElse { ex ->
            throw LinkDisplayableException(
                "Failed to contact Discord API for obtaining token.",
                ex is ClientRequestException && ex.response.status.value == 400, ex
            )
        }
        val data: Map<String, Any?> = ObjectMapper().readValue(res)
        (data["error"] as? String)?.let {
            if (it == "invalid_grant")
                throw LinkDisplayableException("Invalid authorization code", true)
            else
                throw LinkException("Discord OAuth failed: $it (" + (data["error_description"] ?: "no description") + ")")
        }
        return data["access_token"] as String? ?: throw LinkException("Did not receive any access token from Discord")
    }

    /**
     * Retrieve a user's own information using a Discord OAuth2 token.
     *
     * @throws LinkException If something wrong happens while contacting the Discord APIs
     */
    suspend fun getDiscordInfo(token: String): DiscordUserInfo {
        val data = runCatching {
            client.getJson("https://discordapp.com/api/v6/users/@me", bearer = token)
        }.getOrElse {
            throw LinkException("Failed to contact Discord servers for user information retrieval.")
        }
        val userid = data["id"] as String?
            ?: throw LinkException("Missing Discord ID in Discord API response")
        val username = data["username"] as String?
            ?: throw LinkException("Missing Discord username in Discord API response")
        val discriminator = data["discriminator"] as String?
            ?: throw LinkException("Missing Discord discriminator in Discord API response")
        val displayableUsername = "$username#$discriminator"
        val avatarHash = data["avatar"] as String?
        val avatar =
            if (avatarHash != null) "https://cdn.discordapp.com/avatars/$userid/$avatarHash.png?size=256" else null
        return DiscordUserInfo(userid, displayableUsername, avatar)
    }

    fun getAuthorizeStub(): String = authStubDiscord
}