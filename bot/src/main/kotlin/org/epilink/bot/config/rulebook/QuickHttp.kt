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
    val response = client.get<String>(url) {
        header(HttpHeaders.Accept, ContentType.Application.Json)
        if (bearer != null)
            header(HttpHeaders.Authorization, "Bearer $bearer")
    }
    return withContext(Dispatchers.Default) { ObjectMapper().readValue(response) }
}