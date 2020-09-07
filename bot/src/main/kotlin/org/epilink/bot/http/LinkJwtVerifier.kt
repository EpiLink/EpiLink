package org.epilink.bot.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.epilink.bot.LinkEndpointUserException
import org.epilink.bot.StandardErrorCodes
import org.jose4j.jwk.HttpsJwks
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.keys.resolvers.HttpsJwksVerificationKeyResolver
import org.koin.core.KoinComponent

class LinkJwtVerifier(clientId: String, jwksUri: String, val idClaim: String) : KoinComponent {
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