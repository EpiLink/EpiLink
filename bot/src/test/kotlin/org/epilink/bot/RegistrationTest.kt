package org.epilink.bot

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.epilink.bot.db.Allowed
import org.epilink.bot.db.Disallowed
import org.epilink.bot.db.LinkServerDatabase
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.discord.LinkRoleManager
import org.epilink.bot.http.*
import org.epilink.bot.http.endpoints.LinkRegistrationApi
import org.epilink.bot.http.endpoints.LinkRegistrationApiImpl
import org.epilink.bot.http.sessions.ConnectedSession
import org.epilink.bot.http.sessions.RegisterSession
import org.koin.dsl.module
import org.koin.test.get
import kotlin.test.*

class RegistrationTest : KoinBaseTest(
    module {
        // TODO just don't depend on LinkBackEnd -- that requires separating feature installation somewhere
        //      else
        single<LinkBackEnd> { LinkBackEndImpl() }
        single<LinkRegistrationApi> { LinkRegistrationApiImpl() }
        single<CacheClient> { MemoryCacheClient() }
    }
) {
    @Test
    fun `Test Microsoft account authcode registration`() {
        mockHere<LinkMicrosoftBackEnd> {
            coEvery { getMicrosoftToken("fake mac", "fake mur") } returns "fake mtk"
            coEvery { getMicrosoftInfo("fake mtk") } returns MicrosoftUserInfo("fakeguid", "fakemail")
        }
        mockHere<LinkServerDatabase> {
            coEvery { isMicrosoftUserAllowedToCreateAccount(any(), any()) } returns Allowed
        }
        withTestEpiLink {
            val call = handleRequest(HttpMethod.Post, "/api/v1/register/authcode/msft") {
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
            assertEquals(RegisterSession(microsoftUid = "fakeguid", email = "fakemail"), session)
        }
    }

    @Test
    fun `Test Microsoft account authcode registration when disallowed`() {
        mockHere<LinkMicrosoftBackEnd> {
            coEvery { getMicrosoftToken("fake mac", "fake mur") } returns "fake mtk"
            coEvery { getMicrosoftInfo("fake mtk") } returns MicrosoftUserInfo("fakeguid", "fakemail")
        }
        mockHere<LinkServerDatabase> {
            coEvery { isMicrosoftUserAllowedToCreateAccount(any(), any()) } returns Disallowed("Cheh dans ta tronche")
        }
        withTestEpiLink {
            val call = handleRequest(HttpMethod.Post, "/api/v1/register/authcode/msft") {
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
        mockHere<LinkServerDatabase> {
            coEvery { isDiscordUserAllowedToCreateAccount(any()) } returns Allowed
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
        mockHere<LinkServerDatabase> {
            coEvery { getUser("yes") } returns mockk { every { discordId } returns "yes" }
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
            // Check that a session was set
            val session = call.sessions.get<ConnectedSession>()
            assertEquals(
                ConnectedSession(discordId = "yes", discordUsername = "no", discordAvatar = "maybe"),
                session
            )
            // Check that a SessionId header is present
            assertTrue(call.response.headers.contains("SessionId"))
        }
    }

    @Test
    fun `Test Discord account authcode registration when disallowed`() {
        mockHere<LinkDiscordBackEnd> {
            coEvery { getDiscordToken("fake auth", "fake uri") } returns "fake yeet"
            coEvery { getDiscordInfo("fake yeet") } returns DiscordUserInfo("yes", "no", "maybe")
        }
        mockHere<LinkServerDatabase> {
            coEvery { getUser("yes") } returns null
            coEvery { isDiscordUserAllowedToCreateAccount(any()) } returns Disallowed("Cheh dans ta tête")
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
        mockHere<LinkMicrosoftBackEnd> {
            coEvery { getMicrosoftToken("fake mac", "fake mur") } returns "fake mtk"
            coEvery { getMicrosoftInfo("fake mtk") } returns MicrosoftUserInfo("fakeguid", "fakemail")
        }
        val db = mockHere<LinkServerDatabase> {
            coEvery { getUser("yes") } answers { if (userCreated) mockk() else null }
            coEvery { isDiscordUserAllowedToCreateAccount(any()) } returns Allowed
            coEvery { isMicrosoftUserAllowedToCreateAccount(any(), any()) } returns Allowed
            coEvery { createUser(any(), any(), any(), any()) } answers {
                userCreated = true
                mockk { every { discordId } returns "yes" }
            }
        }
        val bot = mockHere<LinkRoleManager> {
            coEvery { invalidateAllRoles(any()) } returns mockk()
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
            handleRequest(HttpMethod.Post, "/api/v1/register/authcode/msft") {
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
            val loginHeader = handleRequest(HttpMethod.Post, "/api/v1/register") {
                addHeader("RegistrationSessionId", regHeader)
                setJsonBody("""{"keepIdentity": true}""")
            }.run {
                assertStatus(HttpStatusCode.Created)
                assertNull(sessions.get<RegisterSession>())
                assertEquals(ConnectedSession("yes", "no", "maybe"), sessions.get<ConnectedSession>())
                response.headers["SessionId"]!!
            }
            coVerify { db.createUser("yes", "fakeguid", "fakemail", true) }
            // Simulate the DB knowing about the new user
            mockHere<LinkServerDatabase> {
                coEvery { getUser("yes") } returns mockk { every { discordId } returns "yes" }
                coEvery { isUserIdentifiable("yes") } returns true
            }
            // TODO get rid of this part because it's a dependency on the back-end/user endpoints
            handleRequest(HttpMethod.Get, "/api/v1/user") {
                addHeader("SessionId", loginHeader)
            }.apply {
                assertStatus(HttpStatusCode.OK)
                // Only checks that it was logged in properly. The results of /api/v1/user are tested elsewhere
                assertTrue { this.response.content!!.contains("yes") }
            }
            coVerify { bot.invalidateAllRoles(any()) }
        }
    }

    private fun withTestEpiLink(block: TestApplicationEngine.() -> Unit) =
        withTestApplication({
            with(get<LinkBackEnd>()) {
                epilinkApiModule()
            }
        }, block)
}