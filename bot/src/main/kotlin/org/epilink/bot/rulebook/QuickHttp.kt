/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.rulebook

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.core.toByteArray
import org.slf4j.LoggerFactory
import java.util.Base64

/*
 * Utility functions for use within a Rulebook
 */

private val logger = LoggerFactory.getLogger("epilink.rulebooks.helpers")
private val client = HttpClient(Apache)

/**
 * An authentication method for [httpGetJson] requests
 */
sealed class Auth

/**
 * Basic HTTP authentication method for [httpGetJson] requests
 *
 * @param username The username to provide
 * @param password The password to provide
 */
data class Basic(val username: String, val password: String) : Auth()

/**
 * Bearer token authentication method for [httpGetJson] requests
 *
 * @param token The token to provide
 */
data class Bearer(val token: String) : Auth()

/**
 * Perform a HTTP GET request to the given URL, expecting a JSON reply, and return this JSON reply deserialized to the
 * given return type.
 *
 * @param url The URL for the GET request
 * @param auth An authentication method to use if needed, or null if it is not. An instance of either [Basic] or
 * [Bearer] can be provided.
 */
suspend inline fun <reified T> httpGetJson(url: String, auth: Auth? = null): T {
    return httpGetJson(url, auth, jacksonTypeRef())
}

/**
 * Perform a HTTP GET request to the given URL, expecting a JSON reply, and return this JSON reply deserialized to the
 * given response type.
 *
 * @param url The URL for the GET request
 * @param auth An authentication method to use if needed, or null if it is not. An instance of either [Basic] or
 * [Bearer] can be provided.
 * @param responseType The type the response should be deserialized to.
 */
suspend fun <T> httpGetJson(url: String, auth: Auth?, responseType: TypeReference<T>): T {
    val response = runCatching {
        client.get(url) {
            header(HttpHeaders.Accept, ContentType.Application.Json)

            when (auth) {
                is Bearer -> header(HttpHeaders.Authorization, "Bearer ${auth.token}")
                is Basic -> {
                    val credentials = "${auth.username}:${auth.password}"
                    val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))

                    header(HttpHeaders.Authorization, "Basic $encoded")
                }

                null -> {
                    // No headers required
                }
            }
        }
    }.getOrElse {
        logger.error("Encountered error on httpGetJson call", it)
        throw RuleException("Encountered an error on httpGetJson call", it)
    }

    return ObjectMapper().readValue(response.bodyAsText(), responseType)
}

// TODO test these functions

/**
 * Perform a HTTP GET request to the given URL, expecting a JSON reply, and return this JSON reply as a Map from String
 * to Any?
 *
 * @param url The URL for the GET request
 * @param basicAuth The Basic authentication credentials to use, or null to not use credentials at all
 * @param bearer The bearer to use in the Authorization header, or null to not do that
 * @param eagerAuthentication Since this method was rewritten to prevent thread leaking, this parameter doesn't change
 * anything and is now always enabled
 */
@Suppress("unused")
@Deprecated("Reworked with better auth handling and custom return", ReplaceWith("httpGetJson(url, auth)"))
suspend fun httpGetJson(
    url: String,
    basicAuth: Pair<String, String>? = null,
    bearer: String? = null,
    @Suppress("unused_parameter")
    eagerAuthentication: Boolean = false
): Map<String, Any?> {
    val auth = when {
        basicAuth != null -> Basic(basicAuth.first, basicAuth.second)
        bearer != null -> Bearer(bearer)
        else -> null
    }

    return httpGetJson(url, auth)
}

/**
 * Get a key, where the value is expected to be a map
 */
fun Map<*, *>.getMap(key: String) = this[key] as? Map<*, *> ?: error("Invalid format, $key is not a map")

/**
 * Get a key, where the value is expected to be a list
 */
fun Map<*, *>.getList(key: String) = this[key] as? List<*> ?: error("Invalid format, $key is not a list")

/**
 * Get a key, where the value is expected to be a string
 */
fun Map<*, *>.getString(key: String) = this[key] as? String ?: error("Invalid format, $key is not a string")

/**
 * Get a value at an index, where the value is expected to be a map
 */
fun List<*>.getMap(index: Int) =
    this[index] as? Map<*, *> ?: error("Invalid format, value at index $index is not a map")

/**
 * Get the value at an index, where the value is expected to be a list
 */
fun List<*>.getList(index: Int) =
    this[index] as? List<*> ?: error("Invalid format, value at index $index is not a list")

/**
 * Get the value at an index, where the value is expected to be a string
 */
fun List<*>.getString(index: Int) =
    this[index] as? String ?: error("Invalid format, value at index $index is not a string")

/**
 * Equivalent to [map], but casts each element to [T] each. Returns a list of elements of type [R]
 */
inline fun <reified T, R> Iterable<*>.mapAs(block: (T) -> R): List<R> =
    map { block(it as? T ?: error("Invalid format, value $it is not of type T (${T::class.qualifiedName}")) }
