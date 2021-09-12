/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import guru.zoroark.shedinja.dsl.put
import guru.zoroark.shedinja.dsl.shedinja
import guru.zoroark.shedinja.dsl.shedinjaModule
import guru.zoroark.shedinja.environment.get
import guru.zoroark.shedinja.environment.named
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.epilink.bot.config.Configuration
import org.epilink.bot.db.BanLogic
import org.epilink.bot.db.BanLogicImpl
import org.epilink.bot.db.DatabaseFacade
import org.epilink.bot.db.GdprReport
import org.epilink.bot.db.GdprReportImpl
import org.epilink.bot.db.IdentityManager
import org.epilink.bot.db.IdentityManagerImpl
import org.epilink.bot.db.PermissionChecks
import org.epilink.bot.db.PermissionChecksImpl
import org.epilink.bot.db.UnlinkCooldown
import org.epilink.bot.db.UnlinkCooldownImpl
import org.epilink.bot.db.UserCreator
import org.epilink.bot.db.UserCreatorImpl
import org.epilink.bot.db.exposed.SQLiteExposedFacadeImpl
import org.epilink.bot.discord.BanManager
import org.epilink.bot.discord.BanManagerImpl
import org.epilink.bot.discord.Command
import org.epilink.bot.discord.Discord4JFacadeImpl
import org.epilink.bot.discord.DiscordClientFacade
import org.epilink.bot.discord.DiscordCommands
import org.epilink.bot.discord.DiscordCommandsImpl
import org.epilink.bot.discord.DiscordMessageSender
import org.epilink.bot.discord.DiscordMessageSenderImpl
import org.epilink.bot.discord.DiscordMessages
import org.epilink.bot.discord.DiscordMessagesI18n
import org.epilink.bot.discord.DiscordMessagesI18nImpl
import org.epilink.bot.discord.DiscordMessagesImpl
import org.epilink.bot.discord.DiscordTargets
import org.epilink.bot.discord.DiscordTargetsImpl
import org.epilink.bot.discord.RoleManager
import org.epilink.bot.discord.RoleManagerImpl
import org.epilink.bot.discord.cmd.CountCommand
import org.epilink.bot.discord.cmd.HelpCommand
import org.epilink.bot.discord.cmd.LangCommand
import org.epilink.bot.discord.cmd.UpdateCommand
import org.epilink.bot.http.BackEnd
import org.epilink.bot.http.BackEndImpl
import org.epilink.bot.http.DiscordBackEnd
import org.epilink.bot.http.FrontEndHandler
import org.epilink.bot.http.FrontEndHandlerImpl
import org.epilink.bot.http.HttpServer
import org.epilink.bot.http.HttpServerImpl
import org.epilink.bot.http.IdentityProvider
import org.epilink.bot.http.IdentityProviderMetadata
import org.epilink.bot.http.JwtVerifier
import org.epilink.bot.http.SessionChecker
import org.epilink.bot.http.SessionCheckerImpl
import org.epilink.bot.http.endpoints.AdminEndpoints
import org.epilink.bot.http.endpoints.AdminEndpointsImpl
import org.epilink.bot.http.endpoints.MetaApi
import org.epilink.bot.http.endpoints.MetaApiImpl
import org.epilink.bot.http.endpoints.RegistrationApi
import org.epilink.bot.http.endpoints.RegistrationApiImpl
import org.epilink.bot.http.endpoints.UserApi
import org.epilink.bot.http.endpoints.UserApiImpl
import org.epilink.bot.rulebook.Rulebook
import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis

/**
 * This class is responsible for holding configuration information and
 * references to things like the Discord client, the Database environment, the
 * server, etc.
 */
@Suppress("LongParameterList") // All parameters are required
class ServerEnvironment(
    private val cfg: Configuration,
    private val legal: LegalTexts,
    private val identityProviderMetadata: IdentityProviderMetadata,
    private val assets: Assets,
    private val discordStrings: Map<String, Map<String, String>>,
    private val defaultDiscordLanguage: String,
    rulebook: Rulebook
) {
    private val logger = LoggerFactory.getLogger("epilink.environment")

    /**
     * Base module, which contains the environment and the database
     */
    private val epilinkBaseModule = shedinjaModule {
        // Environment
        put { this@ServerEnvironment }
        // Facade between EpiLink and the actual database
        put<DatabaseFacade> { SQLiteExposedFacadeImpl(cfg.db) }
        // Cache-based features
        @Suppress("RemoveExplicitTypeArguments")
        put<CacheClient> { cfg.redis?.let { RedisClient(it) } ?: MemoryCacheClient() }
        // Admin list
        put(named("admins")) { cfg.admins }
        // "Glue" thing that calls everything required when accessing an identity
        put<IdentityManager>(::IdentityManagerImpl)
        // Ban-related logic (isActive...)
        put<BanLogic>(::BanLogicImpl)
        // Permission checks (e.g. check if a user can create an account)
        put<PermissionChecks>(::PermissionChecksImpl)
        // User creation logic
        put<UserCreator>(::UserCreatorImpl)
        // GDPR report generation utility
        put<GdprReport>(::GdprReportImpl)
        // Cooldown utility for preventing relink abuse
        put<UnlinkCooldown>(::UnlinkCooldownImpl)
    }

    /**
     * Discord module, for everything related to Discord
     */
    private val epilinkDiscordModule = shedinjaModule {
        // Rulebook
        put { rulebook }
        // Discord configuration
        put { cfg.discord }
        // Privacy configuration
        put { cfg.privacy }
        // Discord bot
        put<DiscordMessages>(::DiscordMessagesImpl)
        put<DiscordMessagesI18n> {
            DiscordMessagesI18nImpl(scope, discordStrings, defaultDiscordLanguage, cfg.discord.preferredLanguages)
        }
        // Role manager
        put<RoleManager>(::RoleManagerImpl)
        // Facade
        put<DiscordClientFacade> {
            Discord4JFacadeImpl(scope, cfg.tokens.discordOAuthClientId, cfg.tokens.discordToken)
        }
        put<DiscordMessageSender>(::DiscordMessageSenderImpl)
        put<BanManager>(::BanManagerImpl)
        put<DiscordCommands>(::DiscordCommandsImpl)
        put<DiscordTargets>(::DiscordTargetsImpl)
        put<List<Command>>(named("discord.commands")) {
            listOf(
                UpdateCommand(),
                HelpCommand(),
                LangCommand(),
                CountCommand()
            )
        }
    }

    /**
     * Web module, for everything related to the web server, the front-end and Ktor
     */
    private val epilinkWebModule = shedinjaModule {
        // HTTP (Ktor) server
        put<HttpServer>(::HttpServerImpl)

        put<BackEnd>(::BackEndImpl)

        put<FrontEndHandler>(::FrontEndHandlerImpl)

        put { cfg.server }

        put { cfg.idProvider }

        put { HttpClient(Apache) }

        put { DiscordBackEnd(scope, cfg.tokens.discordOAuthClientId, cfg.tokens.discordOAuthSecret) }

        put {
            IdentityProvider(
                scope,
                cfg.tokens.idpOAuthClientId,
                cfg.tokens.idpOAuthSecret,
                identityProviderMetadata.tokenUrl,
                identityProviderMetadata.authorizeUrl
            )
        }

        put {
            JwtVerifier(
                cfg.tokens.idpOAuthClientId,
                identityProviderMetadata.jwksUri,
                identityProviderMetadata.idClaim
            )
        }

        put { legal }

        put { assets }

        put<RegistrationApi>(::RegistrationApiImpl)

        put<MetaApi>(::MetaApiImpl)

        put<UserApi>(::UserApiImpl)

        put<AdminEndpoints>(::AdminEndpointsImpl)

        put<SessionChecker>(::SessionCheckerImpl)
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
        val app = shedinja {
            put(epilinkBaseModule)
            put(epilinkDiscordModule)
            put(epilinkWebModule)
        }

        logger.debug { "Starting components" }
        runCatching {
            runBlocking {
                coroutineScope {
                    launch {
                        logger.debug { "Staring Discord bot facade" }
                        measureTimeMillis { app.get<DiscordClientFacade>().start() }.also {
                            logger.debug { "Discord bot facade started in $it ms" }
                        }
                    }
                    launch {
                        logger.debug { "Starting cache provider" }
                        measureTimeMillis { app.get<CacheClient>().start() }.also {
                            logger.debug { "Cache provider started in $it ms" }
                        }
                    }
                    launch {
                        logger.debug { "Starting database facade" }
                        measureTimeMillis { app.get<DatabaseFacade>().start() }.also {
                            logger.debug { "Database facade started in $it ms" }
                        }
                    }
                }
            }
            logger.debug { "Starting server" }
            app.get<HttpServer>().startServer(wait = true)
        }.onFailure {
            logger.error("Encountered an exception on initialization", it)
        }
    }
}
