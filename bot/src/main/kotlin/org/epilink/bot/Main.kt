package org.epilink.bot

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.DefaultHelpFormatter
import com.xenomachina.argparser.mainBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.epilink.bot.config.*
import org.epilink.bot.config.rulebook.Rulebook
import org.epilink.bot.config.rulebook.loadRules
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import kotlin.system.exitProcess

internal val logger = LoggerFactory.getLogger("epilink")

/**
 * CLI Arguments class for EpiLink
 */
class CliArgs(parser: ArgParser) {
    /**
     * Path to the configuration file, w.r.t. the current working directory
     */
    val config by parser.positional("path to the configuration file")

    /**
     * Unused and deprecated
     */
    val allowUnsecureJwtSecret by parser.flagging(
        "-u", "--unsecure-jwt-secret",
        help = "(deprecated) has no effect"
    )
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

    if(cliArgs.allowUnsecureJwtSecret) {
        logger.warn("Using -u / --unsecure-jwt-secret is deprecated. This flag will be removed soon.")
    }

    logger.debug("Loading configuration")

    val cfgPath = Paths.get(cliArgs.config)
    val cfg = loadConfigFromFile(cfgPath)

    if (cfg.discord.rulebook != null && cfg.discord.rulebookFile != null) {
        logger.error("Your configuration defines both a rulebook and a rulebookFile: please only use one of those.")
        logger.info("Use rulebook if you are putting the rulebook code directly in the config file, or rulebookFile if you are putting the code in a separate file.")
        exitProcess(4)
    }

    val rulebook = runBlocking {
        cfg.discord.rulebook?.let {
            logger.info("Loading rulebook from configuration, this may take some time...")
            loadRules(it).also { rb -> logger.info("Rulebook loaded with ${rb.rules.size} rules.")}
        } ?: cfg.discord.rulebookFile?.let { file ->
            withContext(Dispatchers.IO) { // toRealPath blocks, resolve is also blocking
                val path = cfgPath.parent.resolve(file)
                logger.info("Loading rulebook from file $file (${path.toRealPath(LinkOption.NOFOLLOW_LINKS)}), this may take some time...")
                val s = Files.readString(path, StandardCharsets.UTF_8)
                loadRules(s).also { rb -> logger.info("Rulebook loaded with ${rb.rules.size} rules.")}
            }
        } ?: Rulebook(mapOf())
    }

    logger.debug("Checking config...")
    checkConfig(cfg, rulebook, cliArgs)

    logger.debug("Loading legal texts")
    val legal = cfg.legal.load(cfgPath)

    logger.debug("Creating environment")
    val env = LinkServerEnvironment(cfg, legal, rulebook)

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
