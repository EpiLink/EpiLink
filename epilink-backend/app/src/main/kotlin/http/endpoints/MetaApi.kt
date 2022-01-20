/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.http.endpoints

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
import org.epilink.backend.Assets
import org.epilink.backend.LegalText
import org.epilink.backend.LegalTexts
import org.epilink.backend.ResourceAsset
import org.epilink.backend.ServerEnvironment
import org.epilink.backend.asUrl
import org.epilink.backend.common.debug
import org.epilink.backend.config.IdentityProviderConfiguration
import org.epilink.backend.config.WebServerConfiguration
import org.epilink.backend.http.ApiEndpoint
import org.epilink.backend.http.ApiSuccessResponse
import org.epilink.backend.http.DiscordBackEnd
import org.epilink.backend.http.IdentityProvider
import org.epilink.backend.http.data.InstanceInformation
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
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

@OptIn(KoinApiExtension::class)
internal class MetaApiImpl : MetaApi, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.api.meta")
    private val env: ServerEnvironment by inject()
    private val legal: LegalTexts by inject()
    private val discordBackEnd: DiscordBackEnd by inject()
    private val idProvider: IdentityProvider by inject()
    private val assets: Assets by inject()
    private val wsCfg: WebServerConfiguration by inject()
    private val providerConfig: IdentityProviderConfiguration by inject()

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
            contacts = wsCfg.contacts,
            showFullAbout = wsCfg.showFullAbout
        )
}

private fun LegalText.asResponseContent(): OutgoingContent.ByteArrayContent =
    when (this) {
        is LegalText.Pdf -> ByteArrayContent(data, ContentType.Application.Pdf, OK)
        is LegalText.Html -> TextContent(text, ContentType.Text.Html, OK)
    }
