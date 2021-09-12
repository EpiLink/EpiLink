/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.identity

import guru.zoroark.shedinja.dsl.put
import guru.zoroark.shedinja.test.ShedinjaBaseTest
import guru.zoroark.shedinja.test.UnsafeMutableEnvironment
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.coEvery
import io.mockk.mockk
import org.epilink.bot.InternalEndpointException
import org.epilink.bot.StandardErrorCodes
import org.epilink.bot.UserEndpointException
import org.epilink.bot.http.IdentityProvider
import org.epilink.bot.http.JwtVerifier
import org.epilink.bot.http.UserIdentityInfo
import org.epilink.bot.putClientHandler
import org.epilink.bot.putMock
import org.epilink.bot.stest
import org.jose4j.jwt.consumer.InvalidJwtException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class IDPTest : ShedinjaBaseTest<IdentityProvider>(
    IdentityProvider::class, {
        put { IdentityProvider(scope, "CLIENT_ID", "CLIENT_SECRET", "http://TOKEN_URL", "http://AUTHORIZE_URL") }
    }
) {
    /*
     * TODO Test idp metadata from discovery functionality
     */

    @Test
    fun `Test user identity retrieval`() = stest {
        declareTokenRequestHandler("""{"id_token": "JWT_TOKEN"}""")
        val ret = UserIdentityInfo("GUID", "EMAIL")
        putMock<JwtVerifier> { coEvery { process("JWT_TOKEN") } returns ret }
        val actual = subject.getUserIdentityInfo("AUTHCODE", "REDIRECT_URI")
        assertSame(ret, actual)
    }

    @Test
    fun `Test user identity info retrieval failure invalid_grant`() = stest {
        declareTokenRequestHandler("""{"error": "invalid_grant"}""", HttpStatusCode.BadRequest)
        val ex = assertFailsWith<UserEndpointException> {
            subject.getUserIdentityInfo("AUTHCODE", "REDIRECT_URI")
        }
        assertEqualsPairwise(
            "Invalid authorization code" to ex.details,
            "oa.iac" to ex.detailsI18n,
            StandardErrorCodes.InvalidAuthCode to ex.errorCode
        )
    }

    @Test
    fun `Test user identity info retrieval failure other`() = stest {
        declareTokenRequestHandler("""{"error": "blaaah"}""", HttpStatusCode.BadRequest)
        val ex = assertFailsWith<InternalEndpointException> { subject.getUserIdentityInfo("AUTHCODE", "REDIRECT_URI") }
        assertEquals("Identity Provider OAuth failed: blaaah (no description)", ex.message)
        assertEquals(StandardErrorCodes.IdentityProviderApiFailure, ex.errorCode)
    }

    @Test
    fun `Test user identity info retrieval no token returned`() = stest {
        declareTokenRequestHandler("{}")
        val ex = assertFailsWith<InternalEndpointException> {
            subject.getUserIdentityInfo("AUTHCODE", "REDIRECT_URI")
        }
        assertEquals("Did not receive any ID token from the identity provider", ex.message)
        assertEquals(StandardErrorCodes.IdentityProviderApiFailure, ex.errorCode)
    }

    @Test
    fun `Test user identity info retrieval invalid token`() = stest {
        declareTokenRequestHandler("""{"id_token":"JWT_TOKEN"}""")
        val ex = mockk<InvalidJwtException>()
        putMock<JwtVerifier> { coEvery { process("JWT_TOKEN") } throws ex }
        val ex2 = assertFailsWith<InvalidJwtException> {
            subject.getUserIdentityInfo("AUTHCODE", "REDIRECT_URI")
        }
        assertSame(ex, ex2)
    }

    @Test
    fun `Test authorize stub`() = test {
        assertEquals(
            "http://AUTHORIZE_URL?client_id=CLIENT_ID&response_type=code&prompt=select_account&scope=openid%20profile%20email",
            subject.getAuthorizeStub()
        )
    }
}

private fun <A> assertEqualsPairwise(vararg pairs: Pair<A, A>) {
    pairs.forEach { (x, y) -> assertEquals(x, y) }
}

private fun UnsafeMutableEnvironment.declareTokenRequestHandler(
    response: String,
    statusCode: HttpStatusCode = HttpStatusCode.OK,
    beforeResponse: suspend MockRequestHandleScope.(request: HttpRequestData) -> Unit = {}
) = putClientHandler(onlyMatchUrl = "http://TOKEN_URL/") {
    beforeResponse(it)
    respond(
        response,
        statusCode,
        headers = headersOf("Content-Type", "application/json")
    )
}
