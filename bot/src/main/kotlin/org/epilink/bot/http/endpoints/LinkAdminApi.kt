/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
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