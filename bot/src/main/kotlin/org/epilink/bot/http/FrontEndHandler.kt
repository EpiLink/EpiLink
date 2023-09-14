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
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.defaultForFileExtension
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.http.content.resolveResource
import io.ktor.server.plugins.cors.CORSConfig
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.path
import io.ktor.server.request.queryString
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.epilink.bot.config.WebServerConfiguration
import org.epilink.bot.debug
import org.epilink.bot.trace
import org.slf4j.LoggerFactory

/**
 * Base interface for front-end handling. This class is responsible for handling root calls and non-API calls.
 */
interface FrontEndHandler {
    /**
     * True if the server should also serve the front-end, false if it should
     * attempt to redirect to the front-end. The exact factors that are taken into account should at least be
     * the presence of an integrated front end and whether a frontend URL is provided by the user or not.
     */
    val serveIntegratedFrontEnd: Boolean

    /**
     * Installs the front end handler in the current application.
     */
    fun Application.install()
}

/**
 * Front-end handling implementation
 */
internal class FrontEndHandlerImpl(scope: InjectionScope) : FrontEndHandler {
    private val logger = LoggerFactory.getLogger("epilink.fronthandler")

    private val wsCfg: WebServerConfiguration by scope()

    override val serveIntegratedFrontEnd: Boolean by lazy {
        // Detect the presence of the frontend
        HttpServer::class.java.getResource("/.hasFrontend") != null &&
            // Check that no URL is set
            wsCfg.frontendUrl == null
    }

    private fun CORSConfig.applyCorsOptions() {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Delete)

        allowHeader("Content-Type")
        allowHeader("RegistrationSessionId")
        allowHeader("SessionId")
        exposeHeader("RegistrationSessionId")
        exposeHeader("SessionId")
    }

    override fun Application.install() {
        val frontUrl = wsCfg.frontendUrl
        routing {
            get("/{...}") { _ ->
                when {
                    serveIntegratedFrontEnd -> {
                        logger.debug("Serving bootstrapped front-end")
                        call.respondBootstrapped()
                    }

                    frontUrl == null -> {
                        logger.warn("Not serving bootstrapped front-end (none found and frontUrl was null)")
                        call.respond(HttpStatusCode.NotFound)
                    }

                    else -> {
                        logger.debug("Serving front-end as a redirection")
                        val redirectionUrl = frontUrl.dropLast(1) + call.request.path() +
                            (call.request.queryString().takeIf { it.isNotEmpty() }?.let { "?$it" }.orEmpty())
                        logger.debug { "Redirecting ${call.request.uri} to $redirectionUrl" }
                        call.respondRedirect(redirectionUrl, permanent = false)
                    }
                }
            }
        }
        setupCors()
    }

    private fun Application.setupCors() {
        val whitelist = wsCfg.corsWhitelist
        val frontUrl = wsCfg.frontendUrl
        when {
            whitelist.isNotEmpty() -> {
                logger.warn("CORS is enabled because a non-empty corsWhitelist is present.")
                install(CORS) {
                    applyCorsOptions()

                    if (whitelist.contains("*")) {
                        logger.warn(
                            "Whitelist contains '*' entry, CORS will allow all hosts. DO NOT DO THIS IN PRODUCTION!"
                        )
                        anyHost()
                    } else {
                        whitelist.forEach { x ->
                            addHostWithProtocol(x)
                        }

                        if (frontUrl != null) {
                            logger.info("Also allowing frontUrl in addition to the whitelist")
                            allowHost(
                                frontUrl.dropLast(1).replace(Regex("https?://"), ""),
                                schemes = listOf("http", "https")
                            )
                        }
                    }
                }
            }

            serveIntegratedFrontEnd -> {
                logger.debug("CORS is disabled because the integrated front end is being served.")
            }

            frontUrl == null -> {
                logger.warn(
                    "CORS is disabled. Web browsers may deny calls to the back-end. Specify the front-end " +
                        "URL in the configuration files to fix this."
                )
            }

            else -> {
                logger.debug("CORS is enabled.")
                /*
                 * Allows the frontend to call the API along with the required headers
                 * and methods
                 */
                install(CORS) {
                    applyCorsOptions()

                    allowHost(frontUrl.dropLast(1).replace(Regex("https?://"), ""), schemes = listOf("http", "https"))
                }
            }
        }
    }

    private fun CORSConfig.addHostWithProtocol(x: String) {
        val (protocol, path) = x.split("://", limit = 2)
        allowHost(path, listOf(protocol))
    }

    /**
     * Default response for bootstrap requests: simply serve the index. Only
     * called when the frontend is here for sure.
     */
    private suspend fun ApplicationCall.respondDefaultFrontEnd() {
        logger.debug { "Responding to ${request.uri} with default index.html" }
        val def = resolveResource("index.html", "frontend") {
            ContentType.defaultForFileExtension("html")
        }
        checkNotNull(def) { "Could not find front-end index in JAR file" }
        // The throw should not happen, unless the JAR was badly constructed
        respond(def)
    }

    private suspend fun ApplicationCall.respondBootstrapped() {
        logger.trace { "Responding to ${request.uri} with bootstrapped" }
        // The path (without the initial /)
        val path = request.path().substring(1)
        if (path.isEmpty()) {
            // Request on /, respond with the index
            respondDefaultFrontEnd()
        } else {
            logger.trace { "Attempting to resolve resource $path" }
            // Request somewhere else: is it something in the frontend?
            val f = resolveResource(path, "frontend")
            if (f != null) {
                logger.debug { "Responding to ${request.uri} with resolved bootstrapped resource" }
                // Respond with the frontend element
                respond(f)
            } else {
                logger.trace { "Did not resolve bootstrapped $path" }
                // Respond with the index
                respondDefaultFrontEnd()
            }
        }
    }
}
