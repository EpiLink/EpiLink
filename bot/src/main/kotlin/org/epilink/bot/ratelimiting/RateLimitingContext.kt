/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.ratelimiting

import java.time.Duration
import java.time.Instant

/**
 * This context provides information useful for [RateLimiter]s that wish to create new [Rate] objects.
 */
data class RateLimitingContext(
    /**
     * The limit, which is the maximum number amount of requests that can be made in the span of [resetTime]
     * milliseconds
     */
    val limit: Long,
    /**
     * The amount of milliseconds the rate limit lasts
     */
    val resetTime: Long
)

/**
 * Creates a new [Rate] object based on the current rate limiting context. Useful for creating brand new rates.
 */
fun RateLimitingContext.newRate(): Rate =
    Rate(limit, Instant.now() + Duration.ofMillis(resetTime))