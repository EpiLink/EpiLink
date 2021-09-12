/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.user

import io.ktor.application.ApplicationCall
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.util.pipeline.PipelineContext
import io.mockk.*
import org.epilink.bot.*
import org.epilink.bot.config.RateLimitingProfile
import org.epilink.bot.config.WebServerConfiguration
import org.epilink.bot.db.DatabaseFacade
import org.epilink.bot.db.IdentityManager
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.discord.RoleManager
import org.epilink.bot.http.*
import org.epilink.bot.http.data.IdAccess
import org.epilink.bot.http.data.IdAccessLogs
import org.epilink.bot.http.endpoints.UserApi
import org.epilink.bot.http.endpoints.UserApiImpl
import org.epilink.bot.http.sessions.ConnectedSession
import org.epilink.bot.web.*
import org.epilink.bot.web.UnsafeTestSessionStorage
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.get
import java.time.Duration
import java.time.Instant
import kotlin.test.*

class UserTest : EpiLinkBaseTest<Unit>(
    Unit::class,
    module {
        single<UserApi> { UserApiImpl() }
        single<BackEnd> { BackEndImpl() }
        single<SessionChecker> {
            mockk {
                val slot = slot<PipelineContext<Unit, ApplicationCall>>()
                coEvery { verifyUser(capture(slot)) } coAnswers {
                    injectUserIntoAttributes(slot, userObjAttribute)
                    true
                }
            }
        }
        single<WebServerConfiguration> {
            mockk { every { rateLimitingProfile } returns RateLimitingProfile.Standard }
        }
        single(named("admins")) { listOf("adminid") }
    }
) {
    override fun additionalModule(): Module? = module {
        single<CacheClient> { DummyCacheClient { sessionStorage } }
    }

    private val sessionStorage = UnsafeTestSessionStorage()
    private val sessionChecker: SessionChecker
        get() = get() // I'm not sure if inject() handles test execution properly

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user endpoint when identifiable`() {
        withTestEpiLink {
            mockHere<DatabaseFacade> {
                coEvery { isUserIdentifiable(any()) } returns true
            }
            val sid = setupSession(
                sessionStorage,
                discId = "myDiscordId",
                discUsername = "Discordian#1234",
                discAvatarUrl = "https://veryavatar"
            )
            handleRequest(HttpMethod.Get, "/api/v1/user") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(HttpStatusCode.OK)
                val data = fromJson<ApiSuccess>(response)
                assertNotNull(data.data)
                assertEquals("myDiscordId", data.data["discordId"])
                assertEquals("Discordian#1234", data.data["username"])
                assertEquals("https://veryavatar", data.data["avatarUrl"])
                assertEquals(true, data.data["identifiable"])
                assertEquals(false, data.data["privileged"])
            }
            coVerify { sessionChecker.verifyUser(any()) }
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user endpoint when not identifiable`() {
        withTestEpiLink {
            mockHere<DatabaseFacade> {
                coEvery { isUserIdentifiable(any()) } returns false
            }
            val sid = setupSession(
                sessionStorage,
                discId = "myDiscordId",
                discUsername = "Discordian#1234",
                discAvatarUrl = "https://veryavatar"
            )
            handleRequest(HttpMethod.Get, "/api/v1/user") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(HttpStatusCode.OK)
                val data = fromJson<ApiSuccess>(response)
                assertNotNull(data.data)
                assertEquals("myDiscordId", data.data["discordId"])
                assertEquals("Discordian#1234", data.data["username"])
                assertEquals("https://veryavatar", data.data["avatarUrl"])
                assertEquals(false, data.data["identifiable"])
                assertEquals(false, data.data["privileged"])
            }
            coVerify { sessionChecker.verifyUser(any()) }
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user endpoint when admin`() {
        withTestEpiLink {
            mockHere<DatabaseFacade> {
                coEvery { isUserIdentifiable(any()) } returns false
            }
            val sid = setupSession(
                sessionStorage,
                discId = "adminid",
                discUsername = "Discordian#1234",
                discAvatarUrl = "https://veryavatar"
            )
            handleRequest(HttpMethod.Get, "/api/v1/user") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(HttpStatusCode.OK)
                val data = fromJson<ApiSuccess>(response)
                assertNotNull(data.data)
                assertEquals("adminid", data.data["discordId"])
                assertEquals("Discordian#1234", data.data["username"])
                assertEquals("https://veryavatar", data.data["avatarUrl"])
                assertEquals(false, data.data["identifiable"])
                assertEquals(true, data.data["privileged"])
            }
            coVerify { sessionChecker.verifyUser(any()) }
        }
    }

    @Test
    fun `Test user access logs retrieval`() {
        val inst1 = Instant.now() - Duration.ofHours(1)
        val inst2 = Instant.now() - Duration.ofHours(10)
        mockHere<IdentityManager> {
            coEvery { getIdAccessLogs(match { it.discordId == "discordid" }) } returns IdAccessLogs(
                manualAuthorsDisclosed = false,
                accesses = listOf(
                    IdAccess(true, "Robot Robot Robot", "Yes", inst1.toString()),
                    IdAccess(false, null, "No", inst2.toString())
                )
            )
        }
        withTestEpiLink {
            val sid = setupSession(
                sessionStorage,
                discId = "discordid"
            )
            handleRequest(HttpMethod.Get, "/api/v1/user/idaccesslogs") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(HttpStatusCode.OK)
                fromJson<ApiSuccess>(response).apply {
                    assertNotNull(data)
                    assertEquals(false, data["manualAuthorsDisclosed"])
                    val list = data["accesses"] as? List<*> ?: error("Unexpected format on accesses")
                    assertEquals(2, list.size)
                    val first = list[0] as? Map<*, *> ?: error("Unexpected format")
                    assertEquals(true, first["automated"])
                    assertEquals("Robot Robot Robot", first["author"])
                    assertEquals("Yes", first["reason"])
                    assertEquals(inst1.toString(), first["timestamp"])
                    val second = list[1] as? Map<*, *> ?: error("Unexpected format")
                    assertEquals(false, second["automated"])
                    assertEquals(null, second.getOrDefault("author", "WRONG"))
                    assertEquals("No", second["reason"])
                    assertEquals(inst2.toString(), second["timestamp"])
                }
            }
            coVerify { sessionChecker.verifyUser(any()) }
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user identity relink with correct account`() {
        val msftId = "MyMicrosoftId"
        val hashMsftId = msftId.sha256()
        val email = "e.mail@mail.maiiiil"
        mockHere<IdentityProvider> {
            coEvery { getUserIdentityInfo("msauth", "uriii") } returns UserIdentityInfo("MyMicrosoftId", email)
        }
        val rm = mockHere<RoleManager> {
            every { invalidateAllRolesLater("userid", true) } returns mockk()
        }
        mockHere<DatabaseFacade> {
            coEvery { isUserIdentifiable(any()) } returns false
        }
        val ida = mockHere<IdentityManager> {
            coEvery { relinkIdentity(match { it.discordId == "userid" }, email, "MyMicrosoftId") } just runs
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "userid", msIdHash = hashMsftId)
            handleRequest(HttpMethod.Post, "/api/v1/user/identity") {
                addHeader("SessionId", sid)
                setJsonBody("""{"code":"msauth","redirectUri":"uriii"}""")
            }.apply {
                assertStatus(HttpStatusCode.OK)
                val resp = fromJson<ApiSuccess>(response)
                assertTrue(resp.success)
                assertNull(resp.data)
            }
        }
        coVerify {
            rm.invalidateAllRolesLater(any(), true)
            ida.relinkIdentity(any(), email, "MyMicrosoftId")
            sessionChecker.verifyUser(any())
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user identity relink with account already linked`() {
        val msftId = "MyMicrosoftId"
        val hashMsftId = msftId.sha256()
        val mbe = mockHere<IdentityProvider> {
            coEvery { getUserIdentityInfo("msauth", "uriii") } returns UserIdentityInfo("", "")
        }
        mockHere<DatabaseFacade> {
            coEvery { isUserIdentifiable(any()) } returns true
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "userid", msIdHash = hashMsftId)
            handleRequest(HttpMethod.Post, "/api/v1/user/identity") {
                addHeader("SessionId", sid)
                setJsonBody("""{"code":"msauth","redirectUri":"uriii"}""")
            }.apply {
                assertStatus(HttpStatusCode.BadRequest)
                val resp = fromJson<ApiError>(response)
                val err = resp.data
                assertEquals(110, err.code)
            }
        }
        coVerify {
            mbe.getUserIdentityInfo("msauth", "uriii") // Ensure the back-end has consumed the authcode
            sessionChecker.verifyUser(any())
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user identity relink with relink error`() {
        val msftId = "MyMicrosoftId"
        val hashMsftId = msftId.sha256()
        val email = "e.mail@mail.maiiiil"
        mockHere<IdentityProvider> {
            coEvery { getUserIdentityInfo("msauth", "uriii") } returns UserIdentityInfo("MyMicrosoftId", email)
        }
        mockHere<DatabaseFacade> {
            coEvery { isUserIdentifiable(any()) } returns false
        }
        mockHere<IdentityManager> {
            coEvery {
                relinkIdentity(match { it.discordId == "userid" }, email, "MyMicrosoftId")
            } throws UserEndpointException(
                object : ErrorCode {
                    override val code = 98765
                    override val description = "Strange error WeirdChamp"
                }
            )
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "userid", msIdHash = hashMsftId)
            handleRequest(HttpMethod.Post, "/api/v1/user/identity") {
                addHeader("SessionId", sid)
                setJsonBody("""{"code":"msauth","redirectUri":"uriii"}""")
            }.apply {
                assertStatus(HttpStatusCode.BadRequest)
                val resp = fromJson<ApiError>(response)
                val err = resp.data
                assertEquals(98765, err.code)
            }
            coVerify { sessionChecker.verifyUser(any()) }
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user identity deletion when no identity exists`() {
        mockHere<DatabaseFacade> {
            coEvery { isUserIdentifiable(any()) } returns false
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "userid")
            handleRequest(HttpMethod.Delete, "/api/v1/user/identity") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(HttpStatusCode.BadRequest)
                val resp = fromJson<ApiError>(response)
                val err = resp.data
                assertEquals(111, err.code)
            }
            coVerify { sessionChecker.verifyUser(any()) }
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user identity deletion success`() {
        mockHere<DatabaseFacade> {
            coEvery { isUserIdentifiable(any()) } returns true
        }
        val ida = mockHere<IdentityManager> {
            coEvery { deleteUserIdentity(match { it.discordId == "userid" }) } just runs
        }
        val rm = mockHere<RoleManager> {
            every { invalidateAllRolesLater("userid") } returns mockk()
        }
        withTestEpiLink {
            val sid = setupSession(sessionStorage, "userid")
            handleRequest(HttpMethod.Delete, "/api/v1/user/identity") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(HttpStatusCode.OK)
                val resp = fromJson<ApiSuccess>(response)
                assertNull(resp.data)
            }
        }
        coVerify {
            ida.deleteUserIdentity(any())
            rm.invalidateAllRolesLater(any())
            sessionChecker.verifyUser(any())
        }
    }

    @Test
    fun `Test user log out`() {
        withTestEpiLink {
            val sid = setupSession(sessionStorage)
            handleRequest(HttpMethod.Post, "/api/v1/user/logout") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(HttpStatusCode.OK)
                assertNull(fromJson<ApiSuccess>(response).data)
                assertNull(sessions.get<ConnectedSession>())
            }
            coVerify { sessionChecker.verifyUser(any()) }
        }
    }

    private fun withTestEpiLink(block: TestApplicationEngine.() -> Unit) =
        withTestApplication({
            with(get<BackEnd>()) {
                installFeatures()
            }
            routing {
                with(get<BackEnd>()) { installErrorHandling() }
                get<UserApi>().install(this)
            }
        }, block)
}
