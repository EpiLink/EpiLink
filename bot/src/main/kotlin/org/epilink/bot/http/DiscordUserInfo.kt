/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
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
    val avatarUrl: String?,
    val presentOnMonitoredServers: Boolean?
)
