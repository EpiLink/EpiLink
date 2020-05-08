/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.contentType
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.epilink.bot.config.LinkContactInformation
import org.epilink.bot.config.LinkFooterUrl
import org.epilink.bot.config.LinkWebServerConfiguration
import org.epilink.bot.http.LinkBackEnd
import org.epilink.bot.http.LinkBackEndImpl
import org.epilink.bot.http.LinkDiscordBackEnd
import org.epilink.bot.http.LinkMicrosoftBackEnd
import org.epilink.bot.http.endpoints.LinkMetaApi
import org.epilink.bot.http.endpoints.LinkMetaApiImpl
import org.epilink.bot.http.endpoints.LinkRegistrationApi
import org.koin.dsl.module
import org.koin.test.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MetaTest : KoinBaseTest(
    module {
        single<LinkMetaApi> { LinkMetaApiImpl() }
        single<LinkBackEnd> { LinkBackEndImpl() }
        // TODO make this cleaner, this is here because the server calls registrationApi.install
        single<LinkRegistrationApi> { mockk { every { install(any()) } just runs } }
        single<CacheClient> { MemoryCacheClient() }
    }
) {
    @Test
    fun `Test meta information gathering`() {
        mockHere<LinkServerEnvironment> {
            every { name } returns "EpiLink Test Instance"
        }
        mockHere<LinkDiscordBackEnd> {
            every { getAuthorizeStub() } returns "I am a Discord authorize stub"
        }
        mockHere<LinkMicrosoftBackEnd> {
            every { getAuthorizeStub() } returns "I am a Microsoft authorize stub"
        }
        mockHere<LinkLegalTexts> {
            every { idPrompt } returns "My id prompt text is the best"
        }
        mockHere<LinkWebServerConfiguration> {
            every { logo } returns "https://amazinglogo"
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
            call.assertStatus(HttpStatusCode.OK)
            val data = fromJson<ApiSuccess>(call.response).data
            assertNotNull(data)
            assertEquals("EpiLink Test Instance", data["title"])
            assertEquals("https://amazinglogo", data.getValue("logo"))
            assertEquals("I am a Discord authorize stub", data.getString("authorizeStub_discord"))
            assertEquals("I am a Microsoft authorize stub", data.getString("authorizeStub_msft"))
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
        val tos = "<p>ABCDEFG</p>"
        mockHere<LinkLegalTexts> {
            every { tosText } returns tos
        }
        withTestEpiLink {
            val call = handleRequest(HttpMethod.Get, "/api/v1/meta/tos")
            call.assertStatus(HttpStatusCode.OK)
            assertEquals(ContentType.Text.Html, call.response.contentType())
            val data = call.response.content
            assertEquals(tos, data)
        }
    }

    @Test
    fun `Test PP retrieval`() {
        val pp = "<p>Privacy policyyyyyyyyyyyyyyyyyyyyyyyyyyyyy</p>"
        mockHere<LinkLegalTexts> {
            every { policyText } returns pp
        }
        withTestEpiLink {
            val call = handleRequest(HttpMethod.Get, "/api/v1/meta/privacy")
            call.assertStatus(HttpStatusCode.OK)
            assertEquals(ContentType.Text.Html, call.response.contentType())
            val data = call.response.content
            assertEquals(pp, data)
        }
    }

    private fun withTestEpiLink(block: TestApplicationEngine.() -> Unit) =
        withTestApplication({
            with(get<LinkBackEnd>()) {
                // TODO Only install features
                epilinkApiModule()
            }
        }, block)
}