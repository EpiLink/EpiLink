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
import org.epilink.bot.assertStatus
import org.epilink.bot.config.WebServerConfiguration
import org.epilink.bot.http.FrontEndHandler
import org.epilink.bot.http.FrontEndHandlerImpl
import org.epilink.bot.mockHere
import org.koin.dsl.module
import org.koin.test.get
import kotlin.test.Test
import kotlin.test.assertEquals

class FrontEndHandlingTest : EpiLinkBaseTest<FrontEndHandler>(
    FrontEndHandler::class,
    module {
        single<FrontEndHandler> { spyk(FrontEndHandlerImpl()) }
    }
) {
    @Test
    fun `Test front 404 error`() {
        get<FrontEndHandler>().apply {
            every { serveIntegratedFrontEnd } returns false
        }
        mockHere<WebServerConfiguration> {
            every { frontendUrl } returns null
            every { corsWhitelist } returns listOf()
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
        get<FrontEndHandler>().apply {
            every { serveIntegratedFrontEnd } returns false
        }
        mockHere<WebServerConfiguration> {
            every { frontendUrl } returns "https://frontend/"
            every { corsWhitelist } returns listOf()
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
        mockHere<WebServerConfiguration> {
            every { frontendUrl } returns null
            every { corsWhitelist } returns listOf()
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

    @Test
    fun `Test CORS - Whitelist - Allow All`() {
        mockHere<WebServerConfiguration> {
            every { frontendUrl } returns null
            every { corsWhitelist } returns listOf("*")
        }
        handleCorsRequest("http://somewhere.else", "*")
    }

    @Test
    fun `Test CORS - Whitelist - No front-end`() {
        mockHere<WebServerConfiguration> {
            every { frontendUrl } returns null
            every { corsWhitelist } returns listOf("http://somewhere.else", "http://amazing.website")
        }
        handleCorsRequest("http://somewhere.else")
    }

    @Test
    fun `Test CORS - Whitelist - With front-end`() {
        mockHere<WebServerConfiguration> {
            every { frontendUrl } returns "http://front.end/"
            every { corsWhitelist } returns listOf("http://somewhere.else", "http://amazing.website")
        }
        handleCorsRequest("http://front.end")
    }

    @Test
    fun `Test CORS - Whitelist - Invalid`() {
        mockHere<WebServerConfiguration> {
            every { frontendUrl } returns "http://front.end/"
            every { corsWhitelist } returns listOf("http://somewhere.else", "http://amazing.website")
        }
        handleCorsRequest("http://oh.no", null)
    }

    @Test
    fun `Test CORS - Enabled - Front-end`() {
        mockHere<WebServerConfiguration> {
            every { frontendUrl } returns "http://front.end/"
            every { corsWhitelist } returns listOf()
        }
        handleCorsRequest("http://front.end")
    }

    @Test
    fun `Test CORS - Enabled - Front-end Invalid`() {
        mockHere<WebServerConfiguration> {
            every { frontendUrl } returns "http://front.end/"
            every { corsWhitelist } returns listOf()
        }
        handleCorsRequest("http://oh.no", null)
    }

    private fun handleCorsRequest(origin: String, expectedACAO: String? = origin) {
        withTestFrontHandler {
            handleRequest(HttpMethod.Options, "/") {
                addHeader("Origin", origin)
                addHeader("Access-Control-Request-Method", "POST")
                addHeader("Access-Control-Request-Headers", "Content-Type, RegistrationSessionId, SessionId")
            }.apply {
                print(response.headers.allValues())
                if (expectedACAO != null) {
                    assertEquals(
                        expectedACAO,
                        response.headers["Access-Control-Allow-Origin"],
                        "Wrong or absent Access-Control-Allow-Origin returned header value"
                    )
                    assertEquals("DELETE, OPTIONS", response.headers["Access-Control-Allow-Methods"])
                    assertEquals(
                        "Content-Type, RegistrationSessionId, SessionId",
                        response.headers["Access-Control-Allow-Headers"]
                    )
                } else {
                    assertStatus(HttpStatusCode.Forbidden)
                }
            }
        }
    }

    private fun withTestFrontHandler(block: TestApplicationEngine.() -> Unit) =
        withTestApplication({
            with(get<FrontEndHandler>()) {
                install()
            }
        }, block)
}
