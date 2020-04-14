package org.epilink.bot.http.data

// See the Api.md documentation file for more information
@Suppress("KDocMissingDocumentation")
data class InstanceInformation(
    val title: String,
    val logo: String?,
    val authorizeStub_msft: String,
    val authorizeStub_discord: String,
    val idPrompt: String
)