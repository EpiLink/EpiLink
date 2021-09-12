/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.web

import guru.zoroark.shedinja.dsl.put
import guru.zoroark.shedinja.environment.InjectionScope
import guru.zoroark.shedinja.environment.get
import guru.zoroark.shedinja.environment.invoke
import guru.zoroark.shedinja.environment.named
import guru.zoroark.shedinja.test.ShedinjaBaseTest
import guru.zoroark.shedinja.test.UnsafeMutableEnvironment
import io.ktor.application.ApplicationCall
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.contentType
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.util.pipeline.PipelineContext
import io.mockk.*
import org.epilink.bot.*
import org.epilink.bot.config.RateLimitingProfile
import org.epilink.bot.config.WebServerConfiguration
import org.epilink.bot.db.*
import org.epilink.bot.discord.BanManager
import org.epilink.bot.discord.RoleManager
import org.epilink.bot.http.BackEnd
import org.epilink.bot.http.BackEndImpl
import org.epilink.bot.http.SessionChecker
import org.epilink.bot.http.adminObjAttribute
import org.epilink.bot.http.endpoints.AdminEndpoints
import org.epilink.bot.http.endpoints.AdminEndpointsImpl
import org.epilink.bot.http.sessions.ConnectedSession
import org.epilink.bot.rulebook.getList
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.test.*

private class BanImpl(
    override val banId: Int,
    override val idpIdHash: ByteArray,
    override val expiresOn: Instant?,
    override val issued: Instant,
    override val revoked: Boolean,
    override val author: String,
    override val reason: String
) : Ban

class FakeAdminSessionChecker(scope: InjectionScope) : SessionChecker {
    private val db: DatabaseFacade by scope()

    override suspend fun verifyUser(context: PipelineContext<Unit, ApplicationCall>): Boolean {
        return true
    }

    override suspend fun verifyAdmin(context: PipelineContext<Unit, ApplicationCall>): Boolean {
        context.context.attributes.put(
            adminObjAttribute,
            db.getUser(context.context.sessions.get<ConnectedSession>()!!.discordId)!!
        )
        return true
    }
}


