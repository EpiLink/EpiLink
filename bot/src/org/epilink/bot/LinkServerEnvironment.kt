package org.epilink.bot

import kotlinx.coroutines.runBlocking
import org.epilink.bot.config.LinkConfiguration
import org.epilink.bot.config.rulebook.Rulebook
import org.epilink.bot.db.LinkServerDatabase
import org.epilink.bot.discord.LinkDiscordBot
import org.epilink.bot.http.LinkHttpServer

/**
 * This class is responsible for holding configuration information and
 * references to things like the Discord client, the Database environment, the
 * server, etc.
 */
class LinkServerEnvironment(
    private val cfg: LinkConfiguration,
    rulebook: Rulebook
) {
    var database: LinkServerDatabase =
        LinkServerDatabase(cfg)
        private set

    val discord: LinkDiscordBot =
        LinkDiscordBot(
            database,
            cfg.discord,
            cfg.privacy,
            cfg.tokens.discordToken ?: error("Discord token cannot be null"),
            cfg.tokens.discordOAuthClientId ?: error("Discord client ID cannot be null"),
            rulebook
        )

    private var server: LinkHttpServer =
        LinkHttpServer(this, cfg.server, cfg.tokens)

    val name: String
        get() = cfg.name

    fun start() {
        runBlocking { discord.start() }
        server.startServer(wait = true)
    }
}