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
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.mockk.*
import org.epilink.bot.db.*
import org.epilink.bot.discord.LinkRoleManager
import org.epilink.bot.http.*
import org.epilink.bot.http.endpoints.LinkRegistrationApi
import org.epilink.bot.http.endpoints.LinkRegistrationApiImpl
import org.epilink.bot.http.endpoints.LinkUserApi
import org.epilink.bot.http.sessions.RegisterSession
import org.koin.dsl.module
import org.koin.test.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RegistrationTest : KoinBaseTest<Unit>(
    Unit::class,
    module {
        single<LinkBackEnd> { LinkBackEndImpl() }
        single<LinkRegistrationApi> { LinkRegistrationApiImpl() }
        single<CacheClient> { MemoryCacheClient() }
    }
) {
    @Test
    fun `Test Microsoft account authcode registration`() {
        mockHere<LinkIdentityProvider> {
            coEvery { getUserIdentityInfo("fake mac", "fake mur") } returns UserIdentityInfo("fakeguid", "fakemail")
        }
        mockHere<LinkPermissionChecks> {
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
        mockHere<LinkIdentityProvider> {
            coEvery { getUserIdentityInfo("fake mac", "fake mur") } returns UserIdentityInfo("fakeguid", "fakemail")
        }
        mockHere<LinkPermissionChecks> {
            coEvery { isIdentityProviderUserAllowedToCreateAccount(any(), any()) } returns Disallowed("Cheh dans ta tronche", "ch.eh")
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
    fun `Test Discord account authcode registration account does not exist`() {
        mockHere<LinkDiscordBackEnd> {
            coEvery { getDiscordToken("fake auth", "fake uri") } returns "fake yeet"
            coEvery { getDiscordInfo("fake yeet") } returns DiscordUserInfo("yes", "no", "maybe")
        }
        mockHere<LinkPermissionChecks> {
            coEvery { isDiscordUserAllowedToCreateAccount(any()) } returns Allowed
        }
        mockHere<LinkDatabaseFacade> {
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
        mockHere<LinkDiscordBackEnd> {
            coEvery { getDiscordToken("fake auth", "fake uri") } returns "fake yeet"
            coEvery { getDiscordInfo("fake yeet") } returns DiscordUserInfo("yes", "no", "maybe")
        }
        mockHere<LinkDatabaseFacade> {
            coEvery { getUser("yes") } returns mockk { every { discordId } returns "yes" }
        }
        val lua = mockHere<LinkUserApi> {
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
        mockHere<LinkDiscordBackEnd> {
            coEvery { getDiscordToken("fake auth", "fake uri") } returns "fake yeet"
            coEvery { getDiscordInfo("fake yeet") } returns DiscordUserInfo("yes", "no", "maybe")
        }
        mockHere<LinkPermissionChecks> {
            coEvery { isDiscordUserAllowedToCreateAccount(any()) } returns Disallowed("Cheh dans ta tête", "ch.eh2")
        }
        mockHere<LinkDatabaseFacade> {
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
    fun `Test registration session deletion`() {
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
    fun `Test full registration sequence, discord then msft`() {
        var userCreated = false
        mockHere<LinkDiscordBackEnd> {
            coEvery { getDiscordToken("fake auth", "fake uri") } returns "fake yeet"
            coEvery { getDiscordInfo("fake yeet") } returns DiscordUserInfo("yes", "no", "maybe")
        }
        mockHere<LinkIdentityProvider> {
            coEvery { getUserIdentityInfo("fake mac", "fake mur") } returns UserIdentityInfo("fakeguid", "fakemail")
        }
        mockHere<LinkPermissionChecks> {
            coEvery { isDiscordUserAllowedToCreateAccount(any()) } returns Allowed
            coEvery { isIdentityProviderUserAllowedToCreateAccount(any(), any()) } returns Allowed
        }
        mockHere<LinkDatabaseFacade> {
            coEvery { getUser("yes") } answers { if (userCreated) mockk() else null }
        }
        val uc = mockHere<LinkUserCreator> {
            coEvery { createUser(any(), any(), any(), any()) } answers {
                userCreated = true
                mockk { every { discordId } returns "yes" }
            }
        }
        val bot = mockHere<LinkRoleManager> {
            coEvery { invalidateAllRoles(any(), true) } returns mockk()
        }
        val lua = mockHere<LinkUserApi> {
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
                bot.invalidateAllRoles(any(), true)
            }
        }
    }

    private fun withTestEpiLink(block: TestApplicationEngine.() -> Unit) =
        withTestApplication({
            with(get<LinkBackEnd>()) { installFeatures() }
            routing { get<LinkRegistrationApi>().install(this) }
        }, block)
}