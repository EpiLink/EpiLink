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
import org.epilink.bot.StandardErrorCodes.*
import org.koin.core.KoinComponent
import org.koin.core.inject

class LinkMicrosoftBackEnd(
    private val clientId: String,
    private val secret: String,
    private val tenant: String
) : KoinComponent {
    private val authStubMsft = "https://login.microsoftonline.com/${tenant}/oauth2/v2.0/authorize?" +
            listOf(
                "client_id=${clientId}",
                "response_type=code",
                "prompt=select_account",
                "scope=User.Read"
            ).joinToString("&")

    private val client: HttpClient by inject()

    /**
     * Consume the authcode and return a token.
     *
     * @param code The authorization code to consume
     * @param redirectUri The redirect_uri that was used in the authorization request that returned the authorization
     * code
     */
    suspend fun getMicrosoftToken(code: String, redirectUri: String): String {
        val res = runCatching {
            client.post<String>("https://login.microsoftonline.com/${tenant}/oauth2/v2.0/token") {
                header(HttpHeaders.Accept, ContentType.Application.Json)
                body = TextContent(
                    ParametersBuilder().apply {
                        append("scope", "User.Read")
                        appendOauthParameters(clientId, secret, code, redirectUri)
                    }.build().formUrlEncode(),
                    ContentType.Application.FormUrlEncoded
                )
            }
        }.getOrElse { ex ->
            if (ex is ClientRequestException) {
                val data = ObjectMapper().readValue<Map<String, Any?>>(ex.response.call.receive<String>())
                when (val errt = data["error"] as? String) {
                    "invalid_grant" -> throw LinkEndpointException(
                        InvalidAuthCode,
                        "Invalid authorization code",
                        true,
                        ex
                    )
                    else -> throw LinkEndpointException(
                        MicrosoftApiFailure,
                        "Microsoft OAuth failed: $errt (" + (data["error_description"] ?: "no description") + ")",
                        false,
                        ex
                    )
                }
            } else {
                throw LinkEndpointException(MicrosoftApiFailure, "Microsoft API call failed", false, ex)
            }
        }
        val data: Map<String, Any?> = ObjectMapper().readValue(res)
        return data["access_token"] as String?
            ?: throw LinkEndpointException(MicrosoftApiFailure, "Did not receive any access token from Microsoft")

    }

    /**
     * Retrieve a user's own information with Microsoft Graph using a Microsoft OAuth2 token.
     */
    suspend fun getMicrosoftInfo(token: String): MicrosoftUserInfo {
        try {
            val data = client.getJson("https://graph.microsoft.com/v1.0/me", bearer = token)
            val email = data["mail"] as String?
                ?: (data["userPrincipalName"] as String?)?.takeIf { it.contains("@") }
                ?: throw LinkEndpointException(
                    AccountHasNoEmailAddress,
                    "This account does not have an email address",
                    true
                )
            val id = data["id"] as String? ?: throw LinkEndpointException(
                AccountHasNoId,
                "This user does not have an ID",
                true
            )
            return MicrosoftUserInfo(id, email)
        } catch (ex: ClientRequestException) {
            throw LinkEndpointException(
                MicrosoftApiFailure,
                "Failed to contact the Microsoft API.",
                ex.response.status.value == 400,
                ex
            )
        }
    }

    fun getAuthorizeStub(): String = authStubMsft
}