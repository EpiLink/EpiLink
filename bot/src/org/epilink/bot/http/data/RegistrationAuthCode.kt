package org.epilink.bot.http.data

// See the Api.md documentation file for more information
data class RegistrationAuthCode(
    val code: String,
    val redirectUri: String
)
