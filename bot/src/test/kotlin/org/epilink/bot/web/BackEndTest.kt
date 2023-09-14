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
import guru.zoroark.tegral.di.dsl.put
import guru.zoroark.tegral.di.environment.get
import guru.zoroark.tegral.di.test.TegralSubjectTest
import guru.zoroark.tegral.di.test.TestMutableInjectionEnvironment
import guru.zoroark.tegral.di.test.mockk.putMock
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.pluginOrNull
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verifyAll
import org.epilink.bot.CacheClient
import org.epilink.bot.InternalEndpointException
import org.epilink.bot.MemoryCacheClient
import org.epilink.bot.StandardErrorCodes
import org.epilink.bot.StandardErrorCodes.InvalidAuthCode
import org.epilink.bot.UserEndpointException
import org.epilink.bot.assertStatus
import org.epilink.bot.config.WebServerConfiguration
import org.epilink.bot.fromJson
import org.epilink.bot.http.BackEnd
import org.epilink.bot.http.BackEndImpl
import org.epilink.bot.http.endpoints.AdminEndpoints
import org.epilink.bot.http.endpoints.MetaApi
import org.epilink.bot.http.endpoints.RegistrationApi
import org.epilink.bot.http.endpoints.UserApi
import org.epilink.bot.putSpy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BackEndTest : TegralSubjectTest<Unit>(
    Unit::class,
    {
        put<CacheClient> { MemoryCacheClient() }
    }
) {
    @Test
    fun `Test feature installation`() = test {
        put<BackEnd>(::BackEndImpl)
        withTestApplication({
            with(get<BackEnd>()) { installFeatures() }
        }) {
            assertNotNull(application.pluginOrNull(ContentNegotiation))
            assertNotNull(application.pluginOrNull(Sessions))
            assertNotNull(application.pluginOrNull(RateLimit))
        }
    }

    private fun TestMutableInjectionEnvironment.withErrorHandlingTestApplication(
        routing: Routing.() -> Unit,
        test: TestApplicationEngine.() -> Unit
    ) =
        withTestApplication({
            // Requires content negotiation
            with(get<BackEnd>()) { installFeatures() }
            routing {
                with(get<BackEnd>()) { installErrorHandling() }
                routing()
            }
        }, test)

    @Test
    fun `Test error handling random error`() = test {
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
    fun `Test error handling user error`() = test {
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
    fun `Test error handling internal error`() = test {
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
    fun `Test module installation, admin enabled`() = test {
        testModuleInstallation(true)
    }

    @Test
    fun `Test module installation, admin disabled`() = test {
        testModuleInstallation(false)
    }

    private fun TestMutableInjectionEnvironment.testModuleInstallation(enableAdminEndpoints: Boolean) {
        val back = putSpy<BackEnd>({ spyk(BackEndImpl(scope)) }) {
            every { any<Application>().installFeatures() } just runs
            every { any<Route>().installErrorHandling() } just runs
        }

        val user = putMock<UserApi> { every { install(any()) } just runs }
        val meta = putMock<MetaApi> { every { install(any()) } just runs }
        val register = putMock<RegistrationApi> { every { install(any()) } just runs }
        val admin = if (enableAdminEndpoints) {
            putMock<AdminEndpoints> { every { install(any()) } just runs }
        } else {
            null
        }
        putMock<WebServerConfiguration> { every { this@putMock.enableAdminEndpoints } returns enableAdminEndpoints }
        withTestApplication({ with(get<BackEnd>()) { epilinkApiModule() } }) {}
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
