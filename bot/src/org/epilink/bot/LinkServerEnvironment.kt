package org.epilink.bot

import kotlinx.coroutines.runBlocking
import org.epilink.bot.config.LinkConfiguration
import org.epilink.bot.db.LinkServerDatabase
import org.epilink.bot.discord.LinkDiscordBot
import org.epilink.bot.http.LinkHttpServer

/**
 * This class is responsible for holding configuration information and
 * references to things like the Discord client, the Database environment, the
 * server, etc.
 */
class LinkServerEnvironment(
    private val cfg: LinkConfiguration
) {
    var database: LinkServerDatabase =
        LinkServerDatabase(cfg)
        private set

    private val discordBot: LinkDiscordBot =
        LinkDiscordBot(
            this,
            cfg.discord,
            cfg.tokens.discordToken ?: error("Discord token cannot be null"),
            cfg.tokens.discordOAuthClientId ?: error("Discord client ID cannot be null")
        )

    private var server: LinkHttpServer =
        LinkHttpServer(this, cfg.server, cfg.tokens)

    val name: String
        get() = cfg.name

    fun start() {
        runBlocking { discordBot.start() }
        server.startServer(wait = true)
    }
}