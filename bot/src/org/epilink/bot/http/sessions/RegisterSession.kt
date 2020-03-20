package org.epilink.bot.http.sessions

/**
 * Session object that contains information about a registration process
 */
data class RegisterSession (
    val microsoftUid: String? = null,
    val email: String? = null,
    val discordId: String? = null,
    val discordUsername: String? = null,
    val discordAvatarUrl: String? = null
)