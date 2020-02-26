package org.epilink.bot

import java.util.*

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

private val JWT_USER_AUDIENCE = "user";

/**
 * This class is responsible for holding configuration information and
 * references to things like the Discord client, the Database environment, etc.
 */
class LinkServerEnvironment(
    private val cfg: LinkConfiguration
) {
    private val jwtAlgorithm = Algorithm.HMAC256(cfg.tokens.jwtSecret)
    private var server: ApplicationEngine? = null

    val name: String
        get() = cfg.name

    fun start() {
        val serv = embeddedServer(Netty, cfg.serverPort) {
            install(Authentication) {
                jwt {
                    realm = cfg.name
                    verifier(makeJwtVerifier(cfg.name, JWT_USER_AUDIENCE))
                    validate { JWTPrincipal(it.payload) }
                }
            }

            routing {
                get("/") {
                    call.respondText("Hello World!")
                }

                authenticate() {
                    get("/admin") {
                        val principal = call.authentication.principal<JWTPrincipal>()

                        if (principal == null) {
                            call.respond(401)
                        }

                        call.respondText("Hello ${principal?.payload?.subject}")
                    }
                }

                get("/login") {
                    val jwt = JWT.create()
                            .withSubject("test n°" + Random().nextInt(1000))
                            .withIssuedAt(Date())
                            .withExpiresAt(Date(System.currentTimeMillis() + cfg.sessionDuration))
                            .withAudience(JWT_USER_AUDIENCE)
                            .withIssuer(cfg.name)
                            .sign(jwtAlgorithm);

                    call.respondText(jwt)
                }
            }
        }

        server = serv
        serv.start(wait = true)
    }

    private fun makeJwtVerifier(issuer: String, audience: String): JWTVerifier = JWT
            .require(jwtAlgorithm)
            .withAudience(audience)
            .withIssuer(issuer)
            .build()
}