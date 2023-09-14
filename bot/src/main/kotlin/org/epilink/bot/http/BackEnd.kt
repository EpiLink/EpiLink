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
import guru.zoroark.tegral.di.environment.InjectionScope
import guru.zoroark.tegral.di.environment.invoke
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.locations.Locations
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.header
import kotlinx.coroutines.coroutineScope
import org.epilink.bot.CacheClient
import org.epilink.bot.EndpointException
import org.epilink.bot.InternalEndpointException
import org.epilink.bot.StandardErrorCodes.UnknownError
import org.epilink.bot.UserEndpointException
import org.epilink.bot.config.WebServerConfiguration
import org.epilink.bot.http.endpoints.AdminEndpoints
import org.epilink.bot.http.endpoints.MetaApi
import org.epilink.bot.http.endpoints.RegistrationApi
import org.epilink.bot.http.endpoints.UserApi
import org.epilink.bot.http.sessions.ConnectedSession
import org.epilink.bot.http.sessions.RegisterSession
import org.epilink.bot.toApiResponse
import org.epilink.bot.toResponse
import org.slf4j.LoggerFactory

/**
 * Interface for the back-end. This component is responsible for the installation of features, error-handling and
 * routes.
 */
interface BackEnd {

    /**
     * Ktor module for the back-end features
     */
    fun Application.installFeatures()

    /**
     * Install the error handling interceptor. Automatically called by [epilinkApiModule].
     *
     * This automatically catches [EndpointException] and any other exceptions. For the former, a 400 or 500 error
     * is generated (depending on whether the exception is a [user exception][UserEndpointException] or an
     * [internal exception][InternalEndpointException]). For the latter, a 500 error is generated.
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
internal class BackEndImpl(scope: InjectionScope) : BackEnd {

    private val logger = LoggerFactory.getLogger("epilink.api")
    private val cacheClient: CacheClient by scope()
    private val registrationApi: RegistrationApi by scope()
    private val metaApi: MetaApi by scope()
    private val userApi: UserApi by scope()
    private val adminEndpoints: AdminEndpoints by scope()
    private val wsCfg: WebServerConfiguration by scope()

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
        install(Locations)
    }

    @Suppress("TooGenericExceptionCaught") // Catching all exceptinos is the entire point here
    override fun Route.installErrorHandling() {
        // Make sure that exceptions are not left for Ktor to try and figure out.
        // Ktor would figure it out, but we a) need to log them b) respond with an ApiResponse
        intercept(ApplicationCallPipeline.Monitoring) {
            try {
                coroutineScope {
                    proceed()
                }
            } catch (ex: UserEndpointException) {
                logger.info("Encountered an endpoint exception ${ex.errorCode.description}", ex)
                call.respond(HttpStatusCode.BadRequest, ex.toApiResponse())
            } catch (ex: InternalEndpointException) {
                logger.error("Encountered a back-end caused endpoint exception (${ex.errorCode}", ex)
                call.respond(HttpStatusCode.InternalServerError, ex.toApiResponse())
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
            if (wsCfg.enableAdminEndpoints) {
                adminEndpoints.install(this)
            }
        }
    }
}
