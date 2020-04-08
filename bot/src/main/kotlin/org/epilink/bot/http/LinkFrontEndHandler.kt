package org.epilink.bot.http

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.resolveResource
import io.ktor.http.defaultForFileExtension
import io.ktor.request.path
import io.ktor.request.queryString
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import org.epilink.bot.config.LinkWebServerConfiguration
import org.epilink.bot.logger
import org.koin.core.KoinComponent
import org.koin.core.inject

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

    private val wsCfg: LinkWebServerConfiguration by inject()

    override val serveIntegratedFrontEnd: Boolean by lazy {
        // Detect the presence of the frontend
        LinkHttpServer::class.java.getResource("/.hasFrontend") != null &&
                // Check that no URL is set
                wsCfg.frontendUrl == null
    }

    override fun Application.install() {
        val frontUrl = wsCfg.frontendUrl
        when {
            serveIntegratedFrontEnd -> {
                logger.debug("CORS is disabled because the integrated front end is being served.")
                routing {
                    get("/{...}") {
                        call.respondBootstrapped()
                    }
                }
            }
            frontUrl == null -> {
                logger.warn("CORS is disabled. Web browsers may deny calls to the back-end. Specify the front-end URL in the configuration files to fix this.")
                routing {
                    get("/{...}") {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
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

                    host(frontUrl.dropLast(1).replace(Regex("https?://"), ""))
                }
                routing {
                    get("/{...}") {
                        call.respondRedirect(
                            frontUrl.dropLast(1) +
                                    call.request.path() +
                                    (call.request.queryString().takeIf { it.isNotEmpty() }?.let { "?$it" } ?: ""),
                            permanent = true
                        )
                    }
                }
            }
        }
    }

    /**
     * Default response for bootstrap requests: simply serve the index. Only
     * called when the frontend is here for sure.
     */
    private suspend fun ApplicationCall.respondDefaultFrontEnd() {
        val def = resolveResource("index.html", "frontend") {
            ContentType.defaultForFileExtension("html")
        }
        // Should not happen
            ?: throw IllegalStateException("Could not find front-end index in JAR file")
        respond(def)
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
}

