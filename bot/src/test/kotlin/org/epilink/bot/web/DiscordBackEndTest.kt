/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.*
import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.epilink.bot.*
import org.epilink.bot.config.DiscordConfiguration
import org.epilink.bot.config.DiscordServerSpec
import org.epilink.bot.discord.DiscordClientFacade
import org.epilink.bot.http.DiscordUserInfo
import org.epilink.bot.http.DiscordBackEnd
import org.koin.dsl.module
import kotlin.test.*

class DiscordBackEndTest : KoinBaseTest<DiscordBackEnd>(
    DiscordBackEnd::class,
    module {
        single { DiscordBackEnd("DiscordClientId", "DiscordSecret") }
    }
) {
    @Test
    fun `Test Discord auth stub`() = test {
        getAuthorizeStub().apply {
            assertTrue(contains("client_id=DiscordClientId"), "Expected a client ID")
            assertTrue(contains("scope=identify"), "Expected the scope to be set to identify")
            assertTrue(contains("response_type=code"), "Expected the response type to be code")
            assertTrue(contains(Regex("scope=identify(&|$)")), "Expected identify to be the only scope")
            assertTrue(contains("prompt=consent"), "Expected prompt to be set to consent")
            assertFalse(contains("redirect_uri"), "Expected redirect_uri to be absent")
        }
    }


    @Test
    fun `Test Discord token retrieval`() {
        declareClientHandler(onlyMatchUrl = "https://discord.com/api/v6/oauth2/token") { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals(ContentType.Application.FormUrlEncoded, request.body.contentType)
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

        test {
            assertEquals("DiscordAccessToken", getDiscordToken("DiscordAuthCode", "redir"))
        }
    }

    @Test
    fun `Test Discord token retrieval fails on wrong authcode`() {
        declareClientHandler(onlyMatchUrl = "https://discord.com/api/v6/oauth2/token") {
            respondError(
                HttpStatusCode.BadRequest,
                """{"error":"invalid_grant"}""",
                headers = headersOf("Content-Type", "application/json")
            )
        }

        test {
            val exc = assertFailsWith<EndpointException> {
                getDiscordToken("Authcode", "Redir")
            }
            assertEquals(StandardErrorCodes.InvalidAuthCode, exc.errorCode)
        }
    }

    @Test
    fun `Test Discord token retrieval fails on other error`() {
        declareClientHandler(onlyMatchUrl = "https://discord.com/api/v6/oauth2/token") {
            respondError(HttpStatusCode.BadRequest, """{"error":"¯\\_(ツ)_/¯"}""")
        }

        test {
            val exc = assertFailsWith<EndpointException> {
                getDiscordToken("Auth", "Re")
            }
            assertEquals(StandardErrorCodes.DiscordApiFailure, exc.errorCode)
        }
    }

    @Test
    fun `Test Discord info retrieval`() {
        mockHere<DiscordConfiguration> {
            every { servers } returns listOf(
                mockk { every { id } returns "the server id" }
            )
        }

        mockHere<DiscordClientFacade> {
            coEvery { isUserInGuild("myVeryId", "the server id") } returns true
        }

        declareClientHandler(onlyMatchUrl = "https://discord.com/api/v6/users/@me") { req ->
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

        test {
            assertEquals(
                DiscordUserInfo(
                    id = "myVeryId",
                    username = "usseeer#9876",
                    avatarUrl = "https://cdn.discordapp.com/avatars/myVeryId/haaash.png?size=256",
                    true
                ),
                getDiscordInfo("veryserioustoken")
            )
        }
    }
}
