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
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.epilink.bot.LinkEndpointException
import org.epilink.bot.LinkEndpointInternalException
import org.epilink.bot.LinkEndpointUserException
import org.epilink.bot.StandardErrorCodes.*
import org.epilink.bot.debug
import org.epilink.bot.rulebook.getList
import org.epilink.bot.rulebook.getString
import org.jose4j.jwk.HttpsJwks
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.keys.resolvers.HttpsJwksVerificationKeyResolver
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.slf4j.LoggerFactory


/**
 * This class is responsible for communicating with OIDC APIs, determined from the given metadata
 */
class LinkIdentityProvider(
    private val metadata: IdentityProviderMetadata,
    private val clientId: String,
    private val clientSecret: String
) : KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.identityProvider")
    private val authStubMsft = metadata.authorizeUrl + "?" +
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
        // Not present for Google
        // setRequireNotBefore()
        setAllowedClockSkewInSeconds(30)
        setRequireSubject()
        setExpectedAudience(clientId)
        // Does not work for MS, since the issuer is different for every tenant
        // setExpectedIssuer(metadata.issuer)
        setVerificationKeyResolver(
            // TODO Dynamically determine the URL from the OIDC metadata document (.well-known/...)
            HttpsJwksVerificationKeyResolver(HttpsJwks(metadata.jwksUri))
        )
    }.build()

    /**
     * Consume the authcode and return the user info from the retrieved ID token.
     *
     * @param code The authorization code to consume
     * @param redirectUri The redirect_uri that was used in the authorization request that returned the authorization
     * code
     * @throws LinkEndpointException If some error is thrown
     */
    suspend fun getUserIdentityInfo(code: String, redirectUri: String): UserIdentityInfo {
        val res = runCatching {
            client.post<String>(metadata.tokenUrl.also { logger.error(it) }) {
                header(HttpHeaders.Accept, ContentType.Application.Json)
                body = TextContent(
                    ParametersBuilder().apply {
                        //append("scope", "openid profile email")
                        appendOauthParameters(clientId, clientSecret, code, redirectUri)
                    }.build().formUrlEncode().also { logger.error(it) },
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
                        IdentityProviderApiFailure,
                        "Identity Provider OAuth failed: $error (" + (data["error_description"]
                            ?: "no description") + ")",
                        ex
                    )
                }
            } else {
                throw LinkEndpointInternalException(IdentityProviderApiFailure, "Microsoft API call failed", ex)
            }
        }
        val data: Map<String, Any?> = ObjectMapper().readValue(res)
        val jwt = data["id_token"] as String?
            ?: throw LinkEndpointInternalException(
                IdentityProviderApiFailure,
                "Did not receive any ID token from the identity provider"
            )
        val claims = withContext(Dispatchers.Default) { jwtConsumer.processToClaims(jwt) }
        return UserIdentityInfo(
            claims.getClaimValueAsString(metadata.idClaim) ?: throw LinkEndpointUserException(
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

data class IdentityProviderMetadata(
    val issuer: String,
    val tokenUrl: String,
    val authorizeUrl: String,
    val jwksUri: String,
    // or oid for backwards compat for ms accounts
    val idClaim: String = "sub"
)

sealed class MetadataOrFailure {
    class Metadata(val metadata: IdentityProviderMetadata) : MetadataOrFailure()
    class IncompatibleProvider(val reason: String) : MetadataOrFailure()
}

fun identityProviderMetadataFromDiscovery(discoveryContent: String, idClaim: String = "sub"): MetadataOrFailure {
    val map: Map<String, *> = ObjectMapper().readValue(discoveryContent)
    val responseTypes = map.getList("response_types_supported")
    if ("code" !in responseTypes) {
        return MetadataOrFailure.IncompatibleProvider("Does not support authorization code flow")
    }
    val metadata = IdentityProviderMetadata(
        issuer = map.getString("issuer"),
        tokenUrl = map.getString("token_endpoint")
            .ensureHttps { return MetadataOrFailure.IncompatibleProvider("HTTPS is required but got $it") },
        authorizeUrl = map.getString("authorization_endpoint")
            .ensureHttps { return MetadataOrFailure.IncompatibleProvider("HTTPS is required but got $it") },
        jwksUri = map.getString("jwks_uri")
            .ensureHttps { return MetadataOrFailure.IncompatibleProvider("HTTPS is required but got $it") },
        idClaim = idClaim
    )
    return MetadataOrFailure.Metadata(metadata)
}

private inline fun String.ensureHttps(ifNotHttps: (String) -> Nothing): String {
    if (!this.startsWith("https://")) ifNotHttps(this)
    return this
}