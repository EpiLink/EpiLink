/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.discord

/**
 * Component for receiving and handling Discord messages
 */
interface DiscordMessageHandler {
    /**
     * Handle a message received on Discord
     */
    suspend fun handleMessage(message: String, senderId: String, channelId: String, serverId: String?)
}
