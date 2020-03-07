package org.epilink.bot

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.application.ApplicationCall
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
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.resolveResource
import io.ktor.http.content.resource
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.http.defaultForFileExtension
import io.ktor.http.fromFileExtension
import io.ktor.jackson.jackson
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException

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
     * True if the server should also serve the front-end, false if it should
     * attempt to redirect to the front-end.
     */
    private val serveFrontEnd =
        // Detect the presence of the frontend
        LinkServerApi::class.java.getResource("/.hasFrontend") != null &&
                // Check that no URL is set
                wsCfg.frontendUrl == null

    /**
     * Start the server. If wait is true, this function will block until the
     * server stops.
     */
    fun startServer(wait: Boolean) {
        if (serveFrontEnd) {
            logger.info("Front-end will be served. To disable that, set a front-end URL in the config file.")
        }
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
                 * Main API endpoint
                 */
                route("/api/v1") {
                    epilinkApiV1()
                }

                /*
                 * Main endpoint. If the user directly tries to connect to the
                 * back-end, redirect them to the front-end (or 404 if the url
                 * is unknown).
                 */
                get("/{...}") {
                    if (serveFrontEnd) {
                        call.respondBootstrapped()
                    } else {
                        val url = wsCfg.frontendUrl
                        if (url == null) {
                            // 404
                            call.respond(HttpStatusCode.NotFound)
                        } else {
                            // Redirect to frontend
                            call.respondRedirect(
                                url + call.parameters.getAll("path")?.joinToString(
                                    "/"
                                ),
                                permanent = true
                            )
                        }
                    }
                }
            }
        }

    private suspend fun ApplicationCall.respondBootstrapped() {
        // The path (without the initial /)
        val path = request.path().substring(1)
        if (path.isEmpty())
            // Request on /, respond with the index
            respondDefaultFrontEnd()
        else {
            // Request somewhere else: is it something in the frontend?
            val f = resolveResource(path, "frontend")
            if (f != null) {
                // Respond with the frontend element
                respond(f)
            } else {
                // Respond with the index
                respondDefaultFrontEnd()
            }
        }

    }

    /**
     * Default response for bootstrap requests: simply serve the index. Only
     * called when the frontend is here for sure.
     */
    private suspend fun ApplicationCall.respondDefaultFrontEnd() {
        val def = resolveResource("index.prod.html", "frontend") {
            ContentType.defaultForFileExtension("html")
        }
            // Should not happen
            ?: throw IllegalStateException("Could not find front-end index in JAR file")
        respond(def)
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

