/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.cache.redis

import io.ktor.sessions.SessionStorage
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.epilink.backend.cache.CacheClient
import org.epilink.backend.cache.CacheResult
import org.epilink.backend.cache.RuleCache
import org.epilink.backend.cache.SimplifiedSessionStorage
import org.epilink.backend.cache.UnlinkCooldownStorage
import org.epilink.backend.common.EpiLinkException
import org.epilink.backend.common.StandardRoles
import org.epilink.backend.common.debug
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Base64

/**
 * An implementation of a cache client for use with a Redis server.
 */
class RedisClient(uri: String) : CacheClient {
    private val client = RedisClient.create(uri)
    private var connection: StatefulRedisConnection<String, String>? = null
    private val logger = LoggerFactory.getLogger("epilink.redis")

    override suspend fun start() = withContext(Dispatchers.IO) {
        logger.debug("Starting Redis client")
        connection = client.connect()
    }

    override fun newSessionStorage(prefix: String): SessionStorage {
        logger.debug("Creating Redis-backed storage for prefix $prefix")
        return RedisSessionStorage(
            connection ?: throwCallStartFirst(),
            prefix
        )
    }

    override fun newRuleCache(prefix: String): RuleCache {
        logger.debug("Creating Redis-backed caching rule mediator for prefix $prefix")
        return RedisRuleMediator(
            connection ?: throwCallStartFirst(),
            prefix
        )
    }

    override fun newUnlinkCooldownStorage(prefix: String): UnlinkCooldownStorage {
        logger.debug("Creating Redis-backed relink cooldown storage for prefix $prefix")
        return RedisUnlinkCooldownStorage(connection ?: throwCallStartFirst(), prefix)
    }

    private fun throwCallStartFirst(): Nothing =
        throw EpiLinkException("Redis client is not connected yet. Call start() first.")
}

/**
 * Implementation of a session storage using Redis/Lettuce
 */
private class RedisSessionStorage(
    connection: StatefulRedisConnection<String, String>,
    val prefix: String,
    val ttlSeconds: Long = 3600
) : SimplifiedSessionStorage() {
    private val logger = LoggerFactory.getLogger("epilink.redis.sessions.$prefix")
    private val redis = connection.reactive()
    private val b64enc = Base64.getEncoder()
    private val b64dec = Base64.getDecoder()

    private fun buildKey(id: String) = "$prefix$id"
    private fun String.decodeBase64(): ByteArray = b64dec.decode(this)
    private fun ByteArray.encodeBase64(): String = b64enc.encodeToString(this)

    override suspend fun read(id: String): ByteArray? {
        val key = buildKey(id)
        return redis.get(key).awaitSingle()?.decodeBase64()?.also {
            // Refresh TTL
            val success = redis.expire(key, ttlSeconds).awaitSingle()
            if (!success) {
                logger.warn("Failed to set TTL for session $key")
            }
        }
    }

    override suspend fun write(id: String, data: ByteArray?) {
        val key = buildKey(id)
        if (data == null) {
            redis.del(buildKey(id)).awaitSingle()
        } else {
            val encoded = data.encodeBase64()
            logger.debug { "Setting $key to $encoded" }
            redis.set(key, encoded).awaitSingle().also {
                if (it != "OK") {
                    logger.error("Got not OK response for setting key $key")
                }
            }
            redis.expire(key, ttlSeconds).awaitSingle().also {
                if (!it) {
                    logger.error("Failed to set TTL for session $key")
                }
            }
        }
    }

    override suspend fun invalidate(id: String) {
        logger.debug { "Deleting key $id" }
        redis.del(buildKey(id)).awaitSingle()
    }
}

/**
 * Implementation of a rule mediator using a Redis connection.
 *
 * Each cached key has information on the rule and the user. The value is the remembered list of EpiLink roles the rule
 * previously returned. Rules that do not have a cache duration are not cached at all. Redis is responsible for managing
 * timeouts since we flag keys for expiry with the `EXPIRES` command.
 *
 * Additionally, a `_INDEX_` fake cache entry tracks for each user which rules are cached, so that all of them can be
 * invalidated immediately in case of role invalidation (e.g. user's identity is removed). So, if two rules `RuleA` and
 * `RuleB` are cached for user ID `1234` like so `el_rc_RuleA_1234 = [...]` and `el_rc_RuleB_1234 = [...]`, then we have
 * `el_rc__INDEX__1234 = [el_rc_RuleA_1234, el_rc_RuleB_1234]`. During invalidation, this index entry gets popped and we
 * remove the corresponding keys from the Redis database.
 *
 * Note that there is no difference in Redis between an empty set and an absent set. If a rule does not yield any role
 * (e.g. strong rule used for a non-identifiable user), then a list of the single special role name
 * [_none][StandardRoles.None] will be put in the key instead.
 */
