/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.web

import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.contentType
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.mockk.every
import io.mockk.mockk
import org.epilink.bot.*
import org.epilink.bot.config.*
import org.epilink.bot.http.BackEnd
import org.epilink.bot.http.BackEndImpl
import org.epilink.bot.http.DiscordBackEnd
import org.epilink.bot.http.IdentityProvider
import org.epilink.bot.http.endpoints.MetaApi
import org.epilink.bot.http.endpoints.MetaApiImpl
import org.koin.dsl.module
import org.koin.test.get
import org.koin.test.mock.declare
import kotlin.test.*

class MetaTest : KoinBaseTest<Unit>(
    Unit::class,
    module {
        single<MetaApi> { MetaApiImpl() }
        single<BackEnd> { BackEndImpl() }
        single<CacheClient> { MemoryCacheClient() }
        single<WebServerConfiguration> {
            mockk { every { rateLimitingProfile } returns RateLimitingProfile.Standard }
        }
    }
) {
    @Test
    fun `Test meta information gathering`() {
        defaultAssets()
        mockHere<ServerEnvironment> {
            every { name } returns "EpiLink Test Instance"
        }
        mockHere<DiscordBackEnd> {
            every { getAuthorizeStub() } returns "I am a Discord authorize stub"
        }
        mockHere<IdentityProvider> {
            every { getAuthorizeStub() } returns "I am a Microsoft authorize stub"
        }
        mockHere<LegalTexts> {
            every { idPrompt } returns "My id prompt text is the best"
        }
        mockHere<IdentityProviderConfiguration> { every { name } returns "the_name" }
        softMockHere<WebServerConfiguration> {
            every { footers } returns listOf(
                FooterUrl("Hello", "https://hello"),
                FooterUrl("Heeeey", "/macarena")
            )
            every { contacts } returns listOf(
                ContactInformation("Number One", "numberone@my-email.com"),
                ContactInformation("The Two", "othernumber@eeeee.es")
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
            assertEquals("I am a Discord authorize stub", data.getString("authorizeStub_discord"))
            assertEquals("I am a Microsoft authorize stub", data.getString("authorizeStub_idProvider"))
            assertEquals("My id prompt text is the best", data.getString("idPrompt"))
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
        mockHere<LegalTexts> {
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
        mockHere<LegalTexts> {
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
        mockHere<LegalTexts> {
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
        mockHere<LegalTexts> {
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
            Assets(
                ResourceAsset.File(byteArrayOf(1, 2, 3), ContentType.Image.JPEG),
                ResourceAsset.File(byteArrayOf(4, 5, 6), ContentType.Image.PNG),
                ResourceAsset.File(byteArrayOf(7, 8, 9), ContentType.Image.GIF)
            )
        }
        withTestEpiLink {
            for (
                (assetUrl, asset) in listOf(
                    "logo" to assets.logo as ResourceAsset.File,
                    "background" to assets.background as ResourceAsset.File,
                    "idpLogo" to assets.idpLogo as ResourceAsset.File
                )
            ) {
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
        declare { Assets(ResourceAsset.None, ResourceAsset.None, ResourceAsset.None) }
        withTestEpiLink {
            for (urlFragment in assetEndpoints) {
                val call = handleRequest(HttpMethod.Get, "/api/v1/meta/$urlFragment")
                assertFalse(call.requestHandled)
            }
        }
    }

    private fun defaultAssets() = declare {
        Assets(
            ResourceAsset.Url("LogoURL"),
            ResourceAsset.Url("BackgroundURL"),
            ResourceAsset.Url("IDPLogoURL")
        )
    }

    private fun withTestEpiLink(block: TestApplicationEngine.() -> Unit) =
        withTestApplication({
            with(get<BackEnd>()) { installFeatures() }
            routing { get<MetaApi>().install(this) }
        }, block)
}
