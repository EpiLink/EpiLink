/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
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
