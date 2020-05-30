package org.epilink.bot.http.data

// See the Api.md documentation file for more information
@Suppress("KDocMissingDocumentation")
data class RegisteredUserInfo(
    val discordId: String,
    val msftIdHash: String,
    val created: String,
    val identifiable: Boolean
)