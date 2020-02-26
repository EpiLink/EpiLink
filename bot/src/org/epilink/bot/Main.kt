package org.epilink.bot

import org.slf4j.LoggerFactory
import java.nio.file.Paths
import kotlin.system.exitProcess

internal val logger = LoggerFactory.getLogger("epilink")

fun main(args: Array<String>) {
    if(args.isEmpty()) {
        println("""
            Usage: epilink <CONFIG>
            
            (CONFIG is a path to an epilink config file)
        """.trimIndent())
        exitProcess(1)
    }

    logger.debug("Loading configuration")
    val cfg = loadConfigFromFile(Paths.get(args[0]))

    if (cfg.tokens.jwtSecret == "I am a secret ! Please change me :(") {
        logger.error("Please change the default JWT secret in the configuration file")
        return
    }

    if (cfg.sessionDuration < 0) {
        logger.error("Session duration can't be negative")
        return
    }

    logger.debug("Creating environment")
    val env = LinkServerEnvironment(cfg)

    logger.info("Environment created, starting ${env.name}")
    env.start()
}
