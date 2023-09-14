/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.user

import guru.zoroark.tegral.di.dsl.put
import guru.zoroark.tegral.di.dsl.tegralDiModule
import guru.zoroark.tegral.di.environment.get
import guru.zoroark.tegral.di.environment.invoke
import guru.zoroark.tegral.di.environment.named
import guru.zoroark.tegral.di.test.TegralSubjectTest
import guru.zoroark.tegral.di.test.TestMutableInjectionEnvironment
import guru.zoroark.tegral.di.test.mockk.putMock
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.routing
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.util.pipeline.PipelineContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import org.epilink.bot.CacheClient
import org.epilink.bot.ErrorCode
import org.epilink.bot.UserEndpointException
import org.epilink.bot.assertStatus
import org.epilink.bot.config.RateLimitingProfile
import org.epilink.bot.config.WebServerConfiguration
import org.epilink.bot.db.DatabaseFacade
import org.epilink.bot.db.IdentityManager
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.discord.RoleManager
import org.epilink.bot.fromJson
import org.epilink.bot.http.BackEnd
import org.epilink.bot.http.BackEndImpl
import org.epilink.bot.http.IdentityProvider
import org.epilink.bot.http.SessionChecker
import org.epilink.bot.http.UserIdentityInfo
import org.epilink.bot.http.data.IdAccess
import org.epilink.bot.http.data.IdAccessLogs
import org.epilink.bot.http.endpoints.UserApi
import org.epilink.bot.http.endpoints.UserApiImpl
import org.epilink.bot.http.sessions.ConnectedSession
import org.epilink.bot.http.userObjAttribute
import org.epilink.bot.setJsonBody
import org.epilink.bot.sha256
import org.epilink.bot.web.ApiError
import org.epilink.bot.web.ApiSuccess
import org.epilink.bot.web.DummyCacheClient
import org.epilink.bot.web.UnsafeTestSessionStorage
import org.epilink.bot.web.injectUserIntoAttributes
import org.epilink.bot.web.setupSession
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserTest : TegralSubjectTest<Unit>(
    Unit::class,
    {
        put<UserApi>(::UserApiImpl)
        put<BackEnd>(::BackEndImpl)
        put<SessionChecker> {
            val db: DatabaseFacade by scope()
            mockk {
                val slot = slot<PipelineContext<Unit, ApplicationCall>>()
                coEvery { verifyUser(capture(slot)) } coAnswers {
                    injectUserIntoAttributes(slot, userObjAttribute, db)
                    true
                }
            }
        }
        put<WebServerConfiguration> {
            mockk { every { rateLimitingProfile } returns RateLimitingProfile.Standard }
        }
        put(named("admins")) { listOf("adminid") }
    }
) {

    private val sessionStorage = UnsafeTestSessionStorage()

    private fun additionalModule() = tegralDiModule {
        put<CacheClient> { DummyCacheClient { sessionStorage } }
    }

    private val TestMutableInjectionEnvironment.sessionChecker: SessionChecker
        get() = get() // I'm not sure if inject() handles test execution properly

    private fun testWithSession(block: TestMutableInjectionEnvironment.() -> Unit) = test({ put(additionalModule()) }) {
        put<CacheClient> { DummyCacheClient { sessionStorage } }
        block()
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user endpoint when identifiable`() = testWithSession {
        withTestEpiLink {
            putMock<DatabaseFacade> {
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
    fun `Test user endpoint when not identifiable`() = testWithSession {
        withTestEpiLink {
            putMock<DatabaseFacade> {
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
    fun `Test user endpoint when admin`() = testWithSession {
        withTestEpiLink {
            putMock<DatabaseFacade> {
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
    fun `Test user access logs retrieval`() = testWithSession {
        val inst1 = Instant.now() - Duration.ofHours(1)
        val inst2 = Instant.now() - Duration.ofHours(10)
        putMock<IdentityManager> {
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
    fun `Test user identity relink with correct account`() = testWithSession {
        val msftId = "MyMicrosoftId"
        val hashMsftId = msftId.sha256()
        val email = "e.mail@mail.maiiiil"
        putMock<IdentityProvider> {
            coEvery { getUserIdentityInfo("msauth", "uriii") } returns UserIdentityInfo("MyMicrosoftId", email)
        }
        val rm = putMock<RoleManager> {
            every { invalidateAllRolesLater("userid", true) } returns mockk()
        }
        putMock<DatabaseFacade> {
            coEvery { isUserIdentifiable(any()) } returns false
        }
        val ida = putMock<IdentityManager> {
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
    fun `Test user identity relink with account already linked`() = testWithSession {
        val msftId = "MyMicrosoftId"
        val hashMsftId = msftId.sha256()
        val mbe = putMock<IdentityProvider> {
            coEvery { getUserIdentityInfo("msauth", "uriii") } returns UserIdentityInfo("", "")
        }
        putMock<DatabaseFacade> {
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
    fun `Test user identity relink with relink error`() = testWithSession {
        val msftId = "MyMicrosoftId"
        val hashMsftId = msftId.sha256()
        val email = "e.mail@mail.maiiiil"
        putMock<IdentityProvider> {
            coEvery { getUserIdentityInfo("msauth", "uriii") } returns UserIdentityInfo("MyMicrosoftId", email)
        }
        putMock<DatabaseFacade> {
            coEvery { isUserIdentifiable(any()) } returns false
        }
        putMock<IdentityManager> {
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
    fun `Test user identity deletion when no identity exists`() = testWithSession {
        putMock<DatabaseFacade> {
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
    fun `Test user identity deletion success`() = testWithSession {
        putMock<DatabaseFacade> {
            coEvery { isUserIdentifiable(any()) } returns true
        }
        val ida = putMock<IdentityManager> {
            coEvery { deleteUserIdentity(match { it.discordId == "userid" }) } just runs
        }
        val rm = putMock<RoleManager> {
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
    fun `Test user log out`() = testWithSession {
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

    private fun TestMutableInjectionEnvironment.withTestEpiLink(block: TestApplicationEngine.() -> Unit) =
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
