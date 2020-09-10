/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.http

import guru.zoroark.ratelimit.RateLimit
import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.routing
import io.ktor.sessions.Sessions
import io.ktor.sessions.header
import kotlinx.coroutines.coroutineScope
import org.epilink.bot.*
import org.epilink.bot.StandardErrorCodes.UnknownError
import org.epilink.bot.config.LinkWebServerConfiguration
import org.epilink.bot.http.endpoints.LinkAdminApi
import org.epilink.bot.http.endpoints.LinkMetaApi
import org.epilink.bot.http.endpoints.LinkRegistrationApi
import org.epilink.bot.http.endpoints.LinkUserApi
import org.epilink.bot.http.sessions.ConnectedSession
import org.epilink.bot.http.sessions.RegisterSession
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.slf4j.LoggerFactory

/**
 * Interface for the back-end. This component is responsible for the installation of features, error-handling and
 * routes.
 */
interface LinkBackEnd {

    /**
     * Ktor module for the back-end features
     */
    fun Application.installFeatures()

    /**
     * Install the error handling interceptor. Automatically called by [epilinkApiModule].
     *
     * This automatically catches [LinkEndpointException] and any other exceptions. For the former, a 400 or 500 error
     * is generated (depending on whether the exception is a [user exception][LinkEndpointUserException] or an
     * [internal exception][LinkEndpointInternalException]). For the latter, a 500 error is generated.
     *
     * This logs any error and makes sure than any internal error gets returned as a correct API response.
     */
    fun Route.installErrorHandling()

    /**
     * Ktor module for the back-end API routes and features. This just calls all of the relevant
     * installation code (including feature installation and error handling routing).
     */
    fun Application.epilinkApiModule()
}

/**
 * The back-end, defining API endpoints and more
 */
internal class LinkBackEndImpl : LinkBackEnd, KoinComponent {

    private val logger = LoggerFactory.getLogger("epilink.api")
    private val cacheClient: CacheClient by inject()
    private val registrationApi: LinkRegistrationApi by inject()
    private val metaApi: LinkMetaApi by inject()
    private val userApi: LinkUserApi by inject()
    private val adminApi: LinkAdminApi by inject()
    private val wsCfg: LinkWebServerConfiguration by inject()

    override fun Application.installFeatures() {
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
                cacheClient.newSessionStorage("el_reg_")
            )
            header<ConnectedSession>(
                "SessionId",
                cacheClient.newSessionStorage("el_ses_")
            )
        }

        install(RateLimit)
    }

    override fun Route.installErrorHandling() {
        // Make sure that exceptions are not left for Ktor to try and figure out.
        // Ktor would figure it out, but we a) need to log them b) respond with an ApiResponse
        intercept(ApplicationCallPipeline.Monitoring) {
            try {
                coroutineScope {
                    proceed()
                }
            } catch (ex: LinkEndpointException) {
                when (ex) {
                    is LinkEndpointUserException -> {
                        logger.info("Encountered an endpoint exception ${ex.errorCode.description}", ex)
                        call.respond(HttpStatusCode.BadRequest, ex.toApiResponse())
                    }
                    is LinkEndpointInternalException -> {
                        logger.error("Encountered a back-end caused endpoint exception (${ex.errorCode}", ex)
                        call.respond(HttpStatusCode.InternalServerError, ex.toApiResponse())
                    }
                }
            } catch (ex: Exception) {
                logger.error(
                    "Uncaught exception encountered while processing v1 API call. Catch it and return a proper thing!",
                    ex
                )
                call.respond(HttpStatusCode.InternalServerError, UnknownError.toResponse())
            }
        }
    }

    override fun Application.epilinkApiModule() {
        installFeatures()
        routing {
            installErrorHandling()
            userApi.install(this)
            registrationApi.install(this)
            metaApi.install(this)
            if (wsCfg.enableAdminEndpoints)
                adminApi.install(this)
        }
    }
}
