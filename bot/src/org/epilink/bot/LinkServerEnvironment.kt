package org.epilink.bot

import kotlinx.coroutines.runBlocking
import org.epilink.bot.config.LinkConfiguration
import org.epilink.bot.config.rulebook.Rulebook
import org.epilink.bot.db.LinkServerDatabase
import org.epilink.bot.discord.LinkDiscordBot
import org.epilink.bot.discord.LinkRoleManager
import org.epilink.bot.http.LinkBackEnd
import org.epilink.bot.http.LinkHttpServer
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module
import org.koin.logger.slf4jLogger

/**
 * This class is responsible for holding configuration information and
 * references to things like the Discord client, the Database environment, the
 * server, etc.
 */
class LinkServerEnvironment(
    private val cfg: LinkConfiguration,
    rulebook: Rulebook
) {
    private val epilinkBaseModule = module {
        // Environment
        single { this@LinkServerEnvironment }
        // Database
        single { LinkServerDatabase(cfg.db) }
    }

    private val epilinkDiscordModule = module {
        // Rulebook
        single { rulebook }
        // Discord configuration
        single { cfg.discord }
        // Privacy configuration
        single { cfg.privacy }
        // Discord bot
        single {
            LinkDiscordBot(
                cfg.tokens.discordToken ?: error("Discord token cannot be null "),
                cfg.tokens.discordOAuthClientId ?: error("Discord client ID cannot be null")
            )
        }
        // Role manager
        single { LinkRoleManager() }
    }

    private val epilinkWebModule = module {
        // HTTP (Ktor) server
        single { LinkHttpServer(cfg.tokens) }

        single { LinkBackEnd(cfg.tokens) }

        single { cfg.server }
    }

    val name: String
        get() = cfg.name

    fun start() {
        val app = startKoin {
            slf4jLogger(Level.ERROR)
            modules(epilinkBaseModule, epilinkDiscordModule, epilinkWebModule)
        }
        runBlocking {
            app.koin.get<LinkDiscordBot>().start()
        }
        app.koin.get<LinkHttpServer>().startServer(wait = true)
    }
}