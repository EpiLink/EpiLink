package org.epilink.bot

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.mockk.every
import io.mockk.spyk
import org.epilink.bot.config.LinkWebServerConfiguration
import org.epilink.bot.http.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.test.*

class FrontEndHandlingTest : KoinTest {
    @BeforeTest
    fun setupKoin() {
        startKoin {
            modules(module {
                single<LinkFrontEndHandler> { spyk(LinkFrontEndHandlerImpl()) }
            })
        }
    }

    @AfterTest
    fun tearDownKoin() {
        stopKoin()
    }

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
                    assertStatus(HttpStatusCode.MovedPermanently)
                    assertEquals("https://frontend$path", response.headers["Location"])
                }
            }
        }
    }

    @Test
    fun `Test serving bundled front`() {
        get<LinkFrontEndHandler>().apply {
            every { serveIntegratedFrontEnd } returns true
        }
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