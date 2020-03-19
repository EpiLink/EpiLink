package org.epilink.bot.http.classes

data class RegistrationContinuation(
    val next: String,
    val attachment: RegistrationInformation?
)
