package org.epilink.bot.http.classes

// See the Api.md documentation file for more information
data class RegistrationInformation(
    val discordUsername: String?,
    val discordAvatarUrl: String?,
    val email: String?
)