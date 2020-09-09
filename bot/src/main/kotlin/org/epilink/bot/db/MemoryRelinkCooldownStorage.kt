package org.epilink.bot.db

import java.time.Duration
import java.time.Instant

class MemoryRelinkCooldownStorage : RelinkCooldownStorage {
    private val map = HashMap<String, Instant>()

    override suspend fun canRelink(userId: String): Boolean {
        val stopsAt = map[userId] ?: return true
        return if (stopsAt <= Instant.now()) {
            map.remove(userId)
            true
        } else false
    }

    override suspend fun refreshCooldown(userId: String, seconds: Long) {
        if (seconds <= 0) map.remove(userId)
        map[userId] = Instant.now() + Duration.ofSeconds(seconds)
    }
}