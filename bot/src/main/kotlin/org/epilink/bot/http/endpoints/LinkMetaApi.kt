/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.http.endpoints

import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import org.epilink.bot.LinkLegalTexts
import org.epilink.bot.LinkServerEnvironment
import org.epilink.bot.config.LinkWebServerConfiguration
import org.epilink.bot.http.ApiEndpoint
import org.epilink.bot.http.ApiSuccessResponse
import org.epilink.bot.http.LinkDiscordBackEnd
import org.epilink.bot.http.LinkMicrosoftBackEnd
import org.epilink.bot.http.data.InstanceInformation
import org.epilink.bot.ratelimiting.rateLimited
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.Duration

interface LinkMetaApi {
    fun install(route: Route)
}

internal class LinkMetaApiImpl : LinkMetaApi, KoinComponent {
    /**
     * The environment the back end lives in
     */
    private val env: LinkServerEnvironment by inject()

    private val legal: LinkLegalTexts by inject()

    private val discordBackEnd: LinkDiscordBackEnd by inject()

    private val microsoftBackEnd: LinkMicrosoftBackEnd by inject()

    private val wsCfg: LinkWebServerConfiguration by inject()

    override fun install(route: Route) =
        with(route) { meta() }

    private fun Route.meta() {
        route("/api/v1/meta") {
            rateLimited(limit = 50, timeBeforeReset = Duration.ofMinutes(1)) {
                @ApiEndpoint("GET /api/v1/meta/info")
                get("info") {
                    call.respond(ApiSuccessResponse(data = getInstanceInformation()))
                }

                @ApiEndpoint("GET /api/v1/meta/tos")
                get("tos") {
                    call.respond(HttpStatusCode.OK, TextContent(legal.tosText, ContentType.Text.Html))
                }

                @ApiEndpoint("GET /api/v1/meta/privacy")
                get("privacy") {
                    call.respond(HttpStatusCode.OK, TextContent(legal.policyText, ContentType.Text.Html))
                }
            }
        }
    }

    /**
     * Create an [InstanceInformation] object based on this back end's environment and configuration
     */
    private fun getInstanceInformation(): InstanceInformation =
        InstanceInformation(
            title = env.name,
            logo = wsCfg.logo,
            authorizeStub_msft = microsoftBackEnd.getAuthorizeStub(),
            authorizeStub_discord = discordBackEnd.getAuthorizeStub(),
            idPrompt = legal.idPrompt,
            footerUrls = wsCfg.footers,
            contacts = wsCfg.contacts
        )
}