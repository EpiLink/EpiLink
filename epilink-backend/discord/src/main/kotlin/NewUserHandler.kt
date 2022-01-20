/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.discord

interface NewUserHandler {
    /**
     * Handles the event where a user joins a server where the bot is.
     *
     * @param guildId The guild where the user just joined
     * @param guildName The name of the guild the user just joined
     * @param memberId The user who just joined
     */
    suspend fun handleNewUser(guildId: String, guildName: String, memberId: String)
}
