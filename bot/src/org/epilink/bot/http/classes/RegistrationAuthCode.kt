package org.epilink.bot.http.classes

// See the Api.md documentation file for more information
data class RegistrationAuthCode(
    val code: String,
    val redirectUri: String
)
