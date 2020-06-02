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
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.epilink.bot.db.*
import org.epilink.bot.discord.LinkBanManager
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
import kotlin.test.assertNull

private class BanImpl(
    override val banId: Int,
    override val msftIdHash: ByteArray,
    override val expiresOn: Instant?,
    override val issued: Instant,
    override val revoked: Boolean,
    override val author: String,
    override val reason: String
) : LinkBan

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
                assertStatus(OK)
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
                assertStatus(OK)
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

    @Test
    fun `Test retrieve all bans of user`() {
        declare(named("admins")) { listOf("adminid") }
        val msftHash = byteArrayOf(1, 2, 3, 4, 5)
        val msftHashStr = Base64.getUrlEncoder().encodeToString(msftHash)
        val now = Instant.now()
        val bans = listOf(
            BanImpl(0, msftHash, now - Duration.ofSeconds(10), now - Duration.ofDays(1), true, "Yeet", "You got gnomed"),
            BanImpl(1, msftHash, null, now - Duration.ofDays(3), false, "Oops", "Tinkie winkiiiie")
        )
        mockHere<LinkDatabaseFacade> {
            coEvery { getBansFor(msftHash) } returns bans
        }
        mockHere<LinkBanLogic> {
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
    fun `Test retrieve specific ban correct hash`() {
        declare(named("admins")) { listOf("adminid") }
        val msftHash = byteArrayOf(1, 2, 3, 4, 5)
        val msftHashStr = Base64.getUrlEncoder().encodeToString(msftHash)
        val now = Instant.now()
        val ban =
            BanImpl(0, msftHash, now - Duration.ofSeconds(10), now - Duration.ofDays(1), true, "Yeet", "You got gnomed")
        mockHere<LinkDatabaseFacade> {
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
    fun `Test retrieve specific ban malformed id`() {
        declare(named("admins")) { listOf("adminid") }
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
    fun `Test retrieve specific ban incorrect hash`() {
        declare(named("admins")) { listOf("adminid") }
        val msftHash = byteArrayOf(1, 2, 3, 4, 5)
        val now = Instant.now()
        val ban =
            BanImpl(0, msftHash, now - Duration.ofSeconds(10), now - Duration.ofDays(1), true, "Yeet", "You got gnomed")
        mockHere<LinkDatabaseFacade> {
            coEvery { getBan(0) } returns ban
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Get, "/api/v1/admin/ban/sqdfhj/0") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(HttpStatusCode.NotFound)
                val info = fromJson<ApiError>(response)
                val data = info.data
                assertEquals(403, data.code)
            }
        }
    }

    @Test
    fun `Test retrieve specific ban incorrect id`() {
        declare(named("admins")) { listOf("adminid") }
        mockHere<LinkDatabaseFacade> {
            coEvery { getBan(33333) } returns null
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Get, "/api/v1/admin/ban/sqdfhj/33333") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(HttpStatusCode.NotFound)
                val info = fromJson<ApiError>(response)
                val data = info.data
                assertEquals(403, data.code)
            }
        }
    }

    @Test
    fun `Test revoking a ban`() {
        declare(named("admins")) { listOf("adminid") }
        val msftHash = byteArrayOf(1, 2, 3, 4, 5)
        val msftHashStr = Base64.getUrlEncoder().encodeToString(msftHash)
        val bm = mockHere<LinkBanManager> {
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
    fun `Test creating a ban with valid instant format`() {
        declare(named("admins")) { listOf("adminid") }
        val msftHashStr = "Base64"
        val msftHash = Base64.getDecoder().decode(msftHashStr)
        val instant = Instant.now() + Duration.ofHours(5)
        // These don't correspond to the request, this is normal
        val expInst = Instant.now() + Duration.ofDays(10)
        val issuedInst = Instant.now()
        val ban = BanImpl(123, msftHash, expInst, issuedInst, false, "the Author", "a reason")
        val bm = mockHere<LinkBanManager> {
            coEvery { ban(msftHashStr, instant, "author identity", "This is my reason") } returns ban
        }
        mockHere<LinkIdAccessor> {
            coEvery { accessIdentity("adminid", true, any(), any()) } returns "author identity"
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "adminid")
            handleRequest(HttpMethod.Post, "/api/v1/admin/ban/$msftHashStr") {
                addHeader("SessionId", sid)
                setJsonBody("""{"reason":"This is my reason","expiresOn":"$instant"}""")
            }.apply {
                assertStatus(OK)
                val info = fromJson<ApiSuccess>(response)
                assertEquals("Ban created.", info.message)
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
    fun `Test creating a ban with invalid instant`() {
        declare(named("admins")) { listOf("adminid") }
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