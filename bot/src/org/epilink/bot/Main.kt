package org.epilink.bot

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.DefaultHelpFormatter
import com.xenomachina.argparser.mainBody
import org.epilink.bot.config.*
import org.slf4j.LoggerFactory
import java.nio.file.Paths
import kotlin.system.exitProcess

internal val logger = LoggerFactory.getLogger("epilink")

class CliArgs(parser: ArgParser) {
    val config by parser.positional("path to the configuration file")

    val allowUnsecureJwtSecret by parser.flagging(
        "-u", "--unsecure-jwt-secret",
        help = "allows using the default JWT secret in the config file"
    )
}

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

    logger.debug("Loading configuration")

    val cfg = loadEpilinkConfig(cliArgs)

    logger.debug("Creating environment")
    val env = LinkServerEnvironment(cfg)

    logger.info("Environment created, starting ${env.name}")
    env.start()
}

/**
 * Loads the configuration and performs safety checks on it.
 */
private fun loadEpilinkConfig(cliArgs: CliArgs): LinkConfiguration {
    val cfg = loadConfigFromFile(
        Paths.get(cliArgs.config)
    )

    val configReport = cfg.isConfigurationSane(cliArgs)
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

    return cfg
}
