/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.user

import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.sessions.*
import io.mockk.*
import org.epilink.bot.*
import org.epilink.bot.config.RateLimitingProfile
import org.epilink.bot.config.WebServerConfiguration
import org.epilink.bot.db.*
import org.epilink.bot.discord.RoleManager
import org.epilink.bot.http.*
import org.epilink.bot.http.endpoints.RegistrationApi
import org.epilink.bot.http.endpoints.RegistrationApiImpl
import org.epilink.bot.http.endpoints.UserApi
import org.epilink.bot.http.sessions.RegisterSession
import org.epilink.bot.web.ApiError
import org.epilink.bot.web.ApiSuccess
import org.koin.dsl.module
import org.koin.test.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RegistrationTest : KoinBaseTest<Unit>(
    Unit::class,
    module {
        single<BackEnd> { BackEndImpl() }
        single<RegistrationApi> { RegistrationApiImpl() }
        single<CacheClient> { MemoryCacheClient() }
        single<WebServerConfiguration> {
            mockk { every { rateLimitingProfile } returns RateLimitingProfile.Standard }
        }
    }
) {
    @Test
    fun `Test Microsoft account authcode registration`() {
        mockHere<IdentityProvider> {
            coEvery { getUserIdentityInfo("fake mac", "fake mur") } returns UserIdentityInfo("fakeguid", "fakemail")
        }
        mockHere<PermissionChecks> {
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
    fun `Test Microsoft account authcode registration when disallowed`() {
        mockHere<IdentityProvider> {
            coEvery { getUserIdentityInfo("fake mac", "fake mur") } returns UserIdentityInfo("fakeguid", "fakemail")
        }
        mockHere<PermissionChecks> {
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

    private fun DiscordBackEnd.fakeDiscordInfo(
        token: String = "fake yeet",
        id: String = "yes",
        username: String = "no",
        avatarUrl: String = "maybe",
        presentOnServers: Boolean = true
    ) =
        coEvery { getDiscordInfo(token) } returns DiscordUserInfo(id, username, avatarUrl, true)

    private fun DiscordBackEnd.fakeDiscordToken(authcode: String = "fake auth", redirectUri: String = "fake uri") =
        coEvery { getDiscordToken(authcode, redirectUri) } returns "fake yeet"

    @Test
    fun `Test Discord account authcode registration account does not exist`() {
        mockHere<DiscordBackEnd> {
            fakeDiscordInfo()
            fakeDiscordToken()
        }
        mockHere<PermissionChecks> {
            coEvery { isDiscordUserAllowedToCreateAccount(any()) } returns Allowed
        }
        mockHere<DatabaseFacade> {
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
    fun `Test Discord account authcode registration account already exists`() {
        mockHere<DiscordBackEnd> {
            fakeDiscordToken()
            fakeDiscordInfo()
        }
        mockHere<DatabaseFacade> {
            coEvery { getUser("yes") } returns mockk { every { discordId } returns "yes" }
        }
        val lua = mockHere<UserApi> {
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
    fun `Test Discord account authcode registration when disallowed`() {
        mockHere<DiscordBackEnd> {
            fakeDiscordToken()
            fakeDiscordInfo()
        }
        mockHere<PermissionChecks> {
            coEvery { isDiscordUserAllowedToCreateAccount(any()) } returns Disallowed("Cheh dans ta tête", "ch.eh2")
        }
        mockHere<DatabaseFacade> {
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
    fun `Test registration session deletion`() = withTestEpiLink {
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

    @Test
    @OptIn(UsesTrueIdentity::class)
    fun `Test full registration sequence, discord then msft`() {
        var userCreated = false
        mockHere<DiscordBackEnd> {
            fakeDiscordInfo()
            fakeDiscordToken()
        }
        mockHere<IdentityProvider> {
            coEvery { getUserIdentityInfo("fake mac", "fake mur") } returns UserIdentityInfo("fakeguid", "fakemail")
        }
        mockHere<PermissionChecks> {
            coEvery { isDiscordUserAllowedToCreateAccount(any()) } returns Allowed
            coEvery { isIdentityProviderUserAllowedToCreateAccount(any(), any()) } returns Allowed
        }
        mockHere<DatabaseFacade> {
            coEvery { getUser("yes") } answers { if (userCreated) mockk() else null }
        }
        val uc = mockHere<UserCreator> {
            coEvery { createUser(any(), any(), any(), any()) } answers {
                userCreated = true
                mockk { every { discordId } returns "yes" }
            }
        }
        val bot = mockHere<RoleManager> {
            coEvery { invalidateAllRolesLater(any(), true) } returns mockk()
        }
        val lua = mockHere<UserApi> {
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

    private fun withTestEpiLink(block: TestApplicationEngine.() -> Unit) =
        withTestApplication({
            with(get<BackEnd>()) { installFeatures() }
            routing { get<RegistrationApi>().install(this) }
        }, block)
}
