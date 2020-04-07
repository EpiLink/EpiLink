package org.epilink.bot

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import org.epilink.bot.http.DiscordUserInfo
import org.epilink.bot.http.LinkDiscordBackEnd
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.test.mock.declare
import kotlin.test.*

class DiscordBackEndTest : KoinTest {
    @BeforeTest
    fun setupKoin() {
        startKoin {
            modules(module {
                single { LinkDiscordBackEnd("DiscordClientId", "DiscordSecret") }
            })
        }
    }

    @AfterTest
    fun tearDownKoin() {
        stopKoin()
    }

    @Test
    fun `Test Discord auth stub`() {
        val dbe = get<LinkDiscordBackEnd>()
        dbe.getAuthorizeStub().apply {
            assertTrue(contains("client_id=DiscordClientId"), "Expected a client ID")
            assertTrue(contains("scope=identify"), "Expected the scope to be set to identify")
            assertTrue(contains("response_type=code"), "Expected the response type to be code")
            assertTrue(contains(Regex("scope=identify[&$]")), "Expected identify to be the only scope")
            assertTrue(contains("prompt=consent"), "Expected prompt to be set to consent")
            assertFalse(contains("redirect_uri"), "Expected redirect_uri to be absent")
        }
    }

    @Test
    fun `Test Discord token retrieval`() {
        declareClientHandler(onlyMatchUrl = "https://discordapp.com/api/v6/oauth2/token") { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals(ContentType.Application.FormUrlEncoded, request.body.contentType)
            @OptIn(KtorExperimentalAPI::class)
            val params = String(request.body.toByteArray()).parseUrlEncodedParameters()
            assertEquals("DiscordClientId", params["client_id"])
            assertEquals("DiscordSecret", params["client_secret"])
            assertEquals("authorization_code", params["grant_type"])
            assertEquals("DiscordAuthCode", params["code"])
            assertEquals("redir", params["redirect_uri"])
            assertEquals("identify", params["scope"])
            respond(
                """{"access_token":"DiscordAccessToken"}""",
                headers = headersOf("Content-Type", "application/json")
            )
        }

        val dbe = get<LinkDiscordBackEnd>()
        runBlocking {
            assertEquals("DiscordAccessToken", dbe.getDiscordToken("DiscordAuthCode", "redir"))
        }
    }

    @Test
    fun `Test Discord token retrieval fails on wrong authcode`() {
        declareClientHandler(onlyMatchUrl = "https://discordapp.com/api/v6/oauth2/token") {
            respondError(
                HttpStatusCode.BadRequest,
                """{"error":"invalid_grant"}""",
                headers = headersOf("Content-Type", "application/json")
            )
        }

        runBlocking {
            val dbe = get<LinkDiscordBackEnd>()
            val exc = assertFailsWith<LinkEndpointException> {
                dbe.getDiscordToken("Authcode", "Redir")
            }
            assertEquals(StandardErrorCodes.InvalidAuthCode, exc.errorCode)
        }
    }

    @Test
    fun `Test Discord token retrieval fails on other error`() {
        declareClientHandler(onlyMatchUrl = "https://discordapp.com/api/v6/oauth2/token") {
            respondError(HttpStatusCode.BadRequest, """{"error":"¯\\_(ツ)_/¯"}""")
        }

        runBlocking {
            val dbe = get<LinkDiscordBackEnd>()
            val exc = assertFailsWith<LinkEndpointException> {
                dbe.getDiscordToken("Auth", "Re")
            }
            assertEquals(StandardErrorCodes.DiscordApiFailure, exc.errorCode)
        }
    }

    @Test
    fun `Test Discord info retrieval`() {
        declareClientHandler(onlyMatchUrl = "https://discordapp.com/api/v6/users/@me") { req ->
            assertEquals("Bearer veryserioustoken", req.headers["Authorization"])
            val data = mapOf(
                "id" to "myVeryId",
                "username" to "usseeer",
                "discriminator" to "9876",
                "avatar" to "haaash"
            )
            @Suppress("BlockingMethodInNonBlockingContext")
            respond(
                jacksonObjectMapper().writeValueAsString(data),
                headers = headersOf("Content-Type", "application/json")
            )
        }

        runBlocking {
            val dbe = get<LinkDiscordBackEnd>()
            assertEquals(
                DiscordUserInfo(
                    id = "myVeryId",
                    username = "usseeer#9876",
                    avatarUrl = "https://cdn.discordapp.com/avatars/myVeryId/haaash.png?size=256"
                ),
                dbe.getDiscordInfo("veryserioustoken")
            )
        }
    }

    private fun declareClientHandler(onlyMatchUrl: String? = null, handler: MockRequestHandler): HttpClient =
        declare {
            HttpClient(MockEngine) {
                engine {
                    addHandler(onlyMatchUrl?.let<String, MockRequestHandler> {
                        { request ->
                            when (request.url.fullUrl) {
                                onlyMatchUrl -> handler(request)
                                else -> error("Url ${request.url.fullUrl} does not match expected URL $onlyMatchUrl")
                            }
                        }
                    } ?: handler)
                }
            }
        }
}

// From https://ktor.io/clients/http-client/testing.html
private val Url.hostWithPortIfRequired: String get() = if (port == protocol.defaultPort) host else hostWithPort
private val Url.fullUrl: String get() = "${protocol.name}://$hostWithPortIfRequired$fullPath"