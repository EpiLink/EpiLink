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
import guru.zoroark.tegral.di.environment.InjectionScope
import guru.zoroark.tegral.di.environment.invoke
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.formUrlEncode
import io.ktor.http.parametersOf
import io.ktor.http.plus
import org.epilink.bot.EndpointException
import org.epilink.bot.InternalEndpointException
import org.epilink.bot.StandardErrorCodes.DiscordApiFailure
import org.epilink.bot.StandardErrorCodes.InvalidAuthCode
import org.epilink.bot.UserEndpointException
import org.epilink.bot.debug
import org.slf4j.LoggerFactory

/**
 * The back-end, specifically for interacting with the Discord API
 */
class DiscordBackEnd(
    scope: InjectionScope,
    private val clientId: String,
    private val secret: String
) {
    private val logger = LoggerFactory.getLogger("epilink.discordapi")

    private val client: HttpClient by scope()

    private val authStubDiscord = "https://discord.com/api/oauth2/authorize?" + listOf(
        "response_type=code",
        "client_id=$clientId",
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
     * @throws EndpointException If something wrong happens while contacting the Discord APIs.
     */
    suspend fun getDiscordToken(authcode: String, redirectUri: String): String {
        logger.debug { "Contacting Discord API for retrieving OAuth2 token..." }
        val res = runCatching {
            client.post("https://discord.com/api/v6/oauth2/token") {
                header(HttpHeaders.Accept, ContentType.Application.Json)
                val parameters = createOauthParameters(clientId, secret, authcode, redirectUri) +
                    parametersOf("scope", "identify")
                setBody(TextContent(parameters.formUrlEncode(), ContentType.Application.FormUrlEncoded))
            }.bodyAsText()
        }.getOrElse { ex ->
            if (ex is ClientRequestException) {
                handleClientRequestException(ex)
            } else {
                throw InternalEndpointException(
                    DiscordApiFailure,
                    "Failed to contact Discord API for obtaining token.",
                    ex
                )
            }
        }
        val data: Map<String, Any?> = ObjectMapper().readValue(res)
        return data["access_token"] as String?
            ?: throw InternalEndpointException(DiscordApiFailure, "Did not receive any access token from Discord")
    }

    private suspend fun handleClientRequestException(ex: ClientRequestException): Nothing {
        val received = ex.response.call.body<String>()
        logger.debug { "Failed: received $received" }
        val data = ObjectMapper().readValue<Map<String, Any?>>(received)
        val error = data["error"] as? String
        val isCodeError =
            error == "invalid_grant" ||
                (data["error_description"] as? String)?.contains("Invalid \"code\"") ?: false
        if (isCodeError) {
            throw UserEndpointException(InvalidAuthCode, "Invalid authorization code", "oa.iac", cause = ex)
        } else {
            discordApiError(
                "Discord OAuth failed: $error (" + (data["error_description"] ?: "no description") + ")",
                ex
            )
        }
    }

    /**
     * Retrieve a user's own information using a Discord OAuth2 token.
     *
     * @throws EndpointException If something wrong happens while contacting the Discord APIs
     */
    suspend fun getDiscordInfo(token: String): DiscordUserInfo {
        logger.debug { "Attempting to retrieve Discord information from token $token" }
        val data = runCatching {
            client.getJson("https://discord.com/api/v6/users/@me", bearer = token)
        }.getOrElse {
            logger.debug(it) { "Failed (token $token)" }
            discordApiError("Failed to contact Discord servers for user information retrieval.", it)
        }
        val userId = data["id"] as String?
            ?: discordApiError("Missing Discord ID in Discord API response")
        val username = data["username"] as String?
            ?: discordApiError("Missing Discord username in Discord API response")
        val discriminator = data["discriminator"] as String?
            ?: discordApiError("Missing Discord discriminator in Discord API response")
        val displayableUsername = "$username#$discriminator"
        val avatarHash = data["avatar"] as String?
        val avatar =
            if (avatarHash != null) "https://cdn.discordapp.com/avatars/$userId/$avatarHash.png?size=256" else null
        return DiscordUserInfo(userId, displayableUsername, avatar).also { logger.debug { "Retrieved info $it" } }
    }

    private fun discordApiError(message: String, cause: Throwable? = null): Nothing =
        throw InternalEndpointException(DiscordApiFailure, message, cause)

    /**
     * Returns the stub of the authorization OAuth2 URL, without the redirect_uri
     */
    fun getAuthorizeStub(): String = authStubDiscord
}
