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
    logger.debug("Creating environment")
    val env = LinkServerEnvironment(
        cfg = loadConfigFromFile(Paths.get(args[0]))
    )
    logger.info("Environment created, starting ${env.name}")
    env.start()
}
