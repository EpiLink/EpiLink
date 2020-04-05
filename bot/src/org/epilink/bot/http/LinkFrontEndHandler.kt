package org.epilink.bot.http

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.resolveResource
import io.ktor.http.defaultForFileExtension
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.Route
import io.ktor.routing.get

/**
 * Add the front-end handler to the given route
 */
fun Route.frontEndHandler(serveFrontEnd: Boolean, frontEndUrl: String?) {
    /*
     * Main endpoint. If the user directly tries to connect to the
     * back-end, redirect them to the front-end (or 404 if the url
     * is unknown).
     */
    get("/{...}") {
        if (serveFrontEnd) {
            call.respondBootstrapped()
        } else {
            if (frontEndUrl == null) {
                // 404
                call.respond(HttpStatusCode.NotFound)
            } else {
                // Redirect to frontend
                call.respondRedirect(
                    frontEndUrl + call.parameters.getAll("path")?.joinToString("/"),
                    permanent = true
                )
            }
        }
    }
}

/**
 * Default response for bootstrap requests: simply serve the index. Only
 * called when the frontend is here for sure.
 */
private suspend fun ApplicationCall.respondDefaultFrontEnd() {
    val def = resolveResource("index.prod.html", "frontend") {
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


