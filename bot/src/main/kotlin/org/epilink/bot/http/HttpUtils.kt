package org.epilink.bot.http

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.ParametersBuilder

/**
 * Utility function for appending classic OAuth parameters to a ParametersBuilder object all at once.
 */
fun ParametersBuilder.appendOauthParameters(
    clientId: String, secret: String, authcode: String, redirectUri: String
) {
    append("grant_type", "authorization_code")
    append("client_id", clientId)
    append("client_secret", secret)
    append("code", authcode)
    append("redirect_uri", redirectUri)
}

/**
 * Utility function for performing a GET on the given url (with the given bearer as the "Authorization: Bearer" header),
 * turning the JSON result into a map and returning that map.
 */
suspend fun HttpClient.getJson(url: String, bearer: String): Map<String, Any?> {
    val result = get<String>(url) {
        header("Authorization", "Bearer $bearer")
        header(HttpHeaders.Accept, ContentType.Application.Json)
    }
    return ObjectMapper().readValue(result)
}