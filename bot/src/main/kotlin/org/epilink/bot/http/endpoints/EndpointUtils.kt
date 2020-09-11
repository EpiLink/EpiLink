package org.epilink.bot.http.endpoints

import guru.zoroark.ratelimit.rateLimited
import io.ktor.application.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import java.time.Duration

/**
 * Convenience method for translating EpiLink ratelimiting settings into ktor-rate-limit calls
 */
fun Route.limited(
    limit: Int,
    additionalKeyExtractor: ApplicationCall.() -> String = { "" },
    callback: Route.() -> Unit
): Route =
    if (limit < 0) {
        apply { callback() }
    } else
        rateLimited(
            limit.toLong(),
            Duration.ofMinutes(1L),
            additionalKeyExtractor, callback
        )

/**
 * Equivalent to
 *
 *     route(path) {
 *         limited(limit, additionalKeyExtractor) {
 *             /* callback */
 *         }
 *     }
 *
 * This takes less indentation in the code that declares endpoints.
 */
@ContextDsl
fun Route.limitedRoute(
    path: String,
    limit: Int,
    additionalKeyExtractor: ApplicationCall.() -> String = { "" },
    callback: Route.() -> Unit
): Route = route(path) {
    limited(limit, additionalKeyExtractor, callback)
}