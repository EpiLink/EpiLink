package org.epilink.bot.http

import io.ktor.application.install
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
import org.epilink.bot.config.LinkWebServerConfiguration
import org.epilink.bot.http.sessions.ConnectedSession
import org.epilink.bot.http.sessions.RegisterSession
import org.epilink.bot.logger
import org.koin.core.KoinComponent
import org.koin.core.inject

/**
 * This class represents the Ktor server.
 */
class LinkHttpServer : KoinComponent {
    private val wsCfg: LinkWebServerConfiguration by inject()

    /**
     * The actual Ktor application instance
     */
    private var server: ApplicationEngine = ktorServer(wsCfg.port)

    /**
     * True if the server should also serve the front-end, false if it should
     * attempt to redirect to the front-end.
     */
    private val serveFrontEnd =
        // Detect the presence of the frontend
        LinkHttpServer::class.java.getResource("/.hasFrontend") != null &&
                // Check that no URL is set
                wsCfg.frontendUrl == null

    private val backend: LinkBackEnd by inject()

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
