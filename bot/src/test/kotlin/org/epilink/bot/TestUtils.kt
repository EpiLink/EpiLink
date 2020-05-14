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
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.fullPath
import io.ktor.http.hostWithPort
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.TestApplicationResponse
import io.ktor.server.testing.setBody
import io.mockk.mockk
import org.epilink.bot.config.*
import org.koin.test.KoinTest
import org.koin.test.mock.declare
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.test.assertEquals

val minimalConfig = LinkConfiguration(
    "Test",
    server = LinkWebServerConfiguration(0, ProxyType.None, null),
    db = "",
    tokens = LinkTokens(
        discordToken = "",
        discordOAuthClientId = "",
        discordOAuthSecret = "",
        msftOAuthClientId = "",
        msftOAuthSecret = "",
        msftTenant = ""
    ),
    discord = LinkDiscordConfig(null),
    redis = null
)

// From https://ktor.io/clients/http-client/testing.html
val Url.hostWithPortIfRequired: String get() = if (port == protocol.defaultPort) host else hostWithPort
val Url.fullUrl: String get() = "${protocol.name}://$hostWithPortIfRequired$fullPath"

fun KoinTest.declareClientHandler(onlyMatchUrl: String? = null, handler: MockRequestHandler): HttpClient =
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

fun TestApplicationCall.assertStatus(status: HttpStatusCode) {
    val actual = this.response.status()
    assertEquals(status, actual, "Expected status $status, but got $actual instead")
}

inline fun <reified T : Any> KoinTest.mockHere(crossinline body: T.() -> Unit): T =
    declare { mockk(block = body) }

/**
 * Similar to mockHere, but if an instance of T is already injected, apply the initializer to it instead of
 * replacing it
 */
inline fun <reified T : Any> KoinTest.softMockHere(crossinline initializer: T.() -> Unit): T {
    val injected = getKoin().getOrNull<T>()
    return injected?.apply(initializer) ?: mockHere(initializer)
}

fun Map<String, Any?>.getString(key: String): String =
    this.getValue(key) as String

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.getMap(key: String): Map<String, Any?> =
    this.getValue(key) as Map<String, Any?>

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.getListOfMaps(key: String): List<Map<String, Any?>> =
    this.getValue(key) as List<Map<String, Any?>>

inline fun <reified T> fromJson(response: TestApplicationResponse): T {
    return jacksonObjectMapper().readValue(response.content!!)
}

fun String.sha256(): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(this.toByteArray(StandardCharsets.UTF_8))
}

fun TestApplicationRequest.setJsonBody(json: String) {
    addHeader("Content-Type", "application/json")
    setBody(json)
}