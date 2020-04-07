package org.epilink.bot.http.sessions

/**
 * Session object that contains information about a registration process
 */
data class RegisterSession (
    /**
     * The Microsoft ID to be used
     */
    val microsoftUid: String? = null,
    /**
     * The Microsoft email to be used
     */
    val email: String? = null,
    /**
     * The Discord ID to be used
     */
    val discordId: String? = null,
    /**
     * The Discord username to be used
     */
    val discordUsername: String? = null,
    /**
     * The Discord avatar url to be used
     */
    val discordAvatarUrl: String? = null
)