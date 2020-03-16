package org.epilink.bot.http

import com.auth0.jwt.algorithms.Algorithm
import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import org.epilink.bot.LinkServerEnvironment

/**
 * The back-end, defining API endpoints and more
 */
class LinkBackEnd(
    /**
     * The HTTP server that created this back end
     */
    private val server: LinkHttpServer,
    /**
     * The environment the back end lives in
     */
    private val env: LinkServerEnvironment,
    /**
     * The algorithm to use for JWT
     */
    private val jwtAlgorithm: Algorithm,
    /**
     * The duration of a session (for use with [makeJwt])
     */
    private val sessionDuration: Long
) {
    /**
     * Defines the API endpoints. Served under /api/v1
     *
     * Anything responded in here SHOULD use [ApiResponse] in JSON form.
     */
    fun Route.epilinkApiV1() {
        /*
         * Just a hello world for now, answering in JSON
         */
        get("hello") {
            call.respond(
                ApiResponse(true, "Hello World", null)
            )
        }
    }
}
