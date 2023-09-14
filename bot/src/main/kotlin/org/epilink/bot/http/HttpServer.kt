/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.http

import guru.zoroark.tegral.di.environment.InjectionScope
import guru.zoroark.tegral.di.environment.invoke
import guru.zoroark.tegral.services.api.TegralService
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.forwardedheaders.ForwardedHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import org.epilink.bot.config.ProxyType.Forwarded
import org.epilink.bot.config.ProxyType.None
import org.epilink.bot.config.ProxyType.XForwarded
import org.epilink.bot.config.WebServerConfiguration
import org.epilink.bot.debug
import org.slf4j.LoggerFactory

/**
 * The HTTP server. Wraps the actual Ktor server and initializes it with injected dependencies, such as the back end
 * and the front end handler.
 */
interface HttpServer

/**
 * This class represents the Ktor server.
 */
internal class HttpServerImpl(scope: InjectionScope) : HttpServer, TegralService {
    private val logger = LoggerFactory.getLogger("epilink.http")
    private val wsCfg: WebServerConfiguration by scope()

    private val frontEndHandler: FrontEndHandler by scope()

    private val backend: BackEnd by scope()

    /**
     * The actual Ktor application instance
     */
    private lateinit var server: ApplicationEngine

    override suspend fun start() {
        if (frontEndHandler.serveIntegratedFrontEnd) {
            logger.info("Bundled front-end will be served. To disable that, set a front-end URL in the config file.")
        }
        server = embeddedServer(Netty, wsCfg.port, wsCfg.address) {
            when (wsCfg.proxyType) {
                None -> logger.warn("Reverse proxy support is DISABLED. Use a reverse proxy and configure it for HTTPS!")
                Forwarded -> install(ForwardedHeaders)
                    .also { logger.debug { "'Forwarded' header support installed." } }

                XForwarded -> install(XForwardedHeaders)
                    .also { logger.debug { "'X-Forwarded-*' header support installed." } }
            }
            logger.debug("Installing EpiLink API")
            with(backend) {
                epilinkApiModule()
            }
            logger.debug("Installing EpiLink front-end handler")
            with(frontEndHandler) { install() }
        }
        server.start()
    }

    override suspend fun stop() {
        server.stop()
    }
}
