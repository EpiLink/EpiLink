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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.epilink.bot.config.*
import org.epilink.bot.rulebook.Rulebook
import org.epilink.bot.rulebook.loadRules
import org.epilink.bot.rulebook.loadRulesWithCache
import org.epilink.bot.rulebook.readScriptSource
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
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

    // TODO implement disabling caching
    val rulebook = runBlocking {
        cfg.rulebook?.let {
            logger.info("Loading rulebook from configuration, this may take some time...")
            // TODO cache this one too
            loadRules(it).also { rb -> logger.info("Rulebook loaded with ${rb.rules.size} rules.") }
        } ?: cfg.rulebookFile?.let { file ->
            withContext(Dispatchers.IO) { // toRealPath blocks, resolve is also blocking
                val path = cfgPath.parent.resolve(file)
                logger.info("Loading rulebook from file $file (${path.toRealPath(LinkOption.NOFOLLOW_LINKS)}), this may take some time...")
                loadRulesWithCache(path, LoggerFactory.getLogger("epilink.rulebookLoader")).also { rb ->
                    logger.info("Rulebook loaded with ${rb.rules.size} rules.")
                }
            }
        } ?: Rulebook(mapOf()) { true }
    }

    logger.debug("Checking config...")
    checkConfig(cfg, rulebook, cliArgs)

    logger.debug("Loading legal texts")
    val legal = cfg.legal.load(cfgPath)

    logger.debug("Creating environment")
    val env = LinkServerEnvironment(
        cfg = cfg,
        legal = legal,
        rulebook = rulebook
    )

    logger.info("Environment created, starting ${env.name}")
    env.start()
}

private fun checkConfig(cfg: LinkConfiguration, rulebook: Rulebook, cliArgs: CliArgs) {
    val configReport = cfg.isConfigurationSane(cliArgs, rulebook)
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
