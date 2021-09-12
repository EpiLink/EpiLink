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
import guru.zoroark.shedinja.environment.get
import guru.zoroark.shedinja.test.ShedinjaBaseTest
import guru.zoroark.shedinja.test.UnsafeMutableEnvironment
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.epilink.bot.CacheClient
import org.epilink.bot.MemoryCacheClient
import org.epilink.bot.assertStatus
import org.epilink.bot.config.RateLimitingProfile
import org.epilink.bot.config.WebServerConfiguration
import org.epilink.bot.db.Allowed
import org.epilink.bot.db.DatabaseFacade
import org.epilink.bot.db.Disallowed
import org.epilink.bot.db.PermissionChecks
import org.epilink.bot.db.UserCreator
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.discord.RoleManager
import org.epilink.bot.fromJson
import org.epilink.bot.getMap
import org.epilink.bot.getString
import org.epilink.bot.http.BackEnd
import org.epilink.bot.http.BackEndImpl
import org.epilink.bot.http.DiscordBackEnd
import org.epilink.bot.http.DiscordUserInfo
import org.epilink.bot.http.IdentityProvider
import org.epilink.bot.http.UserIdentityInfo
import org.epilink.bot.http.endpoints.RegistrationApi
import org.epilink.bot.http.endpoints.RegistrationApiImpl
import org.epilink.bot.http.endpoints.UserApi
import org.epilink.bot.http.sessions.RegisterSession
import org.epilink.bot.putMock
import org.epilink.bot.setJsonBody
import org.epilink.bot.stest
import org.epilink.bot.web.ApiError
import org.epilink.bot.web.ApiSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RegistrationTest : ShedinjaBaseTest<Unit>(
    Unit::class, {
        put<BackEnd>(::BackEndImpl)
        put<RegistrationApi>(::RegistrationApiImpl)
        put<CacheClient>(::MemoryCacheClient)
        put<WebServerConfiguration> {
            mockk { every { rateLimitingProfile } returns RateLimitingProfile.Standard }
        }
    }
) {
    @Test
    fun `Test Microsoft account authcode registration`() = stest {
        putMock<IdentityProvider> {
            coEvery { getUserIdentityInfo("fake mac", "fake mur") } returns UserIdentityInfo("fakeguid", "fakemail")
        }
        putMock<PermissionChecks> {
            coEvery { isIdentityProviderUserAllowedToCreateAccount(any(), any()) } returns Allowed
        }
        withTestEpiLink {
            val call = handleRequest(HttpMethod.Post, "/api/v1/register/authcode/idProvider") {
                setJsonBody("""{"code":"fake mac","redirectUri":"fake mur"}""")
            }
            call.assertStatus(HttpStatusCode.OK)
            // Check that the response is a "continue" response
            val data = fromJson<ApiSuccess>(call.response).data
            assertNotNull(data)
            assertEquals("continue", data.getString("next"))
            // Check that the returned info is what we expect
            val regInfo = data.getMap("attachment")
            assertEquals("fakemail", regInfo.getString("email"))
            assertEquals(null, regInfo.getValue("discordUsername"))
            assertEquals(null, regInfo.getValue("discordAvatarUrl"))
            // Check that a session was set
            val session = call.sessions.get<RegisterSession>()
            assertEquals(RegisterSession(idpId = "fakeguid", email = "fakemail"), session)
        }
    }

    @Test
    fun `Test Microsoft account authcode registration when disallowed`() = stest {
        putMock<IdentityProvider> {
            coEvery { getUserIdentityInfo("fake mac", "fake mur") } returns UserIdentityInfo("fakeguid", "fakemail")
        }
        putMock<PermissionChecks> {
            coEvery {
                isIdentityProviderUserAllowedToCreateAccount(any(), any())
            } returns Disallowed("Cheh dans ta tronche", "ch.eh")
        }
        withTestEpiLink {
            val call = handleRequest(HttpMethod.Post, "/api/v1/register/authcode/idProvider") {
                setJsonBody("""{"code":"fake mac","redirectUri":"fake mur"}""")
            }
            call.assertStatus(HttpStatusCode.BadRequest)
            val error = fromJson<ApiError>(call.response)
            assertEquals("Cheh dans ta tronche", error.message)
            assertEquals(101, error.data.code)
        }
    }

    @Test
    fun `Test Discord account authcode registration account does not exist`() = stest {
        putMock<DiscordBackEnd> {
            coEvery { getDiscordToken("fake auth", "fake uri") } returns "fake yeet"
            coEvery { getDiscordInfo("fake yeet") } returns DiscordUserInfo("yes", "no", "maybe")
        }
        putMock<PermissionChecks> {
            coEvery { isDiscordUserAllowedToCreateAccount(any()) } returns Allowed
        }
        putMock<DatabaseFacade> {
            coEvery { getUser("yes") } returns null
        }
        withTestEpiLink {
            val call = handleRequest(HttpMethod.Post, "/api/v1/register/authcode/discord") {
                setJsonBody("""{"code":"fake auth","redirectUri":"fake uri"}""")
            }
            call.assertStatus(HttpStatusCode.OK)
            // Check that the response is a "continue" response
            val data = fromJson<ApiSuccess>(call.response).data
            assertEquals("continue", data!!.getString("next"))
            // Check the returned info
            val regInfo = data.getMap("attachment")
            assertEquals(null, regInfo.getValue("email"))
            assertEquals("no", regInfo.getString("discordUsername"))
            assertEquals("maybe", regInfo.getString("discordAvatarUrl"))
            // Check that a session was set
            val session = call.sessions.get<RegisterSession>()
            assertEquals(
                RegisterSession(discordId = "yes", discordUsername = "no", discordAvatarUrl = "maybe"),
                session
            )
        }
    }

    @Test
    fun `Test Discord account authcode registration account already exists`() = stest {
        putMock<DiscordBackEnd> {
            coEvery { getDiscordToken("fake auth", "fake uri") } returns "fake yeet"
            coEvery { getDiscordInfo("fake yeet") } returns DiscordUserInfo("yes", "no", "maybe")
        }
        putMock<DatabaseFacade> {
            coEvery { getUser("yes") } returns mockk { every { discordId } returns "yes" }
        }
        val lua = putMock<UserApi> {
            every { loginAs(any(), any(), "no", "maybe") } just runs
        }
        withTestEpiLink {
            val call = handleRequest(HttpMethod.Post, "/api/v1/register/authcode/discord") {
                setJsonBody("""{"code":"fake auth","redirectUri":"fake uri"}""")
            }
            call.assertStatus(HttpStatusCode.OK)
            // Check that we are logged in
            val data = fromJson<ApiSuccess>(call.response).data
            assertEquals("login", data!!.getString("next"))
            assertEquals(null, data.getValue("attachment"))
            // Check that the log-in code was called
            verify { lua.loginAs(any(), any(), "no", "maybe") }
        }
    }

    @Test
    fun `Test Discord account authcode registration when disallowed`() = stest {
        putMock<DiscordBackEnd> {
            coEvery { getDiscordToken("fake auth", "fake uri") } returns "fake yeet"
            coEvery { getDiscordInfo("fake yeet") } returns DiscordUserInfo("yes", "no", "maybe")
        }
        putMock<PermissionChecks> {
            coEvery { isDiscordUserAllowedToCreateAccount(any()) } returns Disallowed("Cheh dans ta tête", "ch.eh2")
        }
        putMock<DatabaseFacade> {
            coEvery { getUser("yes") } returns null
        }
        withTestEpiLink {
            val call = handleRequest(HttpMethod.Post, "/api/v1/register/authcode/discord") {
                setJsonBody("""{"code":"fake auth","redirectUri":"fake uri"}""")
            }
            call.assertStatus(HttpStatusCode.BadRequest)
            val error = fromJson<ApiError>(call.response)
            assertEquals("Cheh dans ta tête", error.message)
            assertEquals(101, error.data.code)
        }
    }

    @Test
    fun `Test registration session deletion`() = stest {
        withTestEpiLink {
            val header = handleRequest(HttpMethod.Get, "/api/v1/register/info").run {
                assertStatus(HttpStatusCode.OK)
                assertNotNull(sessions.get<RegisterSession>())
                response.headers["RegistrationSessionId"]!!
            }
            handleRequest(HttpMethod.Delete, "/api/v1/register") {
                addHeader("RegistrationSessionId", header)
            }.apply {
                assertNull(sessions.get<RegisterSession>())
            }
        }
    }

    @Test
    @OptIn(UsesTrueIdentity::class)
    fun `Test full registration sequence, discord then msft`() = stest {
        var userCreated = false
        putMock<DiscordBackEnd> {
            coEvery { getDiscordToken("fake auth", "fake uri") } returns "fake yeet"
            coEvery { getDiscordInfo("fake yeet") } returns DiscordUserInfo("yes", "no", "maybe")
        }
        putMock<IdentityProvider> {
            coEvery { getUserIdentityInfo("fake mac", "fake mur") } returns UserIdentityInfo("fakeguid", "fakemail")
        }
        putMock<PermissionChecks> {
            coEvery { isDiscordUserAllowedToCreateAccount(any()) } returns Allowed
            coEvery { isIdentityProviderUserAllowedToCreateAccount(any(), any()) } returns Allowed
        }
        putMock<DatabaseFacade> {
            coEvery { getUser("yes") } answers { if (userCreated) mockk() else null }
        }
        val uc = putMock<UserCreator> {
            coEvery { createUser(any(), any(), any(), any()) } answers {
                userCreated = true
                mockk { every { discordId } returns "yes" }
            }
        }
        val bot = putMock<RoleManager> {
            coEvery { invalidateAllRolesLater(any(), true) } returns mockk()
        }
        val lua = putMock<UserApi> {
            every { loginAs(any(), any(), "no", "maybe") } just runs
        }
        withTestEpiLink {
            // Discord authentication
            val regHeader = handleRequest(HttpMethod.Post, "/api/v1/register/authcode/discord") {
                setJsonBody("""{"code":"fake auth","redirectUri":"fake uri"}""")
            }.run {
                assertStatus(HttpStatusCode.OK)
                val data = fromJson<ApiSuccess>(response).data
                assertNotNull(data)
                assertEquals("continue", data.getString("next"))
                assertEquals("no", data.getMap("attachment").getString("discordUsername"))
                response.headers["RegistrationSessionId"]!!
            }
            // Microsoft authentication
            handleRequest(HttpMethod.Post, "/api/v1/register/authcode/idProvider") {
                addHeader("RegistrationSessionId", regHeader)
                setJsonBody("""{"code":"fake mac","redirectUri":"fake mur"}""")
            }.apply {
                assertStatus(HttpStatusCode.OK)
                val data = fromJson<ApiSuccess>(response).data
                assertNotNull(data)
                assertEquals("continue", data.getString("next"))
                assertEquals("fakemail", data.getMap("attachment").getString("email"))
                assertEquals("no", data.getMap("attachment").getString("discordUsername"))
            }
            // Create the account
            handleRequest(HttpMethod.Post, "/api/v1/register") {
                addHeader("RegistrationSessionId", regHeader)
                setJsonBody("""{"keepIdentity": true}""")
            }.apply {
                assertStatus(HttpStatusCode.Created)
                verify { lua.loginAs(any(), any(), "no", "maybe") }
            }
            coVerify {
                uc.createUser("yes", "fakeguid", "fakemail", true)
                bot.invalidateAllRolesLater(any(), true)
            }
        }
    }

    private fun UnsafeMutableEnvironment.withTestEpiLink(block: TestApplicationEngine.() -> Unit) =
        withTestApplication({
            with(get<BackEnd>()) { installFeatures() }
            routing { get<RegistrationApi>().install(this) }
        }, block)
}
