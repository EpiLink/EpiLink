package org.epilink.bot.http.data

// See the Api.md documentation file for more information
data class RegistrationContinuation(
    val next: String,
    val attachment: RegistrationInformation?
)
