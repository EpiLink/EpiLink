package org.epilink.bot

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.auth.Authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.features.ContentNegotiation
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.util.*
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.jwt.jwt
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing

private const val JWT_USER_AUDIENCE = "user"

/**
 * This class represents the Ktor server.
 */
class LinkServerApi(
    /**
     * The environment this server lives in
     */
    private val env: LinkServerEnvironment,
    /**
     * Configuration specifically or the web server
     */
    private val wsCfg: LinkWebServerConfiguration,
    /**
     * The secret to use. Separate from wsCfg because this is a secret token.
     */
    jwtSecret: String?
) {
    /**
     * The actual Ktor application instance
     */
    private var server: ApplicationEngine = ktorServer(wsCfg.port)

    /**
     * The algorithm to use for JWT related duties
     */
    private val jwtAlgorithm = Algorithm.HMAC256(jwtSecret)

    /**
     * Start the server. If wait is true, this function will block until the
     * server stops.
     */
    fun startServer(wait: Boolean) {
        server.start(wait)
    }

    /**
     * Create the server, and configure it
     */
    private fun ktorServer(port: Int) =
        embeddedServer(Netty, port) {
            install(Authentication) {
                jwt {
                    realm = env.name
                    verifier(makeJwtVerifier(env.name, JWT_USER_AUDIENCE))
                    validate { JWTPrincipal(it.payload) }
                }
            }
            /*
             * Used for automatically converting stuff to JSON when calling
             * "respond" with generic objects.
             */
            install(ContentNegotiation) {
                jackson {}
            }

            routing {
                /*
                 * Main endpoint. If the user directly tries to connect to the
                 * back-end, redirect them to the front-end (or 404 if the url
                 * is unknown).
                 *
                 * Once the opportunity will be given to bootstrap the front-end
                 * in the back-end, this could also just serve the front-end.
                 */
                get("/") {
                    val url = wsCfg.frontendUrl
                    if (url == null) {
                        // 404
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        // Redirect to frontend
                        call.respondRedirect(url, permanent = true)
                    }
                }
                /*
                 * Main API endpoint
                 */
                route("/api/v1") {
                    epilinkApiV1()
                }
            }
        }


    private fun makeJwtVerifier(issuer: String, audience: String): JWTVerifier =
        JWT.require(jwtAlgorithm)
            .withAudience(audience)
            .withIssuer(issuer)
            .build()

    private fun makeJwt(subject: String) =
        JWT.create().apply {
            withSubject(subject)
            withIssuedAt(Date())
            withExpiresAt(Date(System.currentTimeMillis() + wsCfg.sessionDuration))
            withAudience(JWT_USER_AUDIENCE)
            withIssuer(env.name)
            sign(jwtAlgorithm)
        }

    /**
     * Defines the API endpoints. Served under /api/v1
     *
     * Anything responded in here SHOULD use [ApiResponse] in JSON form.
     */
    private fun Route.epilinkApiV1() {
        /*
         * Just a hello world for now, answering in JSON
         */
        get("hello") {
            call.respond(ApiResponse(true, "Hello World", null))
        }
    }
}