package org.epilink.bot.http.sessions

/**
 * Session object that contains information about a connected user
 *
 * @property discordId The ID of the connected user
 * @property discordUsername The username (with the discriminator) of the user, only intended to be used for
 * displaying the user's name.
 * @property discordAvatar Optional URL to the user's Discord avatar.
 */
data class ConnectedSession(val discordId: String, val discordUsername: String, val discordAvatar: String?)
