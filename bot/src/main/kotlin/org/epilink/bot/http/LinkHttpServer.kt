package org.epilink.bot.http

import io.ktor.application.install
import io.ktor.features.ForwardedHeaderSupport
import io.ktor.features.HttpsRedirect
import io.ktor.features.XForwardedHeaderSupport
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.epilink.bot.config.LinkWebServerConfiguration
import org.epilink.bot.config.ProxyType.*
import org.epilink.bot.debug
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.slf4j.LoggerFactory

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
internal class LinkHttpServerImpl : LinkHttpServer, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.http")
    private val wsCfg: LinkWebServerConfiguration by inject()

    private val frontEndHandler: LinkFrontEndHandler by inject()

    private val backend: LinkBackEnd by inject()

    /**
     * The actual Ktor application instance
     */
    private var server: ApplicationEngine = embeddedServer(Netty, wsCfg.port) {
        when (wsCfg.proxyType) {
            None -> logger.info("Reverse proxy support is DISABLED.")
            Forwarded -> install(ForwardedHeaderSupport)
                .also { logger.debug { "'Forwarded' header support installed." } }
            XForwarded -> install(XForwardedHeaderSupport)
                .also { logger.debug { "'X-Forwarded-*' header support installed." } }
        }
        if (wsCfg.enableHttpsRedirect) {
            install(HttpsRedirect)
            logger.debug { "HTTPS redirection installed." }
        } else {
            logger.warn("HTTPS redirection is DISABLED.")
        }
        logger.debug("Installing EpiLink API")
        with(backend) { epilinkApiModule() }
        logger.debug("Installing EpiLink front-end handler")
        with(frontEndHandler) { install() }
    }

    override fun startServer(wait: Boolean) {
        if (frontEndHandler.serveIntegratedFrontEnd) {
            logger.info("Bundled front-end will be served. To disable that, set a front-end URL in the config file.")
        }
        server.start(wait)
    }
}
