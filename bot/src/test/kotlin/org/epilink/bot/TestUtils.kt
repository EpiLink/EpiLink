/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.mockk
import org.koin.test.KoinTest
import org.koin.test.mock.declare
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.test.assertEquals

// From https://ktor.io/clients/http-client/testing.html
private val Url.hostWithPortIfRequired: String get() = if (port == protocol.defaultPort) host else hostWithPort
private val Url.fullUrl: String get() = "${protocol.name}://$hostWithPortIfRequired$fullPath"

/**
 * Declare a Ktor client handler that will be available as an injected dependency
 *
 * @param onlyMatchUrl If non-null, the client will only accept requests that have for destination the given URL
 * @param handler The handler that will be called to handle the request
 * @return The constructed mock HTTP client
 * @receiver A KoinTest class in which the HTTP client will be injected
 */
fun KoinTest.declareClientHandler(onlyMatchUrl: String? = null, handler: MockRequestHandler): HttpClient =
    declare {
        HttpClient(MockEngine) {
            engine {
                addHandler(onlyMatchUrl?.let {
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

/**
 * Asserts that the status parameter is equal to the status in the call
 *
 * @param status The expected status code
 * @receiver The actual call
 */
fun TestApplicationCall.assertStatus(status: HttpStatusCode) {
    val actual = this.response.status()
    assertEquals(status, actual, "Expected status $status, but got $actual instead")
}

/**
 * Inject a mock of type [T] in the current Koin environment and run [body] with the mocked object as the receiver.
 *
 * Throws an exception if a definition of type [T] already exists in Koin
 */
inline fun <reified T : Any> KoinTest.mockHere(crossinline body: T.() -> Unit): T {
    if (getKoin().getOrNull<T>() != null) {
        error("Duplicate definition for ${T::class}. Use softMockHere or combine the definitions.")
    }
    return declare { mockk(block = body) }
}

/**
 * Similar to mockHere, but if an instance of T is already injected, apply the initializer to it instead of
 * replacing it.
 */
inline fun <reified T : Any> KoinTest.softMockHere(crossinline initializer: T.() -> Unit): T {
    val injected = getKoin().getOrNull<T>()
    return injected?.apply(initializer) ?: mockHere(initializer)
}

/**
 * Parses the content of the response as a JSON object representing the given type [T]
 */
inline fun <reified T> fromJson(response: TestApplicationResponse): T {
    return jacksonObjectMapper().readValue(response.content!!)
}

/**
 * Turn this string into a byte array by digesting it using the SHA-256 algorithm
 */
fun String.sha256(): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(this.toByteArray(StandardCharsets.UTF_8))
}

/**
 * Set the body of this request to the given string. This also sets the Content-Type header of this request.
 */
fun TestApplicationRequest.setJsonBody(json: String) {
    addHeader("Content-Type", "application/json")
    setBody(json)
}