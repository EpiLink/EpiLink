/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.epilink.backend.cache.CacheClient
import org.epilink.backend.cache.MemoryCacheClient
import org.epilink.backend.cache.RuleCache
import org.epilink.backend.cache.redis.RedisClient
import org.epilink.backend.commands.CountCommand
import org.epilink.backend.commands.HelpCommand
import org.epilink.backend.commands.LangCommand
import org.epilink.backend.commands.UpdateCommand
import org.epilink.backend.common.debug
import org.epilink.backend.config.Configuration
import org.epilink.backend.db.DatabaseFacade
import org.epilink.backend.db.exposed.SQLiteExposedFacadeImpl
import org.epilink.backend.discord.Discord4JFacadeImpl
import org.epilink.backend.discord.DiscordClientFacade
import org.epilink.backend.discord.DiscordMessageHandler
import org.epilink.backend.discord.NewUserHandler
import org.epilink.backend.discord.WelcomeMessageProvider
import org.epilink.backend.http.BackEnd
import org.epilink.backend.http.BackEndImpl
import org.epilink.backend.http.DiscordBackEnd
import org.epilink.backend.http.FrontEndHandler
import org.epilink.backend.http.FrontEndHandlerImpl
import org.epilink.backend.http.HttpServer
import org.epilink.backend.http.HttpServerImpl
import org.epilink.backend.http.IdentityProvider
import org.epilink.backend.http.IdentityProviderMetadata
import org.epilink.backend.http.JwtVerifier
import org.epilink.backend.http.SessionChecker
import org.epilink.backend.http.SessionCheckerImpl
import org.epilink.backend.http.endpoints.AdminEndpoints
import org.epilink.backend.http.endpoints.AdminEndpointsImpl
import org.epilink.backend.http.endpoints.MetaApi
import org.epilink.backend.http.endpoints.MetaApiImpl
import org.epilink.backend.http.endpoints.RegistrationApi
import org.epilink.backend.http.endpoints.RegistrationApiImpl
import org.epilink.backend.http.endpoints.UserApi
import org.epilink.backend.http.endpoints.UserApiImpl
import org.epilink.backend.rulebook.Rulebook
import org.epilink.backend.services.BanLogic
import org.epilink.backend.services.BanLogicImpl
import org.epilink.backend.services.BanManager
import org.epilink.backend.services.BanManagerImpl
import org.epilink.backend.services.Command
import org.epilink.backend.services.DiscordCommands
import org.epilink.backend.services.DiscordCommandsImpl
import org.epilink.backend.services.DiscordMessageSender
import org.epilink.backend.services.DiscordMessageSenderImpl
import org.epilink.backend.services.DiscordMessages
import org.epilink.backend.services.DiscordMessagesI18n
import org.epilink.backend.services.DiscordMessagesI18nImpl
import org.epilink.backend.services.DiscordMessagesImpl
import org.epilink.backend.services.DiscordTargets
import org.epilink.backend.services.DiscordTargetsImpl
import org.epilink.backend.services.GdprReport
import org.epilink.backend.services.GdprReportImpl
import org.epilink.backend.services.IdentityManager
import org.epilink.backend.services.IdentityManagerImpl
import org.epilink.backend.services.PermissionChecks
import org.epilink.backend.services.PermissionChecksImpl
import org.epilink.backend.services.RoleManager
import org.epilink.backend.services.RoleManagerImpl
import org.epilink.backend.services.RuleExecutor
import org.epilink.backend.services.RuleExecutorImpl
import org.epilink.backend.services.UnlinkCooldown
import org.epilink.backend.services.UnlinkCooldownImpl
import org.epilink.backend.services.UserCreator
import org.epilink.backend.services.UserCreatorImpl
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
        @Suppress("RemoveExplicitTypeArguments")
        single<RuleCache> { get<CacheClient>().newRuleCache("el_rc_") }
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
        // Rule executor
        single<RuleExecutor> { RuleExecutorImpl() }
        // Discord configuration
        single { cfg.discord }
        // Privacy configuration
        single { cfg.privacy }
        // Discord bot
        single<DiscordMessages> { DiscordMessagesImpl() }
        single<WelcomeMessageProvider> { get<DiscordMessages>() }
        single<DiscordMessagesI18n> {
            DiscordMessagesI18nImpl(discordStrings, defaultDiscordLanguage, cfg.discord.preferredLanguages)
        }
        // Role manager
        single<RoleManager> { RoleManagerImpl() }
        single<NewUserHandler> { get<RoleManager>() }
        // Facade
        single<DiscordClientFacade> {
            Discord4JFacadeImpl(cfg.tokens.discordOAuthClientId, cfg.tokens.discordToken)
        }
        single<DiscordMessageSender> { DiscordMessageSenderImpl() }
        single<BanManager> { BanManagerImpl() }
        single<DiscordCommands> { DiscordCommandsImpl() }
        single<DiscordMessageHandler> { get<DiscordCommands>() }
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
