package org.epilink.bot.http.sessions

/**
 * Session object that contains information about a connected user
 *
 * @property discordId The ID of the connected user
 */
data class ConnectedSession(val discordId: String, val discordUsername: String, val discordAvatar: String?)