private class RedisRuleMediator(
    connection: StatefulRedisConnection<String, String>,
    private val prefix: String
) : RuleCache {
    private val redis = connection.reactive()
    private val logger = LoggerFactory.getLogger("epilink.redis.rulecache")
/*
    override suspend fun runRule(
        rule: Rule,
        discordId: String,
        discordName: String,
        discordDisc: String,
        identity: String?
    ): RuleResult = runCatching {
        when (val cachedResult = getCachedRoles(rule, discordId)) {
            null -> {
                logger.debug { "No cached results or cache disabled for rule $rule (ID $discordId): running rule" }
                execAndCacheRule(rule, discordId, discordName, discordDisc, identity)
            }
            else -> {
                logger.debug { "Returning cached results for ${rule.name} on $discordId: $cachedResult" }
                cachedResult
            }
        }
    }.fold({ RuleResult.Success(it) }) { RuleResult.Failure(it) }*/
/*
    private suspend fun execAndCacheRule(
        rule: Rule,
        discordId: String,
        discordName: String,
        discordDisc: String,
        identity: String?
    ): List<String> {
        val result = rule.runCatching { run(discordId, discordName, discordDisc, identity) }.getOrElse {
            logger.error("Encountered error on running rule ${rule.name}", it)
            throw it
        }
        // Cache the results if the rule should be cached
        if (rule.cacheDuration != null) {
            cacheResults(rule.name, rule.cacheDuration, discordId, result)
        }
        return result
    }
*/
    override suspend fun putCache(
        ruleName: String,
        cacheDuration: Duration,
        discordId: String,
        roles: List<String>
    ) {
        logger.debug {
            "Caching rule $ruleName results $roles for Discord ID $discordId for ${cacheDuration.toSeconds()} seconds"
        }
        val key = buildKey(ruleName, discordId)
        // Add the key to the index entry
        redis.sadd(buildIndexKey(discordId), key).awaitSingle()
        // Clear the value (just in case)
        redis.del(key).awaitSingle()
        // If roles were returned, add them to the key. Otherwise, use the special `_none` as the only element
        val valueToCache = roles.ifEmpty { listOf(StandardRoles.None.roleName) }
        @Suppress("SpreadOperator") // No other choice here
        redis.sadd(key, *valueToCache.toTypedArray()).awaitSingle()
        redis.expire(key, cacheDuration.toSeconds()).awaitSingle().also {
            if (!it) {
                logger.error("Failed to set TTL for session $key")
            }
        }
    }

    private suspend fun getCachedRoles(ruleName: String, discordId: String): List<String>? {
        val key = buildKey(ruleName, discordId)
        return if (!cacheExists(key)) {
            logger.debug { "No known cache for $key" }
            null
        } else {
            val list = redis.smembers(key).collectList().awaitSingle().also { logger.debug { "Cached $key = $it" } }
            if (list == listOf(StandardRoles.None.roleName)) {
                listOf()
            } else list
        }
    }

    override suspend fun invalidateCache(discordId: String) {
        logger.debug { "Invalidating rule role cache for $discordId" }
        val indexKey = buildIndexKey(discordId)
        coroutineScope {
            while (true) {
                val key = redis.spop(indexKey).awaitFirstOrNull() ?: break
                launch { redis.del(key).awaitSingle() }
            }
        }
    }

    override suspend fun tryCache(ruleName: String, discordId: String): CacheResult =
        getCachedRoles(ruleName, discordId)
            ?.let { CacheResult.Hit(it) }
            ?: CacheResult.NotFound

    // Note: prefix has a trailing _
    private fun buildKey(ruleName: String, discordId: String) = prefix + ruleName + "_" + discordId
    private fun buildIndexKey(discordId: String) = prefix + "_INDEX__" + discordId

    private suspend fun cacheExists(key: String) =
        // There exists a matching key
        redis.exists(key).awaitSingle() == 1L
}

/**
 * Implementation of RelinkCooldownStorage for Redis.
 */
class RedisUnlinkCooldownStorage(
    connection: StatefulRedisConnection<String, String>,
    private val prefix: String
) : UnlinkCooldownStorage {
    private val redis = connection.reactive()
    private fun buildKey(id: String) = "$prefix$id"

    override suspend fun canUnlink(userId: String): Boolean {
        // The user can relink if there are no cooldown keys
        return redis.exists(buildKey(userId)).awaitSingle() == 0L
    }

    override suspend fun refreshCooldown(userId: String, seconds: Long) {
        if (seconds <= 0) {
            redis.del(buildKey(userId)).awaitSingle()
        } else {
            redis.setnx(buildKey(userId), "true").awaitSingle()
            redis.expire(buildKey(userId), seconds).awaitSingle()
        }
    }
}
