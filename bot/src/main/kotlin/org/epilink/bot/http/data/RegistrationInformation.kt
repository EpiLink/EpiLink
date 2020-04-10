package org.epilink.bot.http.data

// See the Api.md documentation file for more information
@Suppress("KDocMissingDocumentation")
data class RegistrationInformation(
    val discordUsername: String?,
    val discordAvatarUrl: String?,
    val email: String?
)