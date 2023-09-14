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
import guru.zoroark.tegral.di.dsl.put
import guru.zoroark.tegral.di.environment.ScopedSupplier
import guru.zoroark.tegral.di.environment.getOrNull
import guru.zoroark.tegral.di.test.TestMutableInjectionEnvironment
import guru.zoroark.tegral.di.test.mockk.putMock
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
import io.mockk.spyk
import org.epilink.bot.config.Configuration
import org.epilink.bot.config.DiscordConfiguration
import org.epilink.bot.config.IdentityProviderConfiguration
import org.epilink.bot.config.ProxyType
import org.epilink.bot.config.TokensConfiguration
import org.epilink.bot.config.WebServerConfiguration
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

fun TestMutableInjectionEnvironment.putClientHandler(
    onlyMatchUrl: String? = null,
    handler: MockRequestHandler
): HttpClient =
    HttpClient(MockEngine) {
        expectSuccess = true
        engine {
            addHandler(
                if (onlyMatchUrl == null) {
                    handler
                } else {
                    { request ->
                        when (request.url.fullUrl) {
                            onlyMatchUrl -> handler(request)
                            else -> error("Url ${request.url.fullUrl} does not match expected URL $onlyMatchUrl")
                        }
                    }
                }
            )
        }
    }.also {
        put { it }
    }

fun TestApplicationCall.assertStatus(status: HttpStatusCode) {
    val actual = this.response.status()
    assertEquals(status, actual, "Expected status $status, but got $actual instead")
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

inline fun <reified T : Any> TestMutableInjectionEnvironment.putMockOrApply(mockSetup: T.() -> Unit): T {
    val currentMock = this.getOrNull<T>()
    return if (currentMock != null) {
        currentMock.mockSetup()
        currentMock
    } else {
        putMock(mockSetup = mockSetup)
    }
}

inline fun <reified T : Any> TestMutableInjectionEnvironment.putSpy(
    crossinline supplier: ScopedSupplier<T>,
    mockSetup: T.() -> Unit
): T {
    val decl = put<T> { spyk(supplier(this)) }
    val spy = get(decl.identifier)
    spy.mockSetup()
    return spy
}
