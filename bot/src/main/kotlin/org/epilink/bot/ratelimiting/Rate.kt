package org.epilink.bot.ratelimiting

import java.time.Instant

/**
 * Represents a single rate limit state. Immutable.
 */
data class Rate(
    /**
     * The number of remaining requests
     */
    val remainingRequests: Long,
    /**
     * The instant at which the rate limit is invalid and reset.
     */
    val resetAt: Instant
)

/**
 * Returns true if the rate has expired (we have passed its reset instant)
 */
fun Rate.hasExpired() = resetAt < Instant.now()

/**
 * Consumes a single request and returns the modified rate.
 */
fun Rate.consume() = copy(remainingRequests = (remainingRequests - 1).coerceAtLeast(0))

/**
 * Returns true if the 429 HTTP error should be returned, as in, this rate has not expired and there are no remaining
 * requests
 */
fun Rate.shouldLimit() = !hasExpired() && remainingRequests == 0L