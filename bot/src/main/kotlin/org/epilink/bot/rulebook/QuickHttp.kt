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
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.basic
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/*
 * Utility functions for use within a Rulebook
 */

private val logger = LoggerFactory.getLogger("epilink.rulebooks.helpers")

/**
 * Perform a HTTP GET request to the given URL, expecting a JSON reply, and return this JSON reply as a Map from String
 * to Any?
 *
 * @param url The URL for the GET request
 * @param basicAuth The Basic authentication credentials to use, or null to not use credentials at all
 * @param bearer The bearer to use in the Authorization header, or null to not do that
 * @param eagerAuthentication If the authentication information should be sent directly or only after the server sends
 * back an unauthorized error
 */
@Suppress("unused")
suspend fun httpGetJson(
    url: String,
    basicAuth: Pair<String, String>? = null,
    bearer: String? = null,
    eagerAuthentication: Boolean = false
): Map<String, Any?> {
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
            if (bearer != null)
                header(HttpHeaders.Authorization, "Bearer $bearer")
        }
    }.getOrElse {
        logger.error("Encountered error on httpGetJson call", it)
        throw RuleException("Encountered an error on httpGetJson call", it)
    }

    return withContext(Dispatchers.Default) { ObjectMapper().readValue(response) }
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
fun List<*>.getMap(index: Int) = this[index] as? Map<*, *> ?: error("Invalid format, value at index $index is not a map")

/**
 * Get the value at an index, where the value is expected to be a list
 */
fun List<*>.getList(index: Int) = this[index] as? List<*> ?: error("Invalid format, value at index $index is not a list")

/**
 * Get the value at an index, where the value is expected to be a string
 */
fun List<*>.getString(index: Int) = this[index] as? String ?: error("Invalid format, value at index $index is not a string")