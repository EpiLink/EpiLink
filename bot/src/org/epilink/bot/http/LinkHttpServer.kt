package org.epilink.bot.http

import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.epilink.bot.config.LinkWebServerConfiguration
import org.epilink.bot.logger
import org.koin.core.KoinComponent
import org.koin.core.inject

/**
 * The HTTP server. Wraps the actual Ktor server and initializes it with injected dependencies, such as the back end
 * and the front end handler.
 */
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

    private val frontEndHandler: LinkFrontEndHandler by inject()

    private val backend: LinkBackEnd by inject()

    /**
     * The actual Ktor application instance
     */
    private var server: ApplicationEngine = embeddedServer(Netty, wsCfg.port) {
        with(backend) { epilinkApiModule() }
        with(frontEndHandler) { install() }
    }

    override fun startServer(wait: Boolean) {
        if (frontEndHandler.serveIntegratedFrontEnd) {
            logger.info("Bundled front-end will be served. To disable that, set a front-end URL in the config file.")
        }
        server.start(wait)
    }
}
