package org.epilink.bot

import io.ktor.application.call
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/**
 * This class is responsible for holding configuration information and
 * references to things like the Discord client, the Database environment, etc.
 */
class LinkServerEnvironment(
    private val cfg: LinkConfiguration
) {
    private var server: ApplicationEngine? = null
    val name: String
        get() = cfg.name

    fun start() {
        val serv = embeddedServer(Netty, cfg.serverPort) {
            routing {
                get("/") {
                    call.respondText("Hello World!")
                }
            }
        }
        server = serv
        serv.start(wait = true)
    }
}