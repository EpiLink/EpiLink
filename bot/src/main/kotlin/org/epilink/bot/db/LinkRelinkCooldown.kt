package org.epilink.bot.db

import org.epilink.bot.CacheClient
import org.epilink.bot.config.LinkWebServerConfiguration
import org.koin.core.KoinComponent
import org.koin.core.get
import org.koin.core.inject

interface LinkRelinkCooldown {
    suspend fun canRelink(userId: String): Boolean
    suspend fun refreshCooldown(userId: String)
    suspend fun deleteCooldown(userId: String)
}

internal class LinkRelinkCooldownImpl : KoinComponent, LinkRelinkCooldown {
    private val serverConfiguration: LinkWebServerConfiguration by inject()
    private val storage: RelinkCooldownStorage by lazy {
        get<CacheClient>().newRelinkCooldownStorage("el_rlc_")
    }

    override suspend fun canRelink(userId: String): Boolean = storage.canRelink(userId)

    override suspend fun refreshCooldown(userId: String) =
        storage.refreshCooldown(userId, serverConfiguration.relinkCooldown)

    override suspend fun deleteCooldown(userId: String) =
        storage.refreshCooldown(userId, 0L)
}