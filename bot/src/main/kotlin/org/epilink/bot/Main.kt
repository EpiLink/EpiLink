/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.DefaultHelpFormatter
import com.xenomachina.argparser.mainBody
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.get
import kotlinx.coroutines.*
import org.epilink.bot.config.*
import org.epilink.bot.http.MetadataOrFailure
import org.epilink.bot.http.identityProviderMetadataFromDiscovery
import org.epilink.bot.rulebook.Rulebook
import org.epilink.bot.rulebook.loadRules
import org.epilink.bot.rulebook.loadRulesWithCache
import org.epilink.bot.rulebook.readScriptSource
import org.slf4j.LoggerFactory
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.streams.asSequence
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger("epilink.main")

/**
 * CLI Arguments class for EpiLink
 */
class CliArgs(parser: ArgParser) {
    /**
     * Path to the configuration file, w.r.t. the current working directory
     */
    val config by parser.positional("path to the configuration file (or to the rulebook in IRT)")

    /**
     * Interactive rule tester mode
     */
    val irt by parser.flagging(
        "-t",
        "--test-rulebook",
        help = "Launch the Interactive Rule Tester. See the documentation for more details."
    )

    /**
     * If present, enables debug info
     */
    val verbose by parser.flagging("-v", "--verbose", help = "Enables DEBUG output for EpiLink loggers")
}

/**
 * Main entry-point for EpiLink, which expects CLI arguments in the arguments.
 */
fun main(args: Array<String>) = mainBody("epilink") {
    val cliArgs = ArgParser(
        args,
        helpFormatter = DefaultHelpFormatter(
            prologue = """
                EpiLink is a user authentication service with a website and a 
                Discord bot. This is the back-end of the website, the database
                management tool and the Discord bot of EpiLink. You can use the
                command line arguments to specify some specific requirements, 
                but you should use the config file for most configuration 
                options.
            """.trimIndent(),
            epilogue = """
                For more information, visit EpiLink's official documentation.
            """.trimIndent()
        )
    ).parseInto(::CliArgs)

    if (cliArgs.irt) {
        ruleTester(cliArgs.config)
        return@mainBody
    }

    if (cliArgs.verbose) {
        val ctx = LoggerFactory.getILoggerFactory() as LoggerContext
        ctx.getLogger("epilink").level = Level.DEBUG
        logger.debug("DEBUG output enabled. Remove flag -v to disable.")
    }
    runBlockingWithScope {
        logger.debug("Loading configuration")

        val cfgPath = Paths.get(cliArgs.config)
        val cfg = runCatching { loadConfigFromFile(cfgPath) }.getOrElse { exc ->
            logger.debug(exc) { "Encountered exception on config load" }
            when (exc) {
                is java.nio.file.NoSuchFileException -> logger.error("Failed to load config, could not find config file $cfgPath")
                is JsonParseException -> logger.error("Failed to parse YAML file, wrong syntax: ${exc.message}")
                is JsonMappingException -> logger.error("Failed to understand configuration file: ${exc.message}")
                else -> logger.error("Encountered an unexpected exception on config load", exc)
            }
            exitProcess(123)
        }

        if (cfg.rulebook != null && cfg.rulebookFile != null) {
            logger.error("Your configuration defines both a rulebook and a rulebookFile: please only use one of those.")
            logger.info("Use rulebook if you are putting the rulebook code directly in the config file, or rulebookFile if you are putting the code in a separate file.")
            exitProcess(4)
        }

        val idProviderMetadata = async {
            logger.info("Loading identity provider information...")
            val discoveryContent = download(cfg.idProvider.url + "/.well-known/openid-configuration")
            when (val m = identityProviderMetadataFromDiscovery(
                discoveryContent,
                if (cfg.idProvider.microsoftBackwardsCompatibility) "oid" else "sub"
            )) {
                is MetadataOrFailure.Metadata -> m.metadata
                is MetadataOrFailure.IncompatibleProvider -> error("The chosen provider is not compatible: ${m.reason}")
            }
        }

        val rulebook = async { loadRulebook(cfg, cfgPath, cfg.cacheRulebook) }

        val strings = async {
            logger.debug("Loading Discord strings")
            loadDiscordI18n()
        }

        logger.debug("Checking config...")
        checkConfig(cfg, rulebook.await(), cliArgs, strings.await().keys)

        val legal = async {
            logger.debug("Loading legal texts")
            cfg.legal.load(cfgPath)
        }

        val assets = async {
            logger.debug("Loading assets")
            loadAssets(cfg.server, cfg.idProvider, cfgPath.parent)
        }

        logger.debug("Creating environment")
        val env = LinkServerEnvironment(
            cfg = cfg,
            legal = legal.await(),
            assets = assets.await(),
            identityProviderMetadata = idProviderMetadata.await(),
            rulebook = rulebook.await(),
            discordStrings = strings.await(),
            defaultDiscordLanguage = cfg.discord.defaultLanguage
        )

        logger.info("Environment created, starting ${env.name}")
        env.start()
    }
}

