/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.user

import guru.zoroark.shedinja.dsl.put
import guru.zoroark.shedinja.environment.InjectionScope
import guru.zoroark.shedinja.environment.get
import guru.zoroark.shedinja.environment.invoke
import guru.zoroark.shedinja.environment.named
import guru.zoroark.shedinja.test.ShedinjaBaseTest
import guru.zoroark.shedinja.test.UnsafeMutableEnvironment
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
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
import org.epilink.bot.putMock
import org.epilink.bot.setJsonBody
import org.epilink.bot.sha256
import org.epilink.bot.stest
import org.epilink.bot.web.ApiError
import org.epilink.bot.web.ApiSuccess
import org.epilink.bot.web.DummyCacheClient
import org.epilink.bot.web.UnsafeTestSessionStorage
import org.epilink.bot.web.setupSession
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeUserSessionChecker(scope: InjectionScope) : SessionChecker {
    private val db: DatabaseFacade by scope()

    override suspend fun verifyUser(context: PipelineContext<Unit, ApplicationCall>): Boolean {
        context.context.attributes.put(
            userObjAttribute,
            db.getUser(context.context.sessions.get<ConnectedSession>()!!.discordId)!!
        )
        return true
    }

    override suspend fun verifyAdmin(context: PipelineContext<Unit, ApplicationCall>): Boolean =
        error("Not implemented")
}

class UserTest : ShedinjaBaseTest<Unit>(
    Unit::class, {
        put<UserApi>(::UserApiImpl)
        put<BackEnd>(::BackEndImpl)
        put<SessionChecker>(::FakeUserSessionChecker)
        put<WebServerConfiguration> {
            mockk { every { rateLimitingProfile } returns RateLimitingProfile.Standard }
        }
        put(named("admins")) { listOf("adminid") }
    }
) {
    private val sessionStorage = UnsafeTestSessionStorage()

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user endpoint when identifiable`() = stest {
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
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user endpoint when not identifiable`() = stest {
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
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user endpoint when admin`() = stest {
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
        }
    }

    @Test
    fun `Test user access logs retrieval`() = stest {
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
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user identity relink with correct account`() = stest {
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
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user identity relink with account already linked`() = stest {
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
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user identity relink with relink error`() = stest {
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
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user identity deletion when no identity exists`() = stest {
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
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test user identity deletion success`() = stest {
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
        }
    }

    @Test
    fun `Test user log out`() = stest {
        withTestEpiLink {
            val sid = setupSession(sessionStorage)
            handleRequest(HttpMethod.Post, "/api/v1/user/logout") {
                addHeader("SessionId", sid)
            }.apply {
                assertStatus(HttpStatusCode.OK)
                assertNull(fromJson<ApiSuccess>(response).data)
                assertNull(sessions.get<ConnectedSession>())
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
                get<UserApi>().install(this)
            }
        }, block)
    }
}
