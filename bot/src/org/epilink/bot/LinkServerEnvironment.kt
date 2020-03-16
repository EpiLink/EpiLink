package org.epilink.bot

import org.epilink.bot.config.LinkConfiguration
import org.epilink.bot.db.LinkServerDatabase
import org.epilink.bot.http.LinkHttpServer

/**
 * This class is responsible for holding configuration information and
 * references to things like the Discord client, the Database environment, the
 * server, etc.
 */
class LinkServerEnvironment(
    private val cfg: LinkConfiguration
) {
    var database: LinkServerDatabase =
        LinkServerDatabase(cfg)
        private set
    private var server: LinkHttpServer =
        LinkHttpServer(this, cfg.server, cfg.tokens.jwtSecret)

    val name: String
        get() = cfg.name

    fun start() {
        server.startServer(wait = true)
    }
}