/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.ratelimiting

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.epilink.bot.debug
import org.slf4j.LoggerFactory
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
     * @return A [Rate] object representing the rate handled and whether the limit should happen or not
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
    private val mutex = Mutex()
    private val isPurgeRunning = AtomicBoolean(false)
    private var lastPurgeTime = Instant.now()
    private val map = ConcurrentHashMap<String, Rate>()
    private val logger = LoggerFactory.getLogger("epilink.ratelimiting.inmemory")

    override suspend fun handle(ctx: RateLimitingContext, key: String): Rate = withContext(Dispatchers.Default) {
        map.compute(key) { _, v ->
            when {
                v == null -> ctx.newRate()
                v.hasExpired() -> ctx.newRate().also { logger.debug { "Bucket $key has expired, reset" } }
                else -> v.consume()
            }
        }!!.also { launchPurgeIfNeeded() }
    }

    private fun launchPurgeIfNeeded() {
        logger.debug { "Should purge: ${shouldPurge()}" }
        if (shouldPurge() && mutex.tryLock()) {
            logger.debug { "Launching purge in coroutine scope" }
            scope.launch {
                try {
                    logger.info("Purging rate limit information, current size = ${map.size}")
                    val shouldRemove = map.filterValues { it.hasExpired() }.keys
                    shouldRemove.forEach { k ->
                        logger.debug { "Removing stale bucket $k" }
                        map.remove(k)
                    }
                    lastPurgeTime = Instant.now()
                    isPurgeRunning.set(false)
                    logger.info("Purged rate limit information, new size = ${map.size}")
                } finally {
                    mutex.unlock()
                }
            }
        }
    }

    private fun shouldPurge() =
        map.size > mapPurgeSize && Duration.between(lastPurgeTime, Instant.now()) > mapPurgeWaitDuration
}