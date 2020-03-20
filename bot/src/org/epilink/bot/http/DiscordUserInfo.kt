package org.epilink.bot.http

/**
 * Represents Discord user information retrieved from the Discord API. This is manually filled in, so it does not
 * *actually* correspond do any real Discord endpoint.
 */
data class DiscordUserInfo(
    val id: String,
    val username: String,
    val avatarUrl: String?
)
