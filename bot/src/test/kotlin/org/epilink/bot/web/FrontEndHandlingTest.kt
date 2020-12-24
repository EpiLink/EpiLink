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
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.spyk
import org.epilink.bot.KoinBaseTest
import org.epilink.bot.assertStatus
import org.epilink.bot.config.LinkWebServerConfiguration
import org.epilink.bot.http.LinkFrontEndHandler
import org.epilink.bot.http.LinkFrontEndHandlerImpl
import org.epilink.bot.mockHere
import org.koin.dsl.module
import org.koin.test.get
import kotlin.test.Test
import kotlin.test.assertEquals

class FrontEndHandlingTest : KoinBaseTest<LinkFrontEndHandler>(
    LinkFrontEndHandler::class,
    module {
        single<LinkFrontEndHandler> { spyk(LinkFrontEndHandlerImpl()) }
    }
) {
    @Test
    fun `Test front 404 error`() {
        get<LinkFrontEndHandler>().apply {
            every { serveIntegratedFrontEnd } returns false
        }
        mockHere<LinkWebServerConfiguration> {
            every { frontendUrl } returns null
        }
        withTestFrontHandler {
            listOf("/index.html", "/", "/dir/indir.md").forEach { path ->
                handleRequest(HttpMethod.Get, path).apply {
                    assertStatus(HttpStatusCode.NotFound)
                }
            }
        }
    }

    @Test
    fun `Test front redirect`() {
        get<LinkFrontEndHandler>().apply {
            every { serveIntegratedFrontEnd } returns false
        }
        mockHere<LinkWebServerConfiguration> {
            every { frontendUrl } returns "https://frontend/"
        }
        withTestFrontHandler {
            listOf(
                "/index.html",
                "/",
                "/dir/indir.md",
                "/yay/test?query=aze&beta=uxi",
                "/?myquer=azerty",
                "/orthogonal?to=me"
            ).forEach { path ->
                handleRequest(HttpMethod.Get, path).apply {
                    assertStatus(HttpStatusCode.Found)
                    assertEquals("https://frontend$path", response.headers["Location"])
                }
            }
        }
    }

    @Test
    fun `Test serving bundled front`() {
        // The .hasFrontend is present in the test resources
        mockHere<LinkWebServerConfiguration> {
            every { frontendUrl } returns null
        }
        withTestFrontHandler {
            mapOf(
                "/index.html" to "Default index.html",
                "/" to "Default index.html",
                "/does/not/exist.html" to "Default index.html",
                "/one.txt" to "Text file one.txt",
                "/dir/indir.md" to "In directory indir.md"
            ).forEach { (k, v) ->
                handleRequest(HttpMethod.Get, k).apply {
                    assertStatus(HttpStatusCode.OK)
                    assertEquals(v, response.content)
                }
            }
        }
    }

    private fun withTestFrontHandler(block: TestApplicationEngine.() -> Unit) =
        withTestApplication({
            with(get<LinkFrontEndHandler>()) {
                install()
            }
        }, block)
}