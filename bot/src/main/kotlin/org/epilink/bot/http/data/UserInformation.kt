package org.epilink.bot.http.data

import org.epilink.bot.db.UsesTrueIdentity

// See the Api.md documentation file for more information
@Suppress("KDocMissingDocumentation")
data class UserInformation(
    val discordId: String,
    val username: String,
    val avatarUrl: String?,
    @UsesTrueIdentity
    val identifiable: Boolean
)