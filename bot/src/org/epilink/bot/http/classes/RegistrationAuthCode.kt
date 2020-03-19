package org.epilink.bot.http.classes

data class RegistrationAuthCode(
    val code: String,
    val redirectUri: String
)
