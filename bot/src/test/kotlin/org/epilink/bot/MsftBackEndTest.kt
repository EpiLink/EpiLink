/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import io.mockk.mockk
import org.epilink.bot.http.LinkIdentityProvider
import org.koin.dsl.module

class MsftBackEndTest : KoinBaseTest(
    module {
        single { LinkIdentityProvider(mockk(), "MsftSecret", "MsftTenant") }
    }
) {
/*
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
                """{"id_token":"MsftAccessToken"}""",
                headers = headersOf("Content-Type", "application/json")
            )
        }

        val mbe = get<LinkMicrosoftBackEnd>()
        runBlocking {
            assertEquals("MsftAccessToken", mbe.getMicrosoftInfo("MsftAuthCode", "redir"))
        }
    }


    @Test
    fun `Test Microsoft info retrieval fails on wrong authcode`() {
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
                mbe.getMicrosoftInfo("Authcode", "Redir")
            }
            assertEquals(StandardErrorCodes.InvalidAuthCode, exc.errorCode)
        }
    }

    @Test
    fun `Test Microsoft token retrieval fails on other error`() {
        declareClientHandler(onlyMatchUrl = "https://login.microsoftonline.com/MsftTenant/oauth2/v2.0/token") {
            respondError(HttpStatusCode.BadRequest, """{"error":"¯\\_(ツ)_/¯"}""")
        }

        runBlocking {
            val mbe = get<LinkMicrosoftBackEnd>()
            val exc = assertFailsWith<LinkEndpointException> {
                mbe.getMicrosoftToken("Auth", "Re")
            }
            assertEquals(StandardErrorCodes.IdentityProviderApiFailure, exc.errorCode)
            assertTrue(exc.message!!.contains("¯\\_(ツ)_/¯"), "Message ${exc.message!!} does not contain error name")
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
    }*/
}
