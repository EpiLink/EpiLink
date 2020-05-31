package org.epilink.bot.http.data

// See the Api.md documentation file for more information
@Suppress("KDocMissingDocumentation")
data class BanRequest(
    val reason: String,
    val expiresOn: String?
)