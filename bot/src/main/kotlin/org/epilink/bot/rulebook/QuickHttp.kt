/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.rulebook

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.auth.*
import io.ktor.client.features.auth.providers.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

/*
 * Utility functions for use within a Rulebook
 */

private val logger = LoggerFactory.getLogger("epilink.rulebooks.helpers")

// TODO test these functions

/**
 * Perform a HTTP GET request to the given URL, expecting a JSON reply, and return this JSON reply as a Map from String
 * to Any?
 *
 * @param url The URL for the GET request
 * @param basicAuth The Basic authentication credentials to use, or null to not use credentials at all
 * @param bearer The bearer to use in the Authorization header, or null to not do that
 * @param eagerAuthentication If the authentication information should be sent directly or only after the server sends
 * back an unauthorized error
 * @param headers Custom headers that will be added to the request
 */
@Suppress("unused")
suspend fun httpGetJson(
    url: String,
    basicAuth: Pair<String, String>? = null,
    bearer: String? = null,
    eagerAuthentication: Boolean = true,
    headers: List<Pair<String, String>> = listOf()
): Map<String, Any?> =
    httpGetJsonTyped(url, basicAuth, bearer, eagerAuthentication, headers)

/**
 * Perform a HTTP GET request to the given URL, expecting a JSON reply, and return this JSON reply as an object of type
 * `T`
 *
 * @param url The URL for the GET request
 * @param basicAuth The Basic authentication credentials to use, or null to not use credentials at all
 * @param bearer The bearer to use in the Authorization header, or null to not do that
 * @param eagerAuthentication If the authentication information should be sent directly or only after the server sends
 * back an unauthorized error
 * @param headers Custom headers that will be added to the request
 * @param T The type of the value that is returned by the JSON API. Can be, for example, `List<*>`, `String`, or a
 * custom data class.
 */
suspend inline fun <reified T : Any> httpGetJsonTyped(
    url: String,
    basicAuth: Pair<String, String>? = null,
    bearer: String? = null,
    eagerAuthentication: Boolean = true,
    headers: List<Pair<String, String>> = listOf()
): T =
    httpGetJsonClass(url, T::class, basicAuth, bearer, eagerAuthentication, headers)

/**
 * Perform a HTTP GET request to the given URL, expecting a JSON reply, and return this JSON reply as an instance of
 * the class given in the parameters.
 *
 * @param url The URL for the GET request
 * @param returnType The class representing the type of what the URL is supposed to return.
 * @param basicAuth The Basic authentication credentials to use, or null to not use credentials at all
 * @param bearer The bearer to use in the Authorization header, or null to not do that
 * @param eagerAuthentication If the authentication information should be sent directly or only after the server sends
 * back an unauthorized error
 * @param headers Custom headers that will be added to the request
 * @param T The type of the value that is returned by the JSON API. Can be, for example, `List<*>`, `String`, or a
 * custom data class.
 */
suspend fun <T : Any> httpGetJsonClass(
    url: String,
    returnType: KClass<T>,
    basicAuth: Pair<String, String>? = null,
    bearer: String? = null,
    eagerAuthentication: Boolean = true,
    headers: List<Pair<String, String>> = listOf()
): T {
    val client = HttpClient(Apache) {
        install(Auth) {
            if (basicAuth != null) {
                basic {
                    username = basicAuth.first
                    password = basicAuth.second
                    sendWithoutRequest = eagerAuthentication
                }
            }
        }
    }
    val response = runCatching {
        client.get<String>(url) {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            headers.forEach { (name, value) -> header(name, value) }
            if (bearer != null)
                header(HttpHeaders.Authorization, "Bearer $bearer")
        }
    }.getOrElse {
        logger.error("Encountered error on httpGetJson call", it)
        throw RuleException("Encountered an error on httpGetJson call", it)
    }

    return withContext(Dispatchers.Default) { ObjectMapper().readValue(response, returnType.java) }
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