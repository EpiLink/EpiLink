/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.http

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import org.epilink.bot.config.LinkWebServerConfiguration
import org.epilink.bot.debug
import org.epilink.bot.trace
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.slf4j.LoggerFactory

/**
 * Base interface for front-end handling. This class is responsible for handling root calls and non-API calls.
 */
interface LinkFrontEndHandler {
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
internal class LinkFrontEndHandlerImpl : LinkFrontEndHandler, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.fronthandler")

    private val wsCfg: LinkWebServerConfiguration by inject()

    override val serveIntegratedFrontEnd: Boolean by lazy {
        // Detect the presence of the frontend
        LinkHttpServer::class.java.getResource("/.hasFrontend") != null &&
                // Check that no URL is set
                wsCfg.frontendUrl == null
    }

    override fun Application.install() {
        val frontUrl = wsCfg.frontendUrl
        val disableCors = wsCfg.disableCorsSecurity
        routing {
            get("/{...}") {
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
                        val redirectionUrl = frontUrl.dropLast(1) +
                                call.request.path() +
                                (call.request.queryString().takeIf { it.isNotEmpty() }?.let { "?$it" } ?: "")
                        logger.debug { "Redirecting ${call.request.uri} to $redirectionUrl" }
                        call.respondRedirect(redirectionUrl, permanent = false)
                    }
                }
            }
        }
        when {
            disableCors -> {
                logger.warn("CORS is set to allow requests from any origin. DO NOT DO THIS IN PRODUCTION ENVIRONMENTS! Remove disableCorsSecurity from your config file!")
                install(CORS) {
                    anyHost()
                }
            }
            serveIntegratedFrontEnd -> {
                logger.debug("CORS is disabled because the integrated front end is being served.")
            }
            frontUrl == null -> {
                logger.warn("CORS is disabled. Web browsers may deny calls to the back-end. Specify the front-end URL in the configuration files to fix this.")
            }
            else -> {
                logger.debug("CORS is enabled.")
                /*
                 * Allows the frontend to call the API along with the required headers
                 * and methods
                 */
                install(CORS) {
                    method(HttpMethod.Options)
                    method(HttpMethod.Delete)

                    header("Content-Type")
                    header("RegistrationSessionId")
                    header("SessionId")
                    exposeHeader("RegistrationSessionId")
                    exposeHeader("SessionId")

                    host(frontUrl.dropLast(1).replace(Regex("https?://"), ""), schemes = listOf("http", "https"))
                }
                routing {

                }
            }
        }
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
        // Should not happen, unless the JAR was badly constructed
            ?: throw IllegalStateException("Could not find front-end index in JAR file")
        respond(def)
    }

    private suspend fun ApplicationCall.respondBootstrapped() {
        logger.trace { "Responding to ${request.uri} with bootstrapped" }
        // The path (without the initial /)
        val path = request.path().substring(1)
        if (path.isEmpty())
        // Request on /, respond with the index
            respondDefaultFrontEnd()
        else {
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