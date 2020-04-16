package org.epilink.bot.http

import io.ktor.sessions.SessionStorage
import io.ktor.sessions.SessionStorageMemory
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.epilink.bot.LinkException
import org.epilink.bot.debug
import org.slf4j.LoggerFactory
import java.util.*

/**
 * A SessionStorageProvider is responsible for creating session storage objects. Each environment has one provider. This
 * can be used to produce session storage objects backed by a database or Redis server.
 */
interface SessionStorageProvider {
    /**
     * Create a storage, with the given prefix. The prefix can be ignored if unnecessary. Different session types have
     * two different prefixes.
     */
    fun createStorage(prefix: String): SessionStorage

    /**
     * Start this provider. Can be used to trigger a connection to the database/Redis server.
     */
    suspend fun start()
}

/**
 * An implementation of a session storage provider for use with a Redis server.
 */
class LinkRedisClient(uri: String) : SessionStorageProvider {
    private val logger = LoggerFactory.getLogger("epilink.redis")
    private val client = RedisClient.create(uri)
    private var connection: StatefulRedisConnection<String, String>? = null

    override suspend fun start() = withContext(Dispatchers.IO) {
        logger.debug("Starting Redis client")
        connection = client.connect()
    }

    override fun createStorage(prefix: String): SessionStorage {
        logger.debug("Creating Redis-backed storage for prefix $prefix")
        return RedisSessionStorage(
            connection ?: throw LinkException("Redis client is not connected yet. Call start() first."),
            prefix
        )
    }
}

/**
 * A storage provider that only builds [SessionStorageMemory] objects. Should only be used for dev purposes.
 */
class MemoryStorageProvider : SessionStorageProvider {
    override fun createStorage(prefix: String): SessionStorageMemory = SessionStorageMemory()

    override suspend fun start() {}
}

/**
 * Implementation of a session storage using Redis/Lettuce
 */
private class RedisSessionStorage(
    connection: StatefulRedisConnection<String, String>,
    val prefix: String,
    val ttlSeconds: Long = 3600
) : SimplifiedSessionStorage() {
    private val logger = LoggerFactory.getLogger("epilink.redis.$prefix")
    private val redis = connection.reactive()
    private fun buildKey(id: String) = "$prefix$id"
    private val b64enc = Base64.getEncoder()
    private val b64dec = Base64.getDecoder()

    private fun String.decodeBase64(): ByteArray = b64dec.decode(this)
    private fun ByteArray.encodeBase64(): String = b64enc.encodeToString(this)

    override suspend fun read(id: String): ByteArray? {
        val key = buildKey(id)
        return redis.get(key).awaitSingle()?.decodeBase64()?.also {
            // Refresh TTL
            val success = redis.expire(key, ttlSeconds).awaitSingle()
            if (!success)
                logger.warn("Failed to set TTL for session $key")
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
                    logger.error("Received not OK reply from Redis when writing key $key: $it")
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