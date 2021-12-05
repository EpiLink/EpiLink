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
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.epilink.bot.config.*
import org.epilink.bot.discord.DiscordMessagesI18n
import org.koin.core.component.KoinApiExtension
import org.koin.test.KoinTest
import org.koin.test.mock.declare
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.test.assertEquals

val minimalConfig = Configuration(
    "Test",
    server = WebServerConfiguration("0.0.0.0", 0, ProxyType.None, null),
    db = "",
    tokens = TokensConfiguration(
        discordToken = "",
        discordOAuthClientId = "",
        discordOAuthSecret = "",
        idpOAuthClientId = "",
        idpOAuthSecret = ""
    ),
    idProvider = IdentityProviderConfiguration(
        url = "",
        name = "",
        icon = null
    ),
    discord = DiscordConfiguration(null),
    redis = null
)

// From https://ktor.io/clients/http-client/testing.html
val Url.hostWithPortIfRequired: String get() = if (port == protocol.defaultPort) host else hostWithPort
val Url.fullUrl: String get() = "${protocol.name}://$hostWithPortIfRequired$fullPath"

fun KoinTest.declareClientHandler(onlyMatchUrl: String? = null, handler: MockRequestHandler): HttpClient =
    declare {
        HttpClient(MockEngine) {
            engine {
                addHandler(
                    onlyMatchUrl?.let {
                        { request ->
                            when (request.url.fullUrl) {
                                onlyMatchUrl -> handler(request)
                                else -> error("Url ${request.url.fullUrl} does not match expected URL $onlyMatchUrl")
                            }
                        }
                    } ?: handler
                )
            }
        }
    }

fun TestApplicationCall.assertStatus(status: HttpStatusCode) {
    val actual = this.response.status()
    assertEquals(status, actual, "Expected status $status, but got $actual instead")
}

@OptIn(KoinApiExtension::class)
inline fun <reified T : Any> KoinTest.mockHere(crossinline body: T.() -> Unit): T {
    if (getKoin().getOrNull<T>() != null) {
        error("Duplicate definition for ${T::class}. Use softMockHere or combine the definitions.")
    }
    return declare { mockk(block = body) }
}

/**
 * Similar to mockHere, but if an instance of T is already injected, apply the initializer to it instead of
 * replacing it
 */
@OptIn(KoinApiExtension::class)
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

/**
 * Mocks the default behavior for the get message: it will return the second argument (the i18n key)
 */
fun DiscordMessagesI18n.defaultMock() {
    val keySlot = slot<String>()
    every { get(any(), capture(keySlot)) } answers { keySlot.captured }
}
