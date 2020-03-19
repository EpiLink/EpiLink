package org.epilink.bot.http

import com.auth0.jwt.algorithms.Algorithm
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.sessions.SessionStorageMemory
import io.ktor.sessions.Sessions
import io.ktor.sessions.header
import org.epilink.bot.LinkServerEnvironment
import org.epilink.bot.config.LinkTokens
import org.epilink.bot.config.LinkWebServerConfiguration
import org.epilink.bot.http.sessions.ConnectedSession
import org.epilink.bot.http.sessions.RegisterSession
import org.epilink.bot.logger

internal const val JWT_USER_AUDIENCE = "user"

/**
 * This class represents the Ktor server.
 */
class LinkHttpServer(
    /**
     * The environment this server lives in
     */
    private val env: LinkServerEnvironment,
    /**
     * Configuration specifically or the web server
     */
    private val wsCfg: LinkWebServerConfiguration,

    secrets: LinkTokens
) {
    /**
     * The actual Ktor application instance
     */
    private var server: ApplicationEngine = ktorServer(wsCfg.port)

    /**
     * The algorithm to use for JWT related duties
     */
    private val jwtAlgorithm = Algorithm.HMAC256(secrets.jwtSecret)

    /**
     * True if the server should also serve the front-end, false if it should
     * attempt to redirect to the front-end.
     */
    private val serveFrontEnd =
        // Detect the presence of the frontend
        LinkHttpServer::class.java.getResource("/.hasFrontend") != null &&
                // Check that no URL is set
                wsCfg.frontendUrl == null

    private val backend =
        LinkBackEnd(this, env, jwtAlgorithm, wsCfg.sessionDuration, secrets)
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
                    verifier(
                        makeJwtVerifier(
                            jwtAlgorithm, env.name, JWT_USER_AUDIENCE
                        )
                    )
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

            /*
             * Used for sessions
             */
            install(Sessions) {
                header<RegisterSession>(
                    "RegistrationSessionId",
                    // TODO SessionStorageMemory should only be used for dev
                    //      purposes
                    SessionStorageMemory()
                )
                header<ConnectedSession>(
                    "SessionId",
                    // TODO SessionStorageMemory should only be used for dev
                    //      purposes
                    SessionStorageMemory()
                )
            }

            routing {
                /*
                 * Main API endpoint
                 */
                route("/api/v1") {
                    with(backend) { epilinkApiV1() }
                }

                frontEndHandler(serveFrontEnd, wsCfg.frontendUrl)
            }
        }
}
