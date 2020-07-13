/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import guru.zoroark.ratelimit.RateLimit
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
import org.epilink.bot.StandardErrorCodes.InvalidAuthCode
import org.epilink.bot.http.LinkBackEnd
import org.epilink.bot.http.LinkBackEndImpl
import org.epilink.bot.http.endpoints.LinkAdminApi
import org.epilink.bot.http.endpoints.LinkMetaApi
import org.epilink.bot.http.endpoints.LinkRegistrationApi
import org.epilink.bot.http.endpoints.LinkUserApi
import org.koin.core.get
import org.koin.dsl.module
import org.koin.test.mock.declare
import kotlin.test.*

class BackEndTest : KoinBaseTest(
    module {
        single<CacheClient> { MemoryCacheClient() }
    }
) {
    @Test
    fun `Test feature installation`() {
        declare<LinkBackEnd> { LinkBackEndImpl() }
        withTestApplication({
            with(get<LinkBackEnd>()) { installFeatures() }
        }) {
            assertNotNull(application.featureOrNull(ContentNegotiation))
            assertNotNull(application.featureOrNull(Sessions))
            assertNotNull(application.featureOrNull(RateLimit))
        }
    }

    private fun withErrorHandlingTestApplication(routing: Routing.() -> Unit, test: TestApplicationEngine.() -> Unit) =
        withTestApplication({
            // Requires content negotiation
            with(get<LinkBackEnd>()) { installFeatures() }
            routing {
                with(get<LinkBackEnd>()) { installErrorHandling() }
                routing()
            }
        }, test)

    @Test
    fun `Test error handling random error`() {
        declare<LinkBackEnd> { LinkBackEndImpl() }
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
    fun `Test error handling user error`() {
        declare<LinkBackEnd> { LinkBackEndImpl() }
        withErrorHandlingTestApplication({
            get("/two") {
                throw LinkEndpointUserException(
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
    fun `Test error handling internal error`() {
        declare<LinkBackEnd> { LinkBackEndImpl() }
        withErrorHandlingTestApplication({
            get("/three") {
                throw LinkEndpointInternalException(InvalidAuthCode, "EEEE")
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
    fun `Test module installation`() {
        val back = declare<LinkBackEnd> {
            spyk(LinkBackEndImpl()) {
                every { any<Application>().installFeatures() } just runs
                every { any<Route>().installErrorHandling() } just runs
            }
        }
        val user = mockHere<LinkUserApi> { every { install(any()) } just runs }
        val meta = mockHere<LinkMetaApi> { every { install(any()) } just runs }
        val register = mockHere<LinkRegistrationApi> { every { install(any()) } just runs }
        val admin = mockHere<LinkAdminApi> { every { install(any()) } just runs }
        withTestApplication({
            with(get<LinkBackEnd>()) { epilinkApiModule() }
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
            admin.install(any())
        }
    }
}