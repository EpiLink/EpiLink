package org.epilink.bot

/**
 * This class is responsible for holding configuration information and
 * references to things like the Discord client, the Database environment, the
 * server, etc.
 */
class LinkServerEnvironment(
    private val cfg: LinkConfiguration
) {
    var database: LinkServerDatabase = LinkServerDatabase(cfg)
        private set
    private var api: LinkServerApi =
        LinkServerApi(this, cfg.server, cfg.tokens.jwtSecret)

    val name: String
        get() = cfg.name

    fun start() {
        api.startServer(wait = true)
    }
}