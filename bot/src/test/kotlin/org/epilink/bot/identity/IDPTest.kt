/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.identity

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.mockk.coEvery
import io.mockk.mockk
import org.epilink.bot.*
import org.epilink.bot.http.IdentityProvider
import org.epilink.bot.http.JwtVerifier
import org.epilink.bot.http.UserIdentityInfo
import org.jose4j.jwt.consumer.InvalidJwtException
import org.koin.dsl.module
import org.koin.test.KoinTest
import kotlin.test.*

class IDPTest : EpiLinkBaseTest<IdentityProvider>(
    IdentityProvider::class,
    module {
        single { IdentityProvider("CLIENT_ID", "CLIENT_SECRET", "http://TOKEN_URL", "http://AUTHORIZE_URL") }
    }
) {
    /*
     * TODO Test idp metadata from discovery functionality
     */

    @Test
    fun `Test user identity retrieval`() {
        declareTokenRequestHandler("""{"id_token": "JWT_TOKEN"}""")
        val ret = UserIdentityInfo("GUID", "EMAIL")
        mockHere<JwtVerifier> {
            coEvery { process("JWT_TOKEN") } returns ret
        }
        test {
            val actual = getUserIdentityInfo("AUTHCODE", "REDIRECT_URI")
            assertSame(ret, actual)
        }
    }

    @Test
    fun `Test user identity info retrieval failure invalid_grant`() {
        declareTokenRequestHandler("""{"error": "invalid_grant"}""", HttpStatusCode.BadRequest)
        test {
            val ex = assertFailsWith<UserEndpointException> { getUserIdentityInfo("AUTHCODE", "REDIRECT_URI") }
            assertEqualsPairwise(
                "Invalid authorization code" to ex.details,
                "oa.iac" to ex.detailsI18n,
                StandardErrorCodes.InvalidAuthCode to ex.errorCode
            )
        }
    }

    @Test
    fun `Test user identity info retrieval failure other`() {
        declareTokenRequestHandler("""{"error": "blaaah"}""", HttpStatusCode.BadRequest)
        test {
            val ex = assertFailsWith<InternalEndpointException> { getUserIdentityInfo("AUTHCODE", "REDIRECT_URI") }
            assertEquals("Identity Provider OAuth failed: blaaah (no description)", ex.message)
            assertEquals(StandardErrorCodes.IdentityProviderApiFailure, ex.errorCode)
        }
    }

    @Test
    fun `Test user identity info retrieval no token returned`() {
        declareTokenRequestHandler("{}")
        test {
            val ex = assertFailsWith<InternalEndpointException> { getUserIdentityInfo("AUTHCODE", "REDIRECT_URI") }
            assertEquals("Did not receive any ID token from the identity provider", ex.message)
            assertEquals(StandardErrorCodes.IdentityProviderApiFailure, ex.errorCode)
        }
    }

    @Test
    fun `Test user identity info retrieval invalid token`() {
        declareTokenRequestHandler("""{"id_token":"JWT_TOKEN"}""")
        val ex = mockk<InvalidJwtException>()
        mockHere<JwtVerifier> { coEvery { process("JWT_TOKEN") } throws ex }
        test {
            val ex2 = assertFailsWith<InvalidJwtException> { getUserIdentityInfo("AUTHCODE", "REDIRECT_URI") }
            assertSame(ex, ex2)
        }
    }

    @Test
    fun `Test authorize stub`() {
        test {
            assertEquals(
                "http://AUTHORIZE_URL?client_id=CLIENT_ID&response_type=code&prompt=select_account&scope=openid%20profile%20email",
                getAuthorizeStub()
            )
        }
    }
}

private fun <A> assertEqualsPairwise(vararg pairs: Pair<A, A>) {
    pairs.forEach { (x, y) -> assertEquals(x, y) }
}

private fun KoinTest.declareTokenRequestHandler(
    response: String,
    statusCode: HttpStatusCode = HttpStatusCode.OK,
    beforeResponse: suspend MockRequestHandleScope.(request: HttpRequestData) -> Unit = {}
) = declareClientHandler(onlyMatchUrl = "http://TOKEN_URL/") {
    beforeResponse(it)
    respond(
        response,
        statusCode,
        headers = headersOf("Content-Type", "application/json")
    )
}
