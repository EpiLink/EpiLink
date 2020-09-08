/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.epilink.bot.LinkEndpointUserException
import org.epilink.bot.StandardErrorCodes
import org.jose4j.jwk.HttpsJwks
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.keys.resolvers.HttpsJwksVerificationKeyResolver
import org.koin.core.KoinComponent

/**
 * This class is responsible for verifying JWT tokens using a JWKS for keys.
 */
class LinkJwtVerifier(clientId: String, jwksUri: String, private val idClaim: String) : KoinComponent {
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
            HttpsJwksVerificationKeyResolver(HttpsJwks(jwksUri))
        )
    }.build()

    // maybe return something else in case of a failure? (the current JWT exception is fine, but that does make us rely
    // on this particular jwt library)
    /**
     * Process a specific JWT and return the info retrieved. Throws an exception if the JWT is invalid.
     */
    suspend fun process(jwt: String): UserIdentityInfo {
        val claims = withContext(Dispatchers.Default) { jwtConsumer.processToClaims(jwt) }
        return UserIdentityInfo(
            claims.getClaimValueAsString(idClaim) ?: throw LinkEndpointUserException(
                StandardErrorCodes.AccountHasNoId,
                "This user does not have an ID",
                "ms.nid"
            ),
            claims.getClaimValueAsString("email") ?: throw LinkEndpointUserException(
                StandardErrorCodes.AccountHasNoEmailAddress,
                "This account does not have an email address",
                "ms.nea"
            )
        )
    }
}