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
    private val epilinkBaseModule = module {
        // Environment
        single { this@ServerEnvironment }
        // Facade between EpiLink and the actual database
        single<DatabaseFacade> { SQLiteExposedFacadeImpl(cfg.db) }
        // Cache-based features
        @Suppress("RemoveExplicitTypeArguments")
        single<CacheClient> { cfg.redis?.let { RedisClient(it) } ?: MemoryCacheClient() }
        // Admin list
        single(named("admins")) { cfg.admins }
        // "Glue" thing that calls everything required when accessing an identity
        single<IdentityManager> { IdentityManagerImpl() }
        // Ban-related logic (isActive...)
        single<BanLogic> { BanLogicImpl() }
        // Permission checks (e.g. check if a user can create an account)
        single<PermissionChecks> { PermissionChecksImpl() }
        // User creation logic
        single<UserCreator> { UserCreatorImpl() }
        // GDPR report generation utility
        single<GdprReport> { GdprReportImpl() }
        // Cooldown utility for preventing relink abuse
        single<UnlinkCooldown> { UnlinkCooldownImpl() }
    }

    /**
     * Discord module, for everything related to Discord
     */
    private val epilinkDiscordModule = module {
        // Rulebook
        single { rulebook }
        // Discord configuration
        single { cfg.discord }
        // Privacy configuration
        single { cfg.privacy }
        // Discord bot
        single<DiscordMessages> { DiscordMessagesImpl() }
        single<DiscordMessagesI18n> {
            DiscordMessagesI18nImpl(discordStrings, defaultDiscordLanguage, cfg.discord.preferredLanguages)
        }
        // Role manager
        single<RoleManager> { RoleManagerImpl() }
        // Facade
        single<DiscordClientFacade> {
            Discord4JFacadeImpl(cfg.tokens.discordOAuthClientId, cfg.tokens.discordToken)
        }
        single<DiscordMessageSender> { DiscordMessageSenderImpl() }
        single<BanManager> { BanManagerImpl() }
        single<DiscordCommands> { DiscordCommandsImpl() }
        single<DiscordTargets> { DiscordTargetsImpl() }
        single<List<Command>>(named("discord.commands")) {
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
    private val epilinkWebModule = module {
        // HTTP (Ktor) server
        single<HttpServer> { HttpServerImpl() }

        single<BackEnd> { BackEndImpl() }

        single<FrontEndHandler> { FrontEndHandlerImpl() }

        single { cfg.server }

        single { cfg.idProvider }

        single { HttpClient(Apache) }

        single {
            DiscordBackEnd(cfg.tokens.discordOAuthClientId, cfg.tokens.discordOAuthSecret)
        }

        single {
            IdentityProvider(
                cfg.tokens.idpOAuthClientId,
                cfg.tokens.idpOAuthSecret,
                identityProviderMetadata.tokenUrl,
                identityProviderMetadata.authorizeUrl
            )
        }

        single {
            JwtVerifier(
                cfg.tokens.idpOAuthClientId,
                identityProviderMetadata.jwksUri,
                identityProviderMetadata.idClaim
            )
        }

        single { legal }

        single { assets }

        single<RegistrationApi> { RegistrationApiImpl() }

        single<MetaApi> { MetaApiImpl() }

        single<UserApi> { UserApiImpl() }

        single<AdminEndpoints> { AdminEndpointsImpl() }

        single<SessionChecker> { SessionCheckerImpl() }
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
        runCatching {
            runBlocking {
                coroutineScope {
                    launch {
                        logger.debug { "Staring Discord bot facade" }
                        measureTimeMillis { app.koin.get<DiscordClientFacade>().start() }.also {
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
                        measureTimeMillis { app.koin.get<DatabaseFacade>().start() }.also {
                            logger.debug { "Database facade started in $it ms" }
                        }
                    }
                }
            }
            logger.debug { "Starting server" }
            app.koin.get<HttpServer>().startServer(wait = true)
        }.onFailure {
            logger.error("Encountered an exception on initialization", it)
        }
    }
}
