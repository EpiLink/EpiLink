package org.epilink.bot.http.data

// See the Api.md documentation file for more information
@Suppress("KDocMissingDocumentation")
data class BanInfo(
    val id: Int,
    val revoked: Boolean,
    val author: String,
    val reason: String,
    val issuedAt: String,
    val expiresOn: String?
)

data class UserBans(
    val banned: Boolean,
    val bans: List<BanInfo>
)