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
import io.mockk.*
import org.epilink.bot.db.LinkServerDatabase
import org.epilink.bot.db.LinkUser
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.discord.DiscordEmbed
import org.epilink.bot.discord.LinkDiscordClientFacade
import org.epilink.bot.discord.LinkDiscordMessages
import org.epilink.bot.http.LinkBackEnd
import org.epilink.bot.http.LinkBackEndImpl
import org.epilink.bot.http.LinkSessionChecks
import org.epilink.bot.http.LinkSessionChecksImpl
import org.epilink.bot.http.endpoints.LinkAdminApi
import org.epilink.bot.http.endpoints.LinkAdminApiImpl
import org.epilink.bot.http.endpoints.LinkUserApi
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.get
import org.koin.test.mock.declare
import kotlin.test.*

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
        val db = mockHere<LinkServerDatabase> {
            coEvery { getUser("userid") } returns u
            coEvery { isUserIdentifiable("userid") } returns true
            coEvery { accessIdentity(u, false, "admin.name@email", "thisismyreason") } returns "trueidentity@othermail"
        }
        val embed = mockk<DiscordEmbed>()
        mockHere<LinkDiscordMessages> {
            every { getIdentityAccessEmbed(false, "admin.name@email", "thisismyreason") } returns embed
            every { getIdentityAccessEmbed(true, any(), match { it.contains("another user") }) } returns mockk()
        }
        val dcf = mockHere<LinkDiscordClientFacade> {
            coEvery { sendDirectMessage("userid", embed) } just runs
            coEvery { sendDirectMessage("adminid", any()) } just runs
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
                dcf.sendDirectMessage("userid", embed)
                db.accessIdentity(u, false, "admin.name@email", "thisismyreason")
            }
        }
    }

    @Test
    fun `Test manual identity request on unknown target`() {
        declare(named("admins")) { listOf("adminid") }
        val u = mockk<LinkUser>()
        val db = mockHere<LinkServerDatabase> {
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