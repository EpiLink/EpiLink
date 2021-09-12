/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.web

import guru.zoroark.ratelimit.RateLimit
import guru.zoroark.shedinja.dsl.put
import guru.zoroark.shedinja.environment.get
import guru.zoroark.shedinja.test.ShedinjaBaseTest
import guru.zoroark.shedinja.test.UnsafeMutableEnvironment
import io.ktor.application.Application
import io.ktor.application.featureOrNull
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.sessions.Sessions
import io.mockk.*
import org.epilink.bot.*
import org.epilink.bot.StandardErrorCodes.InvalidAuthCode
import org.epilink.bot.config.WebServerConfiguration
import org.epilink.bot.http.BackEnd
import org.epilink.bot.http.BackEndImpl
import org.epilink.bot.http.endpoints.AdminEndpoints
import org.epilink.bot.http.endpoints.MetaApi
import org.epilink.bot.http.endpoints.RegistrationApi
import org.epilink.bot.http.endpoints.UserApi
import kotlin.test.*

class BackEndTest : ShedinjaBaseTest<Unit>(
    Unit::class, {
        put<CacheClient>(::MemoryCacheClient)
    }
) {
    @Test
    fun `Test feature installation`() = stest {
        put<BackEnd>(::BackEndImpl)
        withTestApplication({
            with(get<BackEnd>()) { installFeatures() }
        }) {
            assertNotNull(application.featureOrNull(ContentNegotiation))
            assertNotNull(application.featureOrNull(Sessions))
            assertNotNull(application.featureOrNull(RateLimit))
        }
    }

    private fun UnsafeMutableEnvironment.withErrorHandlingTestApplication(routing: Routing.() -> Unit, test: TestApplicationEngine.() -> Unit) =
        withTestApplication({
            // Requires content negotiation
            with(get<BackEnd>()) { installFeatures() }
            routing {
                with(get<BackEnd>()) { installErrorHandling() }
                routing()
            }
        }, test)

    @Test
    fun `Test error handling random error`() = stest {
        put<BackEnd>(::BackEndImpl)
        withErrorHandlingTestApplication({
            get("/one") {
                error("You don't know me")
            }
        }) {
            handleRequest(HttpMethod.Get, "/one").apply {
                assertStatus(HttpStatusCode.InternalServerError)
                val apiError = fromJson<ApiError>(response).apply {
                    assertTrue(message.contains("unknown", ignoreCase = true))
                    assertEquals("err.999", message_i18n)
                    assertTrue(message_i18n_data.isEmpty())
                }
                apiError.data.apply {
                    assertEquals(StandardErrorCodes.UnknownError.code, code)
                    assertEquals(StandardErrorCodes.UnknownError.description, description)
                }
            }
        }
    }

    @Test
    fun `Test error handling user error`() = stest {
        put<BackEnd>(::BackEndImpl)
        withErrorHandlingTestApplication({
            get("/two") {
                throw UserEndpointException(
                    StandardErrorCodes.MissingAuthentication,
                    "Oops",
                    "oo.ps",
                    mapOf("oo" to "ps")
                )
            }
        }) {
            handleRequest(HttpMethod.Get, "/two").apply {
                assertStatus(HttpStatusCode.BadRequest)
                val apiError = fromJson<ApiError>(response).apply {
                    assertEquals("Oops", message)
                    assertEquals("oo.ps", message_i18n)
                    assertEquals(1, message_i18n_data.size)
                }
                apiError.message_i18n_data.entries.first().apply {
                    assertEquals("oo", key)
                    assertEquals("ps", value)
                }
                apiError.data.apply {
                    assertEquals(StandardErrorCodes.MissingAuthentication.code, code)
                    assertEquals(StandardErrorCodes.MissingAuthentication.description, description)
                }
            }
        }
    }

    @Test
    fun `Test error handling internal error`() = stest {
        put<BackEnd>(::BackEndImpl)
        withErrorHandlingTestApplication({
            get("/three") {
                throw InternalEndpointException(InvalidAuthCode, "EEEE")
            }
        }) {
            handleRequest(HttpMethod.Get, "/three").apply {
                assertStatus(HttpStatusCode.InternalServerError)
                val apiError = fromJson<ApiError>(response).apply {
                    assertFalse(message.contains("EEEE"))
                    assertEquals("err.102", message_i18n)
                    assertTrue(message_i18n_data.isEmpty())
                }
                apiError.data.apply {
                    assertEquals(InvalidAuthCode.code, code)
                    assertEquals(InvalidAuthCode.description, description)
                }
            }
        }
    }

    @Test
    fun `Test module installation, admin enabled`() = stest {
        testModuleInstallation(true)
    }

    @Test
    fun `Test module installation, admin disabled`() = stest {
        testModuleInstallation(false)
    }

    private fun UnsafeMutableEnvironment.testModuleInstallation(enableAdminEndpoints: Boolean) {
        put<BackEnd> {
            spyk(BackEndImpl(scope)) {
                every { any<Application>().installFeatures() } just runs
                every { any<Route>().installErrorHandling() } just runs
            }
        }
        val back = get<BackEnd>()
        val user = putMock<UserApi> { every { install(any()) } just runs }
        val meta = putMock<MetaApi> { every { install(any()) } just runs }
        val register = putMock<RegistrationApi> { every { install(any()) } just runs }
        val admin = if (enableAdminEndpoints) {
            putMock<AdminEndpoints> { every { install(any()) } just runs }
        } else null
        putMock<WebServerConfiguration> { every { this@putMock.enableAdminEndpoints } returns enableAdminEndpoints }
        withTestApplication({
            with(get<BackEnd>()) { epilinkApiModule() }
        }) { }
        verifyAll {
            with(back) {
                any<Application>().epilinkApiModule()
                any<Application>().installFeatures()
                any<Route>().installErrorHandling()
            }
            user.install(any())
            meta.install(any())
            register.install(any())
            if (enableAdminEndpoints) {
                admin!!.install(any())
            }
        }
    }
}
