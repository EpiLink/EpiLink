package org.epilink.bot.config.rulebook

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

/*
 * Utility functions for use within a Rulebook
 */

suspend fun httpGetJson(
    url: String,
    basicAuth: Pair<String, String>? = null,
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
    val response = client.get<String>(url) {
        header(HttpHeaders.Accept, ContentType.Application.Json)
    }
    return withContext(Dispatchers.Default) { ObjectMapper().readValue<Map<String, Any?>>(response) }
}