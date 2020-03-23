package org.epilink.bot.http

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.ParametersBuilder
import io.ktor.http.formUrlEncode
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
        val res = client.post<String>("https://login.microsoftonline.com/${tenant}/oauth2/v2.0/token") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            body = TextContent(
                ParametersBuilder().apply {
                    append("scope", "User.Read")
                    appendOauthParameters(clientId, secret, code, redirectUri)
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
    suspend fun getMicrosoftInfo(token: String): MicrosoftUserInfo {
        val data = client.getJson("https://graph.microsoft.com/v1.0/me", bearer = token)
        val email = data["mail"] as String?
            ?: (data["userPrincipalName"] as String?)?.takeIf { it.contains("@") }
            ?: error("User does not have an email address")
        val id = data["id"] as String? ?: error("User does not have an ID")
        return MicrosoftUserInfo(id, email)
    }

    fun getAuthorizeStub(): String = authStubMsft
}