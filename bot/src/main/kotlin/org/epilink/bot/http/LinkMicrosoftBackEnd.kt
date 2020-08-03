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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.epilink.bot.LinkEndpointException
import org.epilink.bot.LinkEndpointInternalException
import org.epilink.bot.LinkEndpointUserException
import org.epilink.bot.StandardErrorCodes.*
import org.epilink.bot.debug
import org.jose4j.jwk.HttpsJwks
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.keys.resolvers.HttpsJwksVerificationKeyResolver
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.slf4j.LoggerFactory


/**
 * This class is responsible for communicating with Microsoft APIs
 */
class LinkMicrosoftBackEnd(
    private val clientId: String,
    private val secret: String,
    private val tenant: String
) : KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.microsoftapi")
    private val authStubMsft = "https://login.microsoftonline.com/${tenant}/oauth2/v2.0/authorize?" +
            listOf(
                "client_id=${clientId}",
                "response_type=code",
                "prompt=select_account",
                "scope=openid%20profile%20email"
            ).joinToString("&")

    private val client: HttpClient by inject()

    private val jwtConsumer = JwtConsumerBuilder().apply {
        setRequireExpirationTime()
        setRequireIssuedAt()
        setRequireNotBefore()
        setAllowedClockSkewInSeconds(30)
        setRequireSubject()
        setExpectedAudience(clientId)
        setVerificationKeyResolver(
            // TODO Dynamically determine the URL from the OIDC metadata document (.well-known/...)
            HttpsJwksVerificationKeyResolver(HttpsJwks("https://login.microsoftonline.com/$tenant/discovery/v2.0/keys"))
        )
    }.build()

    /**
     * Consume the authcode and return the microsoft info from the retrieved ID token.
     *
     * @param code The authorization code to consume
     * @param redirectUri The redirect_uri that was used in the authorization request that returned the authorization
     * code
     * @throws LinkEndpointException If some error is thrown
     */
    suspend fun getMicrosoftInfo(code: String, redirectUri: String): MicrosoftUserInfo {
        val res = runCatching {
            client.post<String>("https://login.microsoftonline.com/${tenant}/oauth2/v2.0/token") {
                header(HttpHeaders.Accept, ContentType.Application.Json)
                body = TextContent(
                    ParametersBuilder().apply {
                        append("scope", "openid profile email")
                        appendOauthParameters(clientId, secret, code, redirectUri)
                    }.build().formUrlEncode(),
                    ContentType.Application.FormUrlEncoded
                )
            }
        }.getOrElse { ex ->
            if (ex is ClientRequestException) {
                val received = ex.response.call.receive<String>()
                logger.debug { "Failed: received $received" }
                val data = ObjectMapper().readValue<Map<String, Any?>>(received)
                when (val error = data["error"] as? String) {
                    "invalid_grant" -> throw LinkEndpointUserException(
                        InvalidAuthCode,
                        "Invalid authorization code",
                        "oa.iac",
                        cause = ex
                    )
                    else -> throw LinkEndpointInternalException(
                        MicrosoftApiFailure,
                        "Microsoft OAuth failed: $error (" + (data["error_description"] ?: "no description") + ")",
                        ex
                    )
                }
            } else {
                throw LinkEndpointInternalException(MicrosoftApiFailure, "Microsoft API call failed", ex)
            }
        }
        val data: Map<String, Any?> = ObjectMapper().readValue(res)
        val jwt = data["id_token"] as String?
            ?: throw LinkEndpointInternalException(
                MicrosoftApiFailure,
                "Did not receive any ID token from Microsoft"
            )
        val claims = withContext(Dispatchers.Default) { jwtConsumer.processToClaims(jwt) }
        return MicrosoftUserInfo(
            claims.getClaimValueAsString("oid") ?: throw LinkEndpointUserException(
                AccountHasNoId,
                "This user does not have an ID",
                "ms.nid"
            ),
            claims.getClaimValueAsString("email") ?: throw LinkEndpointUserException(
                AccountHasNoEmailAddress,
                "This account does not have an email address",
                "ms.nea"
            )
        )
    }

    /**
     * Retrieve the beginning of the authorization URL that is only missing the redirect_uri
     */
    fun getAuthorizeStub(): String = authStubMsft
}