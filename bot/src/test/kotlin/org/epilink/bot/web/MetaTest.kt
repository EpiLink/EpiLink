/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.web

import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import org.epilink.bot.*
import org.epilink.bot.config.*
import org.epilink.bot.http.LinkBackEnd
import org.epilink.bot.http.LinkBackEndImpl
import org.epilink.bot.http.LinkDiscordBackEnd
import org.epilink.bot.http.LinkIdentityProvider
import org.epilink.bot.http.endpoints.LinkMetaApi
import org.epilink.bot.http.endpoints.LinkMetaApiImpl
import org.koin.dsl.module
import org.koin.test.get
import org.koin.test.mock.declare
import kotlin.test.*

class MetaTest : KoinBaseTest<Unit>(
    Unit::class,
    module {
        single<LinkMetaApi> { LinkMetaApiImpl() }
        single<LinkBackEnd> { LinkBackEndImpl() }
        single<CacheClient> { MemoryCacheClient() }
        single<LinkWebServerConfiguration> {
            mockk { every { rateLimitingProfile } returns RateLimitingProfile.Standard }
        }
    }
) {
    @Test
    fun `Test meta information gathering`() {
        defaultAssets()
        mockHere<LinkServerEnvironment> {
            every { name } returns "EpiLink Test Instance"
        }
        mockHere<LinkDiscordBackEnd> {
            every { getAuthorizeStub() } returns "I am a Discord authorize stub"
        }
        mockHere<LinkIdentityProvider> {
            every { getAuthorizeStub() } returns "I am a Microsoft authorize stub"
        }
        mockHere<LinkLegalTexts> {
            every { idPrompt } returns "My id prompt text is the best"
        }
        mockHere<LinkIdProviderConfiguration> { every { name } returns "the_name" }
        softMockHere<LinkWebServerConfiguration> {
            every { footers } returns listOf(
                LinkFooterUrl("Hello", "https://hello"),
                LinkFooterUrl("Heeeey", "/macarena")
            )
            every { contacts } returns listOf(
                LinkContactInformation("Number One", "numberone@my-email.com"),
                LinkContactInformation("The Two", "othernumber@eeeee.es")
            )
        }
        withTestEpiLink {
            val call = handleRequest(HttpMethod.Get, "/api/v1/meta/info")
            call.assertStatus(OK)
            val data = fromJson<ApiSuccess>(call.response).data
            assertNotNull(data)
            assertEquals("EpiLink Test Instance", data["title"])
            assertEquals("LogoURL", data.getValue("logo"))
            assertEquals("BackgroundURL", data.getValue("background"))
            assertEquals("IDPLogoURL", data.getValue("providerIcon"))
            assertEquals("I am a Discord authorize stub", data.getValue("authorizeStub_discord"))
            assertEquals("I am a Microsoft authorize stub", data.getValue("authorizeStub_idProvider"))
            assertEquals("My id prompt text is the best", data.getValue("idPrompt"))
            val footers = data.getListOfMaps("footerUrls")
            assertEquals(2, footers.size)
            assertTrue(footers.any { it["name"] == "Hello" && it["url"] == "https://hello" })
            assertTrue(footers.any { it["name"] == "Heeeey" && it["url"] == "/macarena" })
            val contacts = data.getListOfMaps("contacts")
            assertEquals(2, contacts.size)
            assertTrue(contacts.any { it["name"] == "Number One" && it["email"] == "numberone@my-email.com" })
            assertTrue(contacts.any { it["name"] == "The Two" && it["email"] == "othernumber@eeeee.es" })
        }
    }

    @Test
    fun `Test ToS retrieval`() {
        defaultAssets()
        val tos = "<p>ABCDEFG</p>"
        mockHere<LinkLegalTexts> {
            every { termsOfServices } returns LegalText.Html(tos)
        }
        withTestEpiLink {
            val call = handleRequest(HttpMethod.Get, "/api/v1/meta/tos")
            call.assertStatus(OK)
            assertEquals(ContentType.Text.Html, call.response.contentType())
            val data = call.response.content
            assertEquals(tos, data)
        }
    }

    @Test
    fun `Test ToS PDF retrieval`() {
        defaultAssets()
        val tos = byteArrayOf(1, 2, 3)
        mockHere<LinkLegalTexts> {
            every { termsOfServices } returns LegalText.Pdf(tos)
        }
        withTestEpiLink {
            val call = handleRequest(HttpMethod.Get, "/api/v1/meta/tos")
            call.assertStatus(OK)
            assertEquals(ContentType.Application.Pdf, call.response.contentType())
            val data = call.response.byteContent
            assertNotNull(data)
            assertTrue(tos.contentEquals(data))
        }
    }

    @Test
    fun `Test PP retrieval`() {
        defaultAssets()
        val pp = "<p>Privacy policyyyyyyyyyyyyyyyyyyyyyyyyyyyyy</p>"
        mockHere<LinkLegalTexts> {
            every { privacyPolicy } returns LegalText.Html(pp)
        }
        withTestEpiLink {
            val call = handleRequest(HttpMethod.Get, "/api/v1/meta/privacy")
            call.assertStatus(OK)
            assertEquals(ContentType.Text.Html, call.response.contentType())
            val data = call.response.content
            assertEquals(pp, data)
        }
    }

    @Test
    fun `Test PP PDF retrieval`() {
        defaultAssets()
        val pp = byteArrayOf(1, 2, 3)
        mockHere<LinkLegalTexts> {
            every { privacyPolicy } returns LegalText.Pdf(pp)
        }
        withTestEpiLink {
            val call = handleRequest(HttpMethod.Get, "/api/v1/meta/privacy")
            call.assertStatus(OK)
            assertEquals(ContentType.Application.Pdf, call.response.contentType())
            val data = call.response.byteContent
            assertNotNull(data)
            assertTrue(pp.contentEquals(data))
        }
    }

    private val assetEndpoints = listOf("logo", "background", "idpLogo")
    private val assetUrls = mapOf("logo" to "LogoURL", "background" to "BackgroundURL", "idpLogo" to "IDPLogoURL")

    @Test
    fun `Test asset retrieval (url redirect)`() {
        defaultAssets()
        withTestEpiLink {
            for ((assetUrlFragment, expected) in assetUrls) {
                val call = handleRequest(HttpMethod.Get, "/api/v1/meta/$assetUrlFragment")
                call.assertStatus(HttpStatusCode.Found)
                assertEquals(expected, call.response.headers["Location"])
            }
        }
    }

    @Test
    fun `Test asset retrieval (direct)`() {
        val assets = declare {
            LinkAssets(
                ResourceAsset.File(byteArrayOf(1, 2, 3), ContentType.Image.JPEG),
                ResourceAsset.File(byteArrayOf(4, 5, 6), ContentType.Image.PNG),
                ResourceAsset.File(byteArrayOf(7, 8, 9), ContentType.Image.GIF)
            )
        }
        withTestEpiLink {
            for ((assetUrl, asset) in listOf(
                "logo" to assets.logo as ResourceAsset.File,
                "background" to assets.background as ResourceAsset.File,
                "idpLogo" to assets.idpLogo as ResourceAsset.File
            )) {
                val call = handleRequest(HttpMethod.Get, "/api/v1/meta/$assetUrl")
                call.assertStatus(OK)
                assertEquals(asset.contentType, call.response.contentType())
                // toList() otherwise check fails (equals() isn't implemented in JVM for byte arrays)
                assertEquals(asset.contents.toList(), call.response.byteContent?.toList())
            }
        }
    }

    @Test
    fun `Test asset retrieval (no asset)`() {
        declare { LinkAssets(ResourceAsset.None, ResourceAsset.None, ResourceAsset.None) }
        withTestEpiLink {
            for (urlFragment in assetEndpoints) {
                val call = handleRequest(HttpMethod.Get, "/api/v1/meta/$urlFragment")
                assertFalse(call.requestHandled)
            }
        }
    }

    private fun defaultAssets() = declare {
        LinkAssets(
            ResourceAsset.Url("LogoURL"),
            ResourceAsset.Url("BackgroundURL"),
            ResourceAsset.Url("IDPLogoURL")
        )
    }

    private fun withTestEpiLink(block: TestApplicationEngine.() -> Unit) =
        withTestApplication({
            with(get<LinkBackEnd>()) { installFeatures() }
            routing { get<LinkMetaApi>().install(this) }
        }, block)
}