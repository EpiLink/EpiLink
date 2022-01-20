/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.http.endpoints

import guru.zoroark.ratelimit.rateLimited
import io.ktor.application.ApplicationCall
import io.ktor.routing.Route
import io.ktor.routing.route
import io.ktor.util.pipeline.ContextDsl
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
    } else {
        rateLimited(
            limit.toLong(),
            Duration.ofMinutes(1L),
            additionalKeyExtractor, callback
        )
    }

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
