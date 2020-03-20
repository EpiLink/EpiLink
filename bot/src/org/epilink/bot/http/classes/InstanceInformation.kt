package org.epilink.bot.http.classes

// See the Api.md documentation file for more information
data class InstanceInformation(
    val title: String,
    val logo: String?,
    val authorizeStub_msft: String,
    val authorizeStub_discord: String
)