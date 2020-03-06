package org.epilink.bot

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.DefaultHelpFormatter
import com.xenomachina.argparser.HelpFormatter
import com.xenomachina.argparser.mainBody
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
    val cfg = loadConfigFromFile(Paths.get(cliArgs.config))

    if(!cfg.isConfigurationSane(cliArgs)) {
        exitProcess(1)
    }

    logger.debug("Creating environment")
    val env = LinkServerEnvironment(cfg)

    logger.info("Environment created, starting ${env.name}")
    env.start()
}

/**
 * Checks the sanity of configuration options, logging information and guidance
 * for resolving issues.
 */
private fun LinkConfiguration.isConfigurationSane(args: CliArgs): Boolean {
    var hasErrors: Boolean = false
    if (tokens.jwtSecret == "I am a secret ! Please change me :(") {
        if (args.allowUnsecureJwtSecret) {
            logger.warn("Default JWT secret found in configuration but allowed through -u flag.")
            logger.warn("DO NOT USE -u IF YOU ARE IN A PRODUCTION ENVIRONMENT!")
        } else {
            logger.error("Please change the default JWT secret in the configuration file.")
            logger.info("If you cannot change the secret (e.g. in a developer environment), run EpiLink with the -u flag.")
            hasErrors = true
        }
    }

    if (server.sessionDuration < 0) {
        logger.error("Session duration can't be negative")
        hasErrors = true
    }

    return !hasErrors
}
