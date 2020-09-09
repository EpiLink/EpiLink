package org.epilink.bot.db

interface RelinkCooldownStorage {
    suspend fun canRelink(userId: String): Boolean

    suspend fun refreshCooldown(userId: String, seconds: Long)
}