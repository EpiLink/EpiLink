package org.epilink.bot

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.*
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import org.epilink.bot.http.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.test.*

class MsftBackEndTest : KoinTest {
    @BeforeTest
    fun setupKoin() {
        startKoin {
            modules(module {
                single { LinkMicrosoftBackEnd("MsftClientId", "MsftSecret", "MsftTenant") }
            })
        }
    }

    @AfterTest
    fun tearDownKoin() {
        stopKoin()
    }

    @Test
    fun `Test Microsoft auth stub`() {
        val mbe = get<LinkMicrosoftBackEnd>()
        mbe.getAuthorizeStub().apply {
            assertTrue(contains("/MsftTenant/"), "Expected the tenant to be set")
            assertTrue(contains("client_id=MsftClientId"), "Expected a client ID")
            assertTrue(contains("scope=User.Read"), "Expected the scope to be set to User.Read")
            assertTrue(contains("response_type=code"), "Expected the response type to be code")
            assertTrue(contains(Regex("scope=User\\.Read(&|$)")), "Expected User.Read to be the only scope")
            assertTrue(contains("prompt=select_account"), "Expected prompt to be set to select_account")
            assertFalse(contains("redirect_uri"), "Expected redirect_uri to be absent")
        }
    }

    @Test
    fun `Test Microsoft token retrieval`() {
        declareClientHandler(onlyMatchUrl = "https://login.microsoftonline.com/MsftTenant/oauth2/v2.0/token") { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals(ContentType.Application.FormUrlEncoded, request.body.contentType)
            @OptIn(KtorExperimentalAPI::class)
            val params = String(request.body.toByteArray()).parseUrlEncodedParameters()
            assertEquals("MsftClientId", params["client_id"])
            assertEquals("MsftSecret", params["client_secret"])
            assertEquals("authorization_code", params["grant_type"])
            assertEquals("MsftAuthCode", params["code"])
            assertEquals("redir", params["redirect_uri"])
            assertEquals("User.Read", params["scope"])
            respond(
                """{"access_token":"MsftAccessToken"}""",
                headers = headersOf("Content-Type", "application/json")
            )
        }

        val mbe = get<LinkMicrosoftBackEnd>()
        runBlocking {
            assertEquals("MsftAccessToken", mbe.getMicrosoftToken("MsftAuthCode", "redir"))
        }
    }

    @Test
    fun `Test Microsoft token retrieval fails on wrong authcode`() {
        declareClientHandler(onlyMatchUrl = "https://login.microsoftonline.com/MsftTenant/oauth2/v2.0/token") {
            respondError(
                HttpStatusCode.BadRequest,
                """{"error":"invalid_grant"}""",
                headers = headersOf("Content-Type", "application/json")
            )
        }

        runBlocking {
            val mbe = get<LinkMicrosoftBackEnd>()
            val exc = assertFailsWith<LinkEndpointException> {
                mbe.getMicrosoftToken("Authcode", "Redir")
            }
            assertEquals(StandardErrorCodes.InvalidAuthCode, exc.errorCode)
        }
    }

    @Test
    fun `Test Microsoft token retrieval fails on other error`() {
        declareClientHandler(onlyMatchUrl = "https://discordapp.com/api/v6/oauth2/token") {
            respondError(HttpStatusCode.BadRequest, """{"error":"¯\\_(ツ)_/¯"}""")
        }

        runBlocking {
            val mbe = get<LinkMicrosoftBackEnd>()
            val exc = assertFailsWith<LinkEndpointException> {
                mbe.getMicrosoftToken("Auth", "Re")
            }
            assertEquals(StandardErrorCodes.MicrosoftApiFailure, exc.errorCode)
        }
    }

    @Test
    fun `Test Microsoft info retrieval mail`() {
        declareClientHandler(onlyMatchUrl = "https://graph.microsoft.com/v1.0/me") { req ->
            assertEquals("Bearer veryserioustoken", req.headers["Authorization"])
            val data = mapOf(
                "id" to "myMsftId",
                "mail" to "mymail@yes.to"
            )
            @Suppress("BlockingMethodInNonBlockingContext")
            respond(
                jacksonObjectMapper().writeValueAsString(data),
                headers = headersOf("Content-Type", "application/json")
            )
        }

        runBlocking {
            val mbe = get<LinkMicrosoftBackEnd>()
            assertEquals(
                MicrosoftUserInfo("myMsftId", "mymail@yes.to"),
                mbe.getMicrosoftInfo("veryserioustoken")
            )
        }
    }

    @Test
    fun `Test Microsoft info retrieval principal name`() {
        declareClientHandler(onlyMatchUrl = "https://graph.microsoft.com/v1.0/me") { req ->
            assertEquals("Bearer veryserioustoken", req.headers["Authorization"])
            val data = mapOf(
                "id" to "myMsftId",
                "userPrincipalName" to "mymail@yes.to"
            )
            @Suppress("BlockingMethodInNonBlockingContext")
            respond(
                jacksonObjectMapper().writeValueAsString(data),
                headers = headersOf("Content-Type", "application/json")
            )
        }

        runBlocking {
            val mbe = get<LinkMicrosoftBackEnd>()
            assertEquals(
                MicrosoftUserInfo("myMsftId", "mymail@yes.to"),
                mbe.getMicrosoftInfo("veryserioustoken")
            )
        }
    }

    @Test
    fun `Test Microsoft info retrieval no mail`() {
        declareClientHandler(onlyMatchUrl = "https://graph.microsoft.com/v1.0/me") { req ->
            assertEquals("Bearer veryserioustoken", req.headers["Authorization"])
            val data = mapOf(
                "id" to "myMsftId"
            )
            @Suppress("BlockingMethodInNonBlockingContext")
            respond(
                jacksonObjectMapper().writeValueAsString(data),
                headers = headersOf("Content-Type", "application/json")
            )
        }

        runBlocking {
            val mbe = get<LinkMicrosoftBackEnd>()
            val exc = assertFailsWith<LinkEndpointException> {
                mbe.getMicrosoftInfo("veryserioustoken")
            }
            assertEquals(StandardErrorCodes.AccountHasNoEmailAddress, exc.errorCode)
        }
    }

    @Test
    fun `Test Microsoft info retrieval no id`() {
        declareClientHandler(onlyMatchUrl = "https://graph.microsoft.com/v1.0/me") { req ->
            assertEquals("Bearer veryserioustoken", req.headers["Authorization"])
            val data = mapOf(
                "mail" to "mymail@yes.to"
            )
            @Suppress("BlockingMethodInNonBlockingContext")
            respond(
                jacksonObjectMapper().writeValueAsString(data),
                headers = headersOf("Content-Type", "application/json")
            )
        }

        runBlocking {
            val mbe = get<LinkMicrosoftBackEnd>()
            val exc = assertFailsWith<LinkEndpointException> {
                mbe.getMicrosoftInfo("veryserioustoken")
            }
            assertEquals(StandardErrorCodes.AccountHasNoId, exc.errorCode)
        }
    }
}
