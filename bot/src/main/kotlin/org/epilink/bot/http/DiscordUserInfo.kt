package org.epilink.bot.http

/**
 * Represents Discord user information retrieved from the Discord API. This is manually filled in, so it does not
 * *actually* correspond do any real Discord endpoint.
 */
data class DiscordUserInfo(
    /**
     * The ID of the Discord user
     */
    val id: String,
    /**
     * The username (name + discriminator) of the Discord user
     */
    val username: String,
    /**
     * An optional URL to the user's avatar
     */
    val avatarUrl: String?
)
