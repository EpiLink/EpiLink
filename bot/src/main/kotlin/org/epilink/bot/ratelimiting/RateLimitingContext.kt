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