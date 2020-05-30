/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.epilink.bot.db.LinkIdAccessor
import org.epilink.bot.db.LinkServerDatabase
import org.epilink.bot.db.LinkUser
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.http.LinkBackEnd
import org.epilink.bot.http.LinkBackEndImpl
import org.epilink.bot.http.LinkSessionChecks
import org.epilink.bot.http.endpoints.LinkAdminApi
import org.epilink.bot.http.endpoints.LinkAdminApiImpl
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.get
import org.koin.test.mock.declare
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AdminTest : KoinBaseTest(
    module {
        single<LinkAdminApi> { LinkAdminApiImpl() }
        single<LinkBackEnd> { LinkBackEndImpl() }
        single<LinkSessionChecks> {
            mockk {
                coEvery { verifyUser(any()) } returns true
                coEvery { verifyAdmin(any()) } returns true
            }
        }
    }
) {
    override fun additionalModule() = module {
        single<CacheClient> { DummyCacheClient { sessionStorage } }
    }

    private val sessionStorage = UnsafeTestSessionStorage()
    private val sessionChecks: LinkSessionChecks
        get() = get() // I'm not sure if inject() handles test execution properly

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test manual identity request on identifiable`() {
        declare(named("admins")) { listOf("adminid") }
        val u = mockk<LinkUser>()
        mockHere<LinkServerDatabase> {
            coEvery { getUser("userid") } returns u
            coEvery { isUserIdentifiable("userid") } returns true
        }
        val lia = mockHere<LinkIdAccessor> {
            coEvery {
                accessIdentity("userid", false, "admin.name@email", "thisismyreason")
            } returns "trueidentity@othermail"
            coEvery {
                accessIdentity("adminid", true, any(), match { it.contains("another user") })
            } returns "admin.name@email"
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid", trueIdentity = "admin.name@email")
            handleRequest(HttpMethod.Post, "/api/v1/admin/idrequest") {
                addHeader("SessionId", sid)
                setJsonBody("""{"target":"userid","reason":"thisismyreason"}""")
            }.apply {
                assertStatus(HttpStatusCode.OK)
                val id = fromJson<ApiSuccess>(response)
                val data = id.data
                assertNotNull(data, "Response has data")
                assertEquals("userid", id.data["target"], "Correct userid")
                assertEquals("trueidentity@othermail", id.data["identity"], "Correct retrieved true identity")
            }
            coVerify {
                sessionChecks.verifyUser(any())
                sessionChecks.verifyAdmin(any())
                lia.accessIdentity("userid", false, "admin.name@email", "thisismyreason")
                lia.accessIdentity("adminid", true, any(), match { it.contains("another user") })
            }
        }
    }

    @Test
    fun `Test manual identity request on unknown target`() {
        declare(named("admins")) { listOf("adminid") }
        mockHere<LinkServerDatabase> {
            coEvery { getUser("userid") } returns null
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid", trueIdentity = "admin.name@email")
            handleRequest(HttpMethod.Post, "/api/v1/admin/idrequest") {
                addHeader("SessionId", sid)
                setJsonBody("""{"target":"userid","reason":"thisismyreason"}""")
            }.apply {
                assertStatus(HttpStatusCode.BadRequest)
                val err = fromJson<ApiError>(response)
                assertEquals(400, err.data.code)
            }
            coVerify {
                sessionChecks.verifyUser(any())
                sessionChecks.verifyAdmin(any())
            }
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test manual identity request on unidentifiable target`() {
        declare(named("admins")) { listOf("adminid") }
        val u = mockk<LinkUser>()
        mockHere<LinkServerDatabase> {
            coEvery { getUser("userid") } returns u
            coEvery { isUserIdentifiable("userid") } returns false
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid", trueIdentity = "admin.name@email")
            handleRequest(HttpMethod.Post, "/api/v1/admin/idrequest") {
                addHeader("SessionId", sid)
                setJsonBody("""{"target":"userid","reason":"thisismyreason"}""")
            }.apply {
                assertStatus(HttpStatusCode.BadRequest)
                val err = fromJson<ApiError>(response)
                assertEquals(430, err.data.code)
            }
            coVerify {
                sessionChecks.verifyUser(any())
                sessionChecks.verifyAdmin(any())
            }
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test manual identity request missing reason `() {
        declare(named("admins")) { listOf("adminid") }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid", trueIdentity = "admin.name@email")
            handleRequest(HttpMethod.Post, "/api/v1/admin/idrequest") {
                addHeader("SessionId", sid)
                setJsonBody("""{"target":"userid","reason":""}""")
            }.apply {
                assertStatus(HttpStatusCode.BadRequest)
                val err = fromJson<ApiError>(response)
                assertEquals(401, err.data.code)
            }
            coVerify {
                sessionChecks.verifyUser(any())
                sessionChecks.verifyAdmin(any())
            }
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user info request user does not exist`() {
        declare(named("admins")) { listOf("adminid") }
        mockHere<LinkServerDatabase> {
            coEvery { getUser("targetid") } returns null
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Get, "/api/v1/admin/user/targetid") {
                addHeader("SessionId", sid)
                setJsonBody("""{"target":"targetid","reason":""}""")
            }.apply {
                assertStatus(HttpStatusCode.NotFound)
                val info = fromJson<ApiError>(response)
                assertEquals(402, info.data.code)
            }
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user info request success`() {
        val instant = Instant.now() - Duration.ofHours(19)
        declare(named("admins")) { listOf("adminid") }
        mockHere<LinkServerDatabase> {
            coEvery { getUser("targetid") } returns mockk {
                every { discordId } returns "targetid"
                every { msftIdHash } returns byteArrayOf(1, 2, 3)
                every { creationDate } returns instant
            }
            coEvery { isUserIdentifiable("targetid") } returns true
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Get, "/api/v1/admin/user/targetid") {
                addHeader("SessionId", sid)
                setJsonBody("""{"target":"targetid","reason":""}""")
            }.apply {
                assertStatus(HttpStatusCode.OK)
                val info = fromJson<ApiSuccess>(response)
                assertNotNull(info.data)
                info.data.apply {
                    assertEquals("targetid", get("discordId"))
                    val expectedHash = Base64.getUrlEncoder().encodeToString(byteArrayOf(1, 2, 3))
                    assertEquals(expectedHash, get("msftIdHash"))
                    assertEquals(instant.toString(), get("created"))
                    assertEquals(true, get("identifiable"))
                }
            }
        }
    }

    private fun withTestEpiLink(block: TestApplicationEngine.() -> Unit) =
        withTestApplication({
            with(get<LinkBackEnd>()) {
                installFeatures()
            }
            routing {
                with(get<LinkBackEnd>()) { installErrorHandling() }
                get<LinkAdminApi>().install(this)
            }
        }, block)
}