package org.epilink.bot.http

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

fun makeJwtVerifier(
    algorithm: Algorithm,
    issuer: String,
    audience: String
): JWTVerifier =
    JWT.require(algorithm)
        .withAudience(audience)
        .withIssuer(issuer)
        .build()

fun makeJwt(
    sessionDuration: Long,
    algorithm: Algorithm,
    issuer: String,
    audience: String,
    subject: String
): String =
    JWT.create().run {
        withSubject(subject)
        withIssuedAt(Date())
        withExpiresAt(Date(System.currentTimeMillis() + sessionDuration))
        withAudience(audience)
        withIssuer(issuer)
        sign(algorithm)
    }