class AdminTest : ShedinjaBaseTest<Unit>(
    Unit::class, {
        put<AdminEndpoints>(::AdminEndpointsImpl)
        put<BackEnd>(::BackEndImpl)
        put<SessionChecker>(::FakeAdminSessionChecker)
        put<WebServerConfiguration> {
            mockk { every { rateLimitingProfile } returns RateLimitingProfile.Standard }
        }
    }
) {
    private val sessionStorage = UnsafeTestSessionStorage()

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test manual identity request on identifiable`() = stest {
        put(named("admins")) { listOf("adminid") }
        val u = mockk<User> { every { discordId } returns "discordId" }
        putMock<DatabaseFacade> {
            coEvery { getUser("userid") } returns u
            coEvery { isUserIdentifiable(u) } returns true
        }
        val lia = putMock<IdentityManager> {
            coEvery {
                accessIdentity(u, false, "admin.name@email", "thisismyreason")
            } returns "trueidentity@othermail"
            coEvery {
                accessIdentity(match { it.discordId == "adminid" }, true, any(), match { it.contains("another user") })
            } returns "admin.name@email"
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid", trueIdentity = "admin.name@email")
            handleRequest(HttpMethod.Post, "/api/v1/admin/idrequest") {
                addHeader("SessionId", sid)
                setJsonBody("""{"target":"userid","reason":"thisismyreason"}""")
            }.apply {
                assertStatus(OK)
                val id = fromJson<ApiSuccess>(response)
                val data = id.data
                assertNotNull(data, "Response has data")
                assertEquals("userid", id.data["target"], "Correct userid")
                assertEquals("trueidentity@othermail", id.data["identity"], "Correct retrieved true identity")
            }
            coVerify {
                lia.accessIdentity(u, false, "admin.name@email", "thisismyreason")
                lia.accessIdentity(any(), true, any(), match { it.contains("another user") })
            }
        }
    }

    @Test
    fun `Test manual identity request on unknown target`() = stest {
        put(named("admins")) { listOf("adminid") }
        putMock<DatabaseFacade> {
            coEvery { getUser("userid") } returns null
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid", trueIdentity = "admin.name@email")
            handleRequest(HttpMethod.Post, "/api/v1/admin/idrequest") {
                addHeader("SessionId", sid)
                setJsonBody("""{"target":"userid","reason":"thisismyreason"}""")
            }.apply {
                assertStatus(BadRequest)
                val err = fromJson<ApiError>(response)
                assertEquals(402, err.data.code)
            }
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test manual identity request on unidentifiable target`() = stest {
        put(named("admins")) { listOf("adminid") }
        val u = mockk<User> { every { discordId } returns "userid" }
        putMock<DatabaseFacade> {
            coEvery { getUser("userid") } returns u
            coEvery { isUserIdentifiable(u) } returns false
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid", trueIdentity = "admin.name@email")
            handleRequest(HttpMethod.Post, "/api/v1/admin/idrequest") {
                addHeader("SessionId", sid)
                setJsonBody("""{"target":"userid","reason":"thisismyreason"}""")
            }.apply {
                assertStatus(BadRequest)
                val err = fromJson<ApiError>(response)
                assertEquals(430, err.data.code)
            }
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test manual identity request missing reason `() = stest {
        put(named("admins")) { listOf("adminid") }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid", trueIdentity = "admin.name@email")
            handleRequest(HttpMethod.Post, "/api/v1/admin/idrequest") {
                addHeader("SessionId", sid)
                setJsonBody("""{"target":"userid","reason":""}""")
            }.apply {
                assertStatus(BadRequest)
                val err = fromJson<ApiError>(response)
                assertEquals(401, err.data.code)
            }
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user info request user does not exist`() = stest {
        put(named("admins")) { listOf("adminid") }
        putMock<DatabaseFacade> {
            coEvery { getUser("targetid") } returns null
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Get, "/api/v1/admin/user/targetid") {
                addHeader("SessionId", sid)
                setJsonBody("""{"target":"targetid","reason":""}""")
            }.apply {
                assertStatus(NotFound)
                val info = fromJson<ApiError>(response)
                assertEquals(402, info.data.code)
            }
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user info request success`() = stest {
        val instant = Instant.now() - Duration.ofHours(19)
        put(named("admins")) { listOf("adminid") }
        val targetMock = mockk<User> {
            every { discordId } returns "targetid"
            every { idpIdHash } returns byteArrayOf(1, 2, 3)
            every { creationDate } returns instant
        }
        putMock<DatabaseFacade> {
            coEvery { getUser("targetid") } returns targetMock
            coEvery { isUserIdentifiable(targetMock) } returns true
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Get, "/api/v1/admin/user/targetid") {
                addHeader("SessionId", sid)
                setJsonBody("""{"target":"targetid","reason":""}""")
            }.apply {
                assertStatus(OK)
                val info = fromJson<ApiSuccess>(response)
                assertNotNull(info.data)
                info.data.apply {
                    assertEquals("targetid", get("discordId"))
                    val expectedHash = Base64.getUrlEncoder().encodeToString(byteArrayOf(1, 2, 3))
                    assertEquals(expectedHash, get("idpIdHash"))
                    assertEquals(instant.toString(), get("created"))
                    assertEquals(true, get("identifiable"))
                }
            }
        }
    }

    @Test
    fun `Test retrieve all bans of user`() = stest {
        put(named("admins")) { listOf("adminid") }
        val msftHash = byteArrayOf(1, 2, 3, 4, 5)
        val msftHashStr = Base64.getUrlEncoder().encodeToString(msftHash)
        val now = Instant.now()
        val bans = listOf(
            BanImpl(
                0,
                msftHash,
                now - Duration.ofSeconds(10),
                now - Duration.ofDays(1),
                true,
                "Yeet",
                "You got gnomed"
            ),
            BanImpl(1, msftHash, null, now - Duration.ofDays(3), false, "Oops", "Tinkie winkiiiie")
        )
        putMock<DatabaseFacade> {
            coEvery { getBansFor(msftHash) } returns bans
        }
        putMock<BanLogic> {
            // Technically incorrect, as one of the bans mocked above would still be active
            // (this ensures we actually use isBanActive)
            every { isBanActive(any()) } returns false
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Get, "/api/v1/admin/ban/$msftHashStr") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(OK)
                val info = fromJson<ApiSuccess>(response)
                val data = info.data
                assertNotNull(data)
                assertEquals(false, data["banned"])
                val actualBans = data.getListOfMaps("bans")
                val banOne = actualBans[0]
                assertEquals(0, banOne["id"])
                assertEquals(true, banOne["revoked"])
                assertEquals("Yeet", banOne["author"])
                assertEquals("You got gnomed", banOne["reason"])
                assertEquals(bans[0].expiresOn.toString(), banOne["expiresOn"])
                assertEquals(bans[0].issued.toString(), banOne["issuedAt"])
                val banTwo = actualBans[1]
                assertEquals(1, banTwo["id"])
                assertEquals(false, banTwo["revoked"])
                assertEquals("Oops", banTwo["author"])
                assertEquals("Tinkie winkiiiie", banTwo["reason"])
                assertNull(banTwo["expiresOn"])
                assertEquals(bans[1].issued.toString(), banTwo["issuedAt"])
            }
        }
    }

    @Test
    fun `Test retrieve specific ban correct hash`() = stest {
        put(named("admins")) { listOf("adminid") }
        val msftHash = byteArrayOf(1, 2, 3, 4, 5)
        val msftHashStr = Base64.getUrlEncoder().encodeToString(msftHash)
        val now = Instant.now()
        val ban =
            BanImpl(0, msftHash, now - Duration.ofSeconds(10), now - Duration.ofDays(1), true, "Yeet", "You got gnomed")
        putMock<DatabaseFacade> {
            coEvery { getBan(0) } returns ban
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Get, "/api/v1/admin/ban/$msftHashStr/0") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(OK)
                val info = fromJson<ApiSuccess>(response)
                val data = info.data
                assertNotNull(data)
                assertEquals(0, data["id"])
                assertEquals(true, data["revoked"])
                assertEquals("Yeet", data["author"])
                assertEquals("You got gnomed", data["reason"])
                assertEquals(ban.expiresOn.toString(), data["expiresOn"])
                assertEquals(ban.issued.toString(), data["issuedAt"])
            }
        }
    }

    @Test
    fun `Test retrieve specific ban malformed id`() = stest {
        put(named("admins")) { listOf("adminid") }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Get, "/api/v1/admin/ban/sqdfhj/eeeeeee") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(BadRequest)
                val info = fromJson<ApiError>(response)
                val data = info.data
                assertEquals(403, data.code)
            }
        }
    }

    @Test
    fun `Test retrieve specific ban incorrect hash`() = stest {
        put(named("admins")) { listOf("adminid") }
        val msftHash = byteArrayOf(1, 2, 3, 4, 5)
        val now = Instant.now()
        val ban =
            BanImpl(0, msftHash, now - Duration.ofSeconds(10), now - Duration.ofDays(1), true, "Yeet", "You got gnomed")
        putMock<DatabaseFacade> {
            coEvery { getBan(0) } returns ban
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Get, "/api/v1/admin/ban/sqdfhj/0") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(NotFound)
                val info = fromJson<ApiError>(response)
                val data = info.data
                assertEquals(403, data.code)
            }
        }
    }

    @Test
    fun `Test retrieve specific ban incorrect id`() = stest {
        put(named("admins")) { listOf("adminid") }
        putMock<DatabaseFacade> {
            coEvery { getBan(33333) } returns null
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Get, "/api/v1/admin/ban/sqdfhj/33333") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(NotFound)
                val info = fromJson<ApiError>(response)
                val data = info.data
                assertEquals(403, data.code)
            }
        }
    }

    @Test
    fun `Test revoking a ban`() = stest {
        put(named("admins")) { listOf("adminid") }
        val msftHash = byteArrayOf(1, 2, 3, 4, 5)
        val msftHashStr = Base64.getUrlEncoder().encodeToString(msftHash)
        val bm = putMock<BanManager> {
            coEvery { revokeBan(any(), 12345) } returns mockk()
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Post, "/api/v1/admin/ban/$msftHashStr/12345/revoke") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(OK)
                val info = fromJson<ApiSuccess>(response)
                assertEquals("Ban revoked.", info.message)
                assertNull(info.data)
            }
        }
        coVerify { bm.revokeBan(any(), 12345) }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test creating a ban with valid instant format`() = stest {
        put(named("admins")) { listOf("adminid") }
        val msftHashStr = "Base64"
        val msftHash = Base64.getDecoder().decode(msftHashStr)
        val instant = Instant.now() + Duration.ofHours(5)
        // These don't correspond to the request, this is normal
        val expInst = Instant.now() + Duration.ofDays(10)
        val issuedInst = Instant.now()
        val ban = BanImpl(123, msftHash, expInst, issuedInst, false, "the Author", "a reason")
        val bm = putMock<BanManager> {
            coEvery { ban(msftHashStr, instant, "author identity", "This is my reason") } returns ban
        }
        putMock<IdentityManager> {
            coEvery {
                accessIdentity(match { it.discordId == "adminid" }, true, any(), any())
            } returns "author identity"
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Post, "/api/v1/admin/ban/$msftHashStr") {
                addHeader("SessionId", sid)
                setJsonBody("""{"reason":"This is my reason","expiresOn":"$instant"}""")
            }.apply {
                assertStatus(OK)
                val info = fromJson<ApiSuccess>(response)
                val data = info.data
                assertNotNull(data)
                assertEquals(123, data["id"])
                assertEquals(false, data["revoked"])
                assertEquals("the Author", data["author"])
                assertEquals("a reason", data["reason"])
                assertEquals(expInst.toString(), data["expiresOn"])
                assertEquals(issuedInst.toString(), data["issuedAt"])
            }
        }
        coVerify {
            bm.ban(msftHashStr, instant, "author identity", "This is my reason")
        }
    }

    @Test
    fun `Test creating a ban with invalid instant`() = stest {
        put(named("admins")) { listOf("adminid") }
        val msftHashStr = "Base64"
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Post, "/api/v1/admin/ban/$msftHashStr") {
                addHeader("SessionId", sid)
                setJsonBody("""{"reason":"This is my reason","expiresOn":"that's not ok"}""")
            }.apply {
                assertStatus(BadRequest)
                val info = fromJson<ApiError>(response)
                assertEquals("Invalid expiry timestamp.", info.message)
                assertEquals(404, info.data.code)
            }
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test generating a GDPR report`() = stest {
        put(named("admins")) { listOf("adminid") }
        val u = mockk<User>()
        putMock<DatabaseFacade> {
            coEvery { getUser("userid") } returns u
        }
        putMock<IdentityManager> {
            coEvery {
                accessIdentity(match { it.discordId == "adminid" }, true, any(), any())
            } returns "admin@admin.admin"
        }
        putMock<GdprReport> {
            coEvery { getFullReport(u, "admin@admin.admin") } returns "C'est la merguez, merguez partie !"
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Post, "/api/v1/admin/gdprreport/userid") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(OK)
                assertEquals("text/markdown", response.contentType().toString())
                assertEquals("C'est la merguez, merguez partie !", response.content)
            }
        }
    }

    @Test
    fun `Test generating a GDPR report on non-existant user`() = stest {
        put(named("admins")) { listOf("adminid") }
        putMock<DatabaseFacade> {
            coEvery { getUser(any()) } returns null
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Post, "/api/v1/admin/gdprreport/yeeeet") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(NotFound)
                val data = fromJson<ApiError>(response)
                assertEquals(402, data.data.code)
            }
        }
    }

    @Test
    fun `Test deleting a user`() = stest {
        val u = mockk<User>()
        put(named("admins")) { listOf("adminid") }
        val dbf = putMock<DatabaseFacade> {
            coEvery { getUser("yep") } returns u
            coEvery { deleteUser(u) } just runs
        }
        val rm = putMock<RoleManager> {
            coEvery { invalidateAllRolesLater("yep") } returns mockk()
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Delete, "/api/v1/admin/user/yep") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(OK)
                fromJson<ApiSuccess>(response)
            }
        }
        coVerify {
            dbf.deleteUser(u)
            rm.invalidateAllRolesLater("yep")
        }
    }

    @Test
    fun `Test deleting a nonexistant user`() = stest {
        put(named("admins")) { listOf("adminid") }
        putMock<DatabaseFacade> {
            coEvery { getUser("yep") } returns null
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Delete, "/api/v1/admin/user/yep") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(NotFound)
                val data = fromJson<ApiError>(response)
                assertEquals(402, data.data.code)
            }
        }
    }

    @Test
    fun `Test partial hash retrieval`() = stest {
        put(named("admins")) { listOf("adminid") }
        putMock<DatabaseFacade> {
            coEvery { searchUserByPartialHash("fe1234") } returns listOf(
                mockk { every { discordId } returns "user1" },
                mockk { every { discordId } returns "user2" }
            )
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Get, "/api/v1/admin/search/hash16/fe1234") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(OK)
                val data = fromJson<ApiSuccess>(response)
                val results = data.data!!.getList("results")
                assertEquals(listOf("user1", "user2"), results)
            }
        }
    }

    @Test
    fun `Test partial hash retrieval invalid hex`() = stest {
        put(named("admins")) { listOf("adminid") }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Get, "/api/v1/admin/search/hash16/gggg") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(BadRequest)
                val data = fromJson<ApiError>(response)
                assertEquals(400, data.data.code)
                assertEquals("Invalid hex string", data.message)
                assertEquals("adm.ihs", data.message_i18n)
            }
        }
    }

    private fun UnsafeMutableEnvironment.withTestEpiLink(block: TestApplicationEngine.() -> Unit) {
        put<CacheClient> { DummyCacheClient { sessionStorage } }
        withTestApplication({
            with(get<BackEnd>()) {
                installFeatures()
            }
            routing {
                with(get<BackEnd>()) { installErrorHandling() }
                get<AdminEndpoints>().install(this)
            }
        }, block)
    }
}
