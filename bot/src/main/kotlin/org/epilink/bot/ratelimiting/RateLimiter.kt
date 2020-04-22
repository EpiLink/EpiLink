package org.epilink.bot.ratelimiting

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The RateLimiter is responsible for storing information about current rate limits
 *
 * @param K The type of the key. The [RateLimiting] feature uses String-based rate limiters
 */
interface RateLimiter<K> {
    /**
     * Handle a single call with the given key. Should return the resulting rate object.
     *
     * This function must also handle all rate-resetting activities.
     *
     * @param ctx The context to use to get more information about rates
     * @return A [Rate] object representing the rate handled.
     */
    suspend fun handle(ctx: RateLimitingContext, key: K): Rate
}

/**
 * An implementation of rate limiters that stores rate limits using a `ConcurrentHashMap`
 *
 * @param mapPurgeSize The size the map needs to reach in order for it to be purged on the next call
 * @param mapPurgeWaitDuration The amount of time to wait before the next purging.
 */
class InMemoryRateLimiter(
    private val mapPurgeSize: Int,
    private val mapPurgeWaitDuration: Duration
) : RateLimiter<String> {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val isPurgeRunning = AtomicBoolean(false)
    private var lastPurgeTime = Instant.now()
    private val map = ConcurrentHashMap<String, Rate>()

    override suspend fun handle(ctx: RateLimitingContext, key: String): Rate = withContext(Dispatchers.Default) {
        map.compute(key) { _, v ->
            when {
                v == null || v.hasExpired() -> ctx.newRate()
                else -> v.consume()
            }
        }!!.also { launchPurgeIfNeeded() }
    }

    private fun launchPurgeIfNeeded() {
        if (shouldPurge() && isPurgeRunning.compareAndExchange(false, true)) {
            scope.launch {
                val shouldRemove = map.filterValues { it.hasExpired() }.keys
                shouldRemove.forEach { map.remove(it) }
                lastPurgeTime = Instant.now()
                isPurgeRunning.set(false)
            }
        }
    }

    private fun shouldPurge() =
        map.size > mapPurgeSize && Duration.between(lastPurgeTime, Instant.now()) > mapPurgeWaitDuration
}