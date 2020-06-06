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
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.sessions.Sessions
import io.mockk.*
import org.epilink.bot.http.LinkBackEnd
import org.epilink.bot.http.LinkBackEndImpl
import org.epilink.bot.http.endpoints.LinkAdminApi
import org.epilink.bot.http.endpoints.LinkMetaApi
import org.epilink.bot.http.endpoints.LinkRegistrationApi
import org.epilink.bot.http.endpoints.LinkUserApi
import org.koin.core.get
import org.koin.dsl.module
import org.koin.test.mock.declare
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    @Test
    fun `Test error handling`() {
        declare<LinkBackEnd> { LinkBackEndImpl() }
        withTestApplication({
            // Requires content negotiation
            with(get<LinkBackEnd>()) { installFeatures() }

            routing {
                with(get<LinkBackEnd>()) { installErrorHandling() }

                get("/one") {
                    error("You don't know me")
                }

                get("/two") {
                    throw LinkEndpointException(StandardErrorCodes.MissingAuthentication, "Oops", true)
                }

                get("/three") {
                    throw LinkEndpointException(StandardErrorCodes.InvalidAuthCode, "EEEE", false)
                }
            }
        }) {
            handleRequest(HttpMethod.Get, "/one").apply {
                assertStatus(HttpStatusCode.InternalServerError)
                val apiError = fromJson<ApiError>(response)
                assertTrue(apiError.message.contains("unknown", ignoreCase = true))
                val data = apiError.data
                assertEquals(StandardErrorCodes.UnknownError.code, data.code)
                assertEquals(StandardErrorCodes.UnknownError.description, data.description)
            }
            handleRequest(HttpMethod.Get, "/two").apply {
                assertStatus(HttpStatusCode.BadRequest)
                val apiError = fromJson<ApiError>(response)
                assertTrue(apiError.message.contains("Oops"))
                val data = apiError.data
                assertEquals(StandardErrorCodes.MissingAuthentication.code, data.code)
                assertEquals(StandardErrorCodes.MissingAuthentication.description, data.description)
            }
            handleRequest(HttpMethod.Get, "/three").apply {
                assertStatus(HttpStatusCode.InternalServerError)
                val apiError = fromJson<ApiError>(response)
                assertTrue(apiError.message.contains("EEEE"))
                val data = apiError.data
                assertEquals(StandardErrorCodes.InvalidAuthCode.code, data.code)
                assertEquals(StandardErrorCodes.InvalidAuthCode.description, data.description)
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