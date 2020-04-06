package org.epilink.bot

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.*
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.epilink.bot.db.Allowed
import org.epilink.bot.db.Disallowed
import org.epilink.bot.db.LinkServerDatabase
import org.epilink.bot.http.*
import org.epilink.bot.http.sessions.RegisterSession
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.test.mock.declare
import kotlin.test.*

data class ApiSuccess(
    val success: Boolean,
    val message: String?,
    val data: Map<String, Any?>
) {
    init {
        assertTrue(success)
    }
}

data class ApiError(
    val success: Boolean,
    val message: String,
    val data: ApiErrorDetails
) {
    init {
        assertFalse(success)
    }
}

data class ApiErrorDetails(
    val code: Int,
    val description: String
)

class BackEndTest : KoinTest {
    @BeforeTest
    fun setupKoin() {
        startKoin {
            modules(module {
                single<LinkBackEnd> { LinkBackEndImpl() }
                single<SessionStorageProvider> { MemoryStorageProvider() }
            })
        }
    }

    @AfterTest
    fun tearDownKoin() {
        stopKoin()
    }

    @Test
    fun `Test meta information gathering`() {
        mockHere<LinkServerEnvironment> {
            every { name } returns "EpiLink Test Instance"
        }
        mockHere<LinkDiscordBackEnd> {
            every { getAuthorizeStub() } returns "I am a Discord authorize stub"
        }
        mockHere<LinkMicrosoftBackEnd> {
            every { getAuthorizeStub() } returns "I am a Microsoft authorize stub"
        }
        withTestEpiLink {
            val call = handleRequest(HttpMethod.Get, "/api/v1/meta/info")
            call.assertStatus(HttpStatusCode.OK)
            val data = fromJson<ApiSuccess>(call.response).data
            assertEquals("EpiLink Test Instance", data["title"])
            assertEquals(null, data.getValue("logo"))
            assertEquals("I am a Discord authorize stub", data.getString("authorizeStub_discord"))
            assertEquals("I am a Microsoft authorize stub", data.getString("authorizeStub_msft"))
        }
    }

    @Test
    fun `Test Microsoft account authcode registration`() {
        mockHere<LinkMicrosoftBackEnd> {
            coEvery { getMicrosoftToken("fake mac", "fake mur")} returns "fake mtk"
            coEvery { getMicrosoftInfo("fake mtk") } returns MicrosoftUserInfo("fakeguid", "fakemail")
        }
        mockHere<LinkServerDatabase> {
            coEvery { isAllowedToCreateAccount(any(), any()) } returns Allowed
        }
        withTestEpiLink {
            val call = handleRequest(HttpMethod.Post, "/api/v1/register/authcode/msft") {
                setJsonBody("""{"code":"fake mac","redirectUri":"fake mur"}""")
            }
            call.assertStatus(HttpStatusCode.OK)
            val data = fromJson<ApiSuccess>(call.response).data
            assertEquals("continue", data.getString("next"))
            val regInfo = data.getMap("attachment")
            assertEquals("fakemail", regInfo.getString("email"))
            assertEquals(null, regInfo.getValue("discordUsername"))
            assertEquals(null, regInfo.getValue("discordAvatarUrl"))
            val session = call.sessions.get<RegisterSession>()
            assertEquals(RegisterSession(microsoftUid = "fakeguid", email = "fakemail"), session)
        }
    }

    @Test
    fun `Test Microsoft account authcode registration when disallowed`() {
        mockHere<LinkMicrosoftBackEnd> {
            coEvery { getMicrosoftToken("fake mac", "fake mur")} returns "fake mtk"
            coEvery { getMicrosoftInfo("fake mtk") } returns MicrosoftUserInfo("fakeguid", "fakemail")
        }
        mockHere<LinkServerDatabase> {
            coEvery { isAllowedToCreateAccount(any(), any()) } returns Disallowed("Cheh dans ta tronche")
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
    private fun TestApplicationRequest.setJsonBody(json: String) {
        addHeader("Content-Type", "application/json")
        setBody(json)
    }

    private fun withTestEpiLink(block: TestApplicationEngine.() -> Unit) =
        withTestApplication({
            with(get<LinkBackEnd>()) {
                epilinkApiModule()
            }
        }, block)

    private inline fun <reified T : Any> mockHere(crossinline body: T.() -> Unit) : T =
        declare { mockk(block = body) }
}

private fun TestApplicationCall.assertStatus(status: HttpStatusCode) {
    assertEquals(status, this.response.status())
}

private fun Map<String, Any?>.getString(key: String): String =
    this.getValue(key) as String

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.getMap(key: String): Map<String, Any?> =
    this.getValue(key) as Map<String, Any?>

inline fun <reified T> fromJson(response: TestApplicationResponse): T {
    return jacksonObjectMapper().readValue(response.content!!)
}