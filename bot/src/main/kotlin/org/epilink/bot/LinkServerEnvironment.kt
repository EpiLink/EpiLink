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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.epilink.bot.config.LinkConfiguration
import org.epilink.bot.db.*
import org.epilink.bot.db.exposed.SQLiteExposedFacadeImpl
import org.epilink.bot.discord.*
import org.epilink.bot.discord.cmd.HelpCommand
import org.epilink.bot.discord.cmd.LangCommand
import org.epilink.bot.discord.cmd.UpdateCommand
import org.epilink.bot.http.*
import org.epilink.bot.http.endpoints.*
import org.epilink.bot.rulebook.Rulebook
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis

/**
 * This class is responsible for holding configuration information and
 * references to things like the Discord client, the Database environment, the
 * server, etc.
 */
class LinkServerEnvironment(
    private val cfg: LinkConfiguration,
    private val legal: LinkLegalTexts,
    private val assets: LinkAssets,
    private val discordStrings: Map<String, Map<String, String>>,
    private val defaultDiscordLanguage: String,
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
        // Cache-based features
        @Suppress("RemoveExplicitTypeArguments")
        single<CacheClient> { cfg.redis?.let { LinkRedisClient(it) } ?: MemoryCacheClient() }
        // Admin list
        single(named("admins")) { cfg.admins }
        // "Glue" thing that calls everything required when accessing an identity
        single<LinkIdManager> { LinkIdManagerImpl() }
        // Ban-related logic (isActive...)
        single<LinkBanLogic> { LinkBanLogicImpl() }
        // Permission checks (e.g. check if a user can create an account)
        single<LinkPermissionChecks> { LinkPermissionChecksImpl() }
        // User creation logic
        single<LinkUserCreator> { LinkUserCreatorImpl() }
        // GDPR report generation utility
        single<LinkGdprReport> { LinkGdprReportImpl() }
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
        single<LinkDiscordMessagesI18n> {
            LinkDiscordMessagesI18nImpl(discordStrings, defaultDiscordLanguage, cfg.discord.preferredLanguages)
        }
        // Role manager
        single<LinkRoleManager> { LinkRoleManagerImpl() }
        // Facade
        single<LinkDiscordClientFacade> {
            LinkDiscord4JFacadeImpl(cfg.tokens.discordOAuthClientId, cfg.tokens.discordToken)
        }
        single<LinkDiscordMessageSender> { LinkDiscordMessageSenderImpl() }
        single<LinkBanManager> { LinkBanManagerImpl() }
        single<LinkDiscordCommands> { LinkDiscordCommandsImpl() }
        single<LinkDiscordTargets> { LinkDiscordTargetsImpl() }
        single<List<Command>>(named("discord.commands")) {
            listOf(
                UpdateCommand(),
                HelpCommand(),
                LangCommand()
            )
        }
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

        single { legal }

        single { assets }

        single<LinkRegistrationApi> { LinkRegistrationApiImpl() }

        single<LinkMetaApi> { LinkMetaApiImpl() }

        single<LinkUserApi> { LinkUserApiImpl() }

        single<LinkAdminApi> { LinkAdminApiImpl() }

        single<LinkSessionChecks> { LinkSessionChecksImpl() }
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
                    launch {
                        logger.debug { "Staring Discord bot facade" }
                        measureTimeMillis { app.koin.get<LinkDiscordClientFacade>().start() }.also {
                            logger.debug { "Discord bot facade started in $it ms" }
                        }
                    }
                    launch {
                        logger.debug { "Starting cache provider" }
                        measureTimeMillis { app.koin.get<CacheClient>().start() }.also {
                            logger.debug { "Cache provider started in $it ms" }
                        }
                    }
                    launch {
                        logger.debug { "Starting database facade" }
                        measureTimeMillis { app.koin.get<LinkDatabaseFacade>().start() }.also {
                            logger.debug { "Database facade started in $it ms" }
                        }
                    }
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