/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import kotlinx.coroutines.*
import org.epilink.bot.config.LinkConfiguration
import org.epilink.bot.rulebook.Rulebook
import org.epilink.bot.db.LinkDatabaseFacade
import org.epilink.bot.db.LinkServerDatabase
import org.epilink.bot.db.LinkServerDatabaseImpl
import org.epilink.bot.db.exposed.SQLiteExposedFacadeImpl
import org.epilink.bot.discord.*
import org.epilink.bot.discord.LinkDiscordMessagesImpl
import org.epilink.bot.discord.LinkRoleManagerImpl
import org.epilink.bot.http.*
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory

/**
 * This class is responsible for holding configuration information and
 * references to things like the Discord client, the Database environment, the
 * server, etc.
 */
class LinkServerEnvironment(
    private val cfg: LinkConfiguration,
    private val legal: LinkLegalTexts,
    rulebook: Rulebook
) {
    private val logger = LoggerFactory.getLogger("epilink.environment")

    /**
     * Base module, which contains the environment and the database
     */
    val epilinkBaseModule = module {
        // Environment
        single { this@LinkServerEnvironment }
        // Facade between EpiLink and the actual database
        single<LinkDatabaseFacade> { SQLiteExposedFacadeImpl(cfg.db) }
        // Higher level database functionality
        single<LinkServerDatabase> { LinkServerDatabaseImpl() }
    }

    /**
     * Discord module, for everything related to Discord
     */
    val epilinkDiscordModule = module {
        // Rulebook
        single { rulebook }
        // Discord configuration
        single { cfg.discord }
        // Privacy configuration
        single { cfg.privacy }
        // Discord bot
        single<LinkDiscordMessages> { LinkDiscordMessagesImpl() }
        // Role manager
        single<LinkRoleManager> { LinkRoleManagerImpl() }
        // Facade
        single<LinkDiscordClientFacade> {
            LinkDiscord4JFacadeImpl(cfg.tokens.discordOAuthClientId, cfg.tokens.discordToken)
        }
        // Rule mediator (e.g. for caching)
        single<RuleMediator> { NoCacheRuleMediator() }
    }

    /**
     * Web module, for everything related to the web server, the front-end and Ktor
     */
    val epilinkWebModule = module {
        // HTTP (Ktor) server
        single<LinkHttpServer> { LinkHttpServerImpl() }

        single<LinkBackEnd> { LinkBackEndImpl() }

        single<LinkFrontEndHandler> { LinkFrontEndHandlerImpl() }

        single { cfg.server }

        single { HttpClient(Apache) }

        single {
            LinkDiscordBackEnd(cfg.tokens.discordOAuthClientId, cfg.tokens.discordOAuthSecret)
        }

        single {
            LinkMicrosoftBackEnd(cfg.tokens.msftOAuthClientId, cfg.tokens.msftOAuthSecret, cfg.tokens.msftTenant)
        }

        single { cfg.redis?.let { LinkRedisClient(it) } ?: MemoryStorageProvider() }

        single { legal }
    }

    /**
     * The name of this EpiLink instance
     */
    val name: String
        get() = cfg.name

    /**
     * Start Koin, the Discord bot + session storage provider and then the HTTP server, in that order.
     */
    fun start() {
        logger.debug { "Starting Koin" }
        val app = startKoin {
            slf4jLogger(Level.ERROR)
            modules(epilinkBaseModule, epilinkDiscordModule, epilinkWebModule)
        }

        logger.debug { "Starting components" }
        try {
            runBlocking {
                coroutineScope {
                    listOf(
                        async {
                            logger.debug { "Staring Discord bot facade" }
                            app.koin.get<LinkDiscordClientFacade>().start()
                        },
                        async {
                            logger.debug { "Starting storage provider" }
                            app.koin.get<SessionStorageProvider>().start()
                        },
                        async {
                            logger.debug { "Starting database facade" }
                            app.koin.get<LinkDatabaseFacade>().start()
                        }
                    ).awaitAll()
                }
            }
            logger.debug { "Starting server" }
            app.koin.get<LinkHttpServer>().startServer(wait = true)
        } catch (ex: Exception) {
            logger.error("Encountered an exception on initialization", ex)
            return
        }
    }
}