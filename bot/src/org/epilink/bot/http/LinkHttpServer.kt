package org.epilink.bot.http

import io.ktor.application.Application
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

interface LinkHttpServer {
    /**
     * Start the server. If wait is true, this function will block until the
     * server stops.
     */
    fun startServer(wait: Boolean)
}

/**
 * This class represents the Ktor server.
 */
class LinkHttpServerImpl : LinkHttpServer, KoinComponent {
    private val wsCfg: LinkWebServerConfiguration by inject()

    /**
     * True if the server should also serve the front-end, false if it should
     * attempt to redirect to the front-end.
     */
    private val serveFrontEnd by lazy {
        // Detect the presence of the frontend
        LinkHttpServer::class.java.getResource("/.hasFrontend") != null &&
                // Check that no URL is set
                wsCfg.frontendUrl == null
    }

    private val backend: LinkBackEnd by inject()

    /**
     * The actual Ktor application instance
     */
    private var server: ApplicationEngine = embeddedServer(Netty, wsCfg.port) {
        with(backend) { epilinkApiModule() }
        frontEndHandler(serveFrontEnd, wsCfg.frontendUrl)
    }


    override fun startServer(wait: Boolean) {
        if (serveFrontEnd) {
            logger.info("Front-end will be served. To disable that, set a front-end URL in the config file.")
        }
        server.start(wait)
    }
}