private fun runBlockingWithScope(block: suspend CoroutineScope.() -> Unit) =
    runBlocking { withContext(Dispatchers.Default) { coroutineScope { block() } } }

private fun checkConfig(
    cfg: LinkConfiguration,
    rulebook: Rulebook,
    cliArgs: CliArgs,
    availableDiscordLanguages: Set<String>
) {
    val configReport = cfg.isConfigurationSane(cliArgs, rulebook, availableDiscordLanguages)
    var shouldExit = false
    configReport.forEach {
        when (it) {
            is ConfigError -> {
                logger.error(it.message)
                if (it.shouldFail) shouldExit = true
            }
            is ConfigWarning -> logger.warn(it.message)
            is ConfigInfo -> logger.info(it.message)
        }
    }

    if (shouldExit) {
        exitProcess(1)
    }
}

private fun download(url: String) = runBlocking { HttpClient(Apache).get<String>(url) }

private suspend fun loadRulebook(cfg: LinkConfiguration, cfgPath: Path, enableCache: Boolean): Rulebook {
    val rb = when {
        cfg.rulebook != null -> cfg.rulebook.let {
            logger.info("Loading rulebook from configuration file, this may take some time...")
            if (enableCache) {
                loadRulesWithCache(cfgPath, it, LoggerFactory.getLogger("epilink.rulebookLoader"))
            } else {
                loadRules(cfg.rulebook)
            }
        }
        cfg.rulebookFile != null -> cfg.rulebookFile.let { file ->
            withContext(Dispatchers.IO) { // toRealPath blocks, resolve is also blocking
                val path = cfgPath.parent.resolve(file)
                @Suppress("BlockingMethodInNonBlockingContext")
                logger.info("Loading rulebook from file $file (${path.toRealPath(LinkOption.NOFOLLOW_LINKS)}), this may take some time...")
                if (enableCache) {
                    loadRulesWithCache(path, LoggerFactory.getLogger("epilink.rulebookLoader"))
                } else {
                    loadRules(path.readScriptSource())
                }
            }
        }
        else -> null
    }
    if (rb != null) {
        if (!enableCache) {
            logger.info("Rulebook caching is disabled, making startup slower. Set 'cacheRulebook' to 'true' in your config file to enable it.")
        }
        logger.info("Rulebook loaded with ${rb.rules.size} rules.")
    }
    return rb ?: Rulebook(mapOf()) { true }
}

private fun loadDiscordI18n(): Map<String, Map<String, String>> =
    CliArgs::class.java.getResourceAsStream("/discord_i18n/languages").bufferedReader().use { reader ->
        reader.lines().asSequence().associateWith {
            val props = Properties()
            CliArgs::class.java.getResourceAsStream("/discord_i18n/strings_$it.properties").bufferedReader()
                .use { fileReader ->
                    props.load(fileReader)
                }
            val map = mutableMapOf<String, String>()
            props.forEach { (k, v) -> map[k.toString()] = v.toString() }
            map
        }
    }
