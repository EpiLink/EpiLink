package org.epilink.bot.http.endpoints

import io.ktor.routing.Route
import io.ktor.routing.route
import org.koin.core.KoinComponent

interface LinkAdminApi {
    fun install(route: Route)
}

internal class LinkAdminApiImpl : LinkAdminApi, KoinComponent {
    override fun install(route: Route) {
        with(route) { admin() }
    }

    private fun Route.admin() = route("/api/v1/admin/") {

    }
}