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
import io.ktor.server.testing.TestApplicationResponse
import io.mockk.mockk
import org.epilink.bot.config.LinkConfiguration
import org.epilink.bot.config.LinkDiscordConfig
import org.epilink.bot.config.LinkTokens
import org.epilink.bot.config.LinkWebServerConfiguration
import org.koin.test.KoinTest
import org.koin.test.mock.declare
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.test.assertEquals

val minimalConfig = LinkConfiguration(
    "Test",
    server = LinkWebServerConfiguration(0, null),
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
    assertEquals(status, this.response.status())
}

inline fun <reified T : Any> KoinTest.mockHere(crossinline body: T.() -> Unit): T =
    declare { mockk(block = body) }

fun Map<String, Any?>.getString(key: String): String =
    this.getValue(key) as String

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.getMap(key: String): Map<String, Any?> =
    this.getValue(key) as Map<String, Any?>

inline fun <reified T> fromJson(response: TestApplicationResponse): T {
    return jacksonObjectMapper().readValue(response.content!!)
}

fun String.sha256(): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(this.toByteArray(StandardCharsets.UTF_8))
}