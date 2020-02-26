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
                EpiLink is a user authentication server with a website. This is the
                back-end of the website. You can use the command line arguments to 
                specify some specific requirements, but you should use the config
                file for most configuration options.
            """.trimIndent(),
            epilogue = """
                For more information, visit EpiLink's official documentation.
            """.trimIndent()
        )
    ).parseInto(::CliArgs)

    logger.debug("Loading configuration")
    val cfg = loadConfigFromFile(Paths.get(cliArgs.config))

    if (cfg.tokens.jwtSecret == "I am a secret ! Please change me :(") {
        if (cliArgs.allowUnsecureJwtSecret) {
            logger.warn("Default JWT secret found in configuration but allowed through -u flag.")
        } else {
            logger.error("Please change the default JWT secret in the configuration file.")
            logger.info("If you cannot change the secret (e.g. in a developer environment), run EpiLink with the -u flag.")
            exitProcess(1)
        }
    }

    if (cfg.sessionDuration < 0) {
        logger.error("Session duration can't be negative")
        exitProcess(2)
    }

    logger.debug("Creating environment")
    val env = LinkServerEnvironment(cfg)

    logger.info("Environment created, starting ${env.name}")
    env.start()
}
