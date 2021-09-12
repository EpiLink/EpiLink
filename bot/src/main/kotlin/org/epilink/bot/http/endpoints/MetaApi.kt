/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.http.endpoints

import guru.zoroark.shedinja.environment.InjectionScope
import guru.zoroark.shedinja.environment.invoke
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.response.respond
import io.ktor.response.respondBytes
import io.ktor.response.respondRedirect
import io.ktor.routing.Route
import io.ktor.routing.get
import org.epilink.bot.Assets
import org.epilink.bot.LegalText
import org.epilink.bot.LegalTexts
import org.epilink.bot.ResourceAsset
import org.epilink.bot.ServerEnvironment
import org.epilink.bot.asUrl
import org.epilink.bot.config.IdentityProviderConfiguration
import org.epilink.bot.config.WebServerConfiguration
import org.epilink.bot.debug
import org.epilink.bot.http.ApiEndpoint
import org.epilink.bot.http.ApiSuccessResponse
import org.epilink.bot.http.DiscordBackEnd
import org.epilink.bot.http.IdentityProvider
import org.epilink.bot.http.data.InstanceInformation
import org.slf4j.LoggerFactory

/**
 * Interface for the /meta API route
 */
interface MetaApi {
    /**
     * Installs the route here (the route for "/api/v1" is automatically added, so you must call this at the
     * root route)
     */
    fun install(route: Route)
}

internal class MetaApiImpl(scope: InjectionScope) : MetaApi {
    private val logger = LoggerFactory.getLogger("epilink.api.meta")
    private val env: ServerEnvironment by scope()
    private val legal: LegalTexts by scope()
    private val discordBackEnd: DiscordBackEnd by scope()
    private val idProvider: IdentityProvider by scope()
    private val assets: Assets by scope()
    private val wsCfg: WebServerConfiguration by scope()
    private val providerConfig: IdentityProviderConfiguration by scope()

    override fun install(route: Route) =
        with(route) { meta() }

    private fun Route.meta() {
        limitedRoute("/api/v1/meta", wsCfg.rateLimitingProfile.metaApi) {
            @ApiEndpoint("GET /api/v1/meta/info")
            get("info") {
                call.respond(ApiSuccessResponse.of(getInstanceInformation()))
            }

            @ApiEndpoint("GET /api/v1/meta/tos")
            get("tos") {
                call.respond(legal.termsOfServices.asResponseContent())
            }

            @ApiEndpoint("GET /api/v1/meta/privacy")
            get("privacy") {
                call.respond(legal.privacyPolicy.asResponseContent())
            }

            serveAsset("logo", assets.logo)
            serveAsset("background", assets.background)
            serveAsset("idpLogo", assets.idpLogo)
        }
    }

    private fun Route.serveAsset(name: String, asset: ResourceAsset) {
        when (asset) {
            ResourceAsset.None -> {
                /* nothing */
            }
            is ResourceAsset.Url -> get(name) {
                call.respondRedirect(asset.url)
            }.also { logger.debug { "Will serve asset $name as a URL redirect" } }
            is ResourceAsset.File -> get(name) {
                call.respondBytes(asset.contents, asset.contentType)
            }.also { logger.debug { "Will serve asset $name as a file" } }
        }
    }

    /**
     * Create an [InstanceInformation] object based on this back end's environment and configuration
     */
    private fun getInstanceInformation(): InstanceInformation =
        InstanceInformation(
            title = env.name,
            logo = assets.logo.asUrl("logo"),
            background = assets.background.asUrl("background"),
            authorizeStub_idProvider = idProvider.getAuthorizeStub(),
            authorizeStub_discord = discordBackEnd.getAuthorizeStub(),
            providerName = providerConfig.name,
            providerIcon = assets.idpLogo.asUrl("idpLogo"),
            idPrompt = legal.idPrompt,
            footerUrls = wsCfg.footers,
            contacts = wsCfg.contacts
        )
}

private fun LegalText.asResponseContent(): OutgoingContent.ByteArrayContent =
    when (this) {
        is LegalText.Pdf -> ByteArrayContent(data, ContentType.Application.Pdf, OK)
        is LegalText.Html -> TextContent(text, ContentType.Text.Html, OK)
    }
