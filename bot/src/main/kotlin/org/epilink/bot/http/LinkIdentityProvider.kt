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
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.content.*
import io.ktor.http.*
import org.epilink.bot.LinkEndpointException
import org.epilink.bot.LinkEndpointInternalException
import org.epilink.bot.LinkEndpointUserException
import org.epilink.bot.StandardErrorCodes.IdentityProviderApiFailure
import org.epilink.bot.StandardErrorCodes.InvalidAuthCode
import org.epilink.bot.debug
import org.epilink.bot.rulebook.getList
import org.epilink.bot.rulebook.getString
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

/**
 * This class is responsible for communicating with OIDC APIs, determined from the given metadata
 */
@OptIn(KoinApiExtension::class)
class LinkIdentityProvider(
    private val clientId: String,
    private val clientSecret: String,
    private val tokenUrl: String,
    authorizeUrl: String
) : KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.identityProvider")
    private val authStub = "$authorizeUrl?" +
            listOf(
                "client_id=${clientId}",
                "response_type=code",
                /*
                 * Only useful for Microsoft, which prompts the "select an account" screen, since users may have
                 * multiple accounts (e.g. their work account and their personal account)
                 *
                 * From the RFC 6749:
                 * > The authorization server MUST ignore unrecognized request parameters.
                 * So we can leave it in, servers that do not support that parameter will ignore it.
                 */
                "prompt=select_account",
                "scope=openid%20profile%20email"
            ).joinToString("&")

    private val client: HttpClient by inject()
    private val jwtVerifier: LinkJwtVerifier by inject()

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
            client.post<String>(tokenUrl) {
                header(HttpHeaders.Accept, ContentType.Application.Json)
                body = TextContent(
                    ParametersBuilder().apply {
                        appendOauthParameters(clientId, clientSecret, code, redirectUri)
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
                        IdentityProviderApiFailure,
                        "Identity Provider OAuth failed: $error (" + (data["error_description"]
                            ?: "no description") + ")",
                        ex
                    )
                }
            } else {
                throw LinkEndpointInternalException(IdentityProviderApiFailure, "Identity Provider API call failed", ex)
            }
        }
        val data: Map<String, Any?> = ObjectMapper().readValue(res)
        val jwt = data["id_token"] as String?
            ?: throw LinkEndpointInternalException(
                IdentityProviderApiFailure,
                "Did not receive any ID token from the identity provider"
            )
        return jwtVerifier.process(jwt)
    }

    /**
     * Retrieve the beginning of the authorization URL that is only missing the redirect_uri
     */
    fun getAuthorizeStub(): String = authStub
}

/**
 * Metadata for the identity provider OpenID setup
 */
data class IdentityProviderMetadata(
    /**
     * Issuer URL
     */
    val issuer: String,
    /**
     * URL for the token endpoint
     */
    val tokenUrl: String,
    /**
     * URL for the authorize endpoint
     */
    val authorizeUrl: String,
    /**
     * URI for the JWK set (JWKS)
     */
    val jwksUri: String,
    /**
     * The claim used for keying on identity
     */
    val idClaim: String = "sub" // or oid for backwards compat for ms accounts
)

/**
 * Metadata about an identity provider or a failure with a reason if the provider is not compatible
 */
sealed class MetadataOrFailure {
    /**
     * Metadata (implying that it was a success)
     *
     * @property metadata The metadata
     */
    class Metadata(val metadata: IdentityProviderMetadata) : MetadataOrFailure()

    /**
     * The provider is not compatible with EpiLink
     *
     * @property reason The reason for the incompatibility
     */
    class IncompatibleProvider(val reason: String) : MetadataOrFailure()
}

/**
 * Determine the identity provider metadata from the contents of the discovery URL
 */
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