package org.epilink.bot

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import kotlinx.coroutines.runBlocking
import org.epilink.bot.config.LinkConfiguration
import org.epilink.bot.config.rulebook.Rulebook
import org.epilink.bot.db.LinkServerDatabase
import org.epilink.bot.discord.LinkDiscordBot
import org.epilink.bot.discord.LinkRoleManager
import org.epilink.bot.http.LinkBackEnd
import org.epilink.bot.http.LinkDiscordBackEnd
import org.epilink.bot.http.LinkHttpServer
import org.epilink.bot.http.LinkMicrosoftBackEnd
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
        single { LinkHttpServer() }

        single { LinkBackEnd() }

        single { cfg.server }

        single { HttpClient(Apache) }

        single {
            LinkDiscordBackEnd(
                cfg.tokens.discordOAuthClientId ?: error("Discord client ID cannot be null"),
                cfg.tokens.discordOAuthSecret ?: error("Discord OAuth secret cannot be null")
            )
        }

        single {
            LinkMicrosoftBackEnd(
                cfg.tokens.msftOAuthClientId ?: error("Microsoft client ID cannot be null"),
                cfg.tokens.msftOAuthSecret ?: error("Microsoft OAuth secret cannot be null"),
                cfg.tokens.msftTenant
            )
        }
    }

    /**
     * The name of this EpiLink instance
     */
    val name: String
        get() = cfg.name

    /**
     * Start Koin, the Discord bot and the HTTP server, in that order.
     */
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