/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.db

/**
 * Storage for the cooldown functionality of EpiLink
 */
interface UnlinkCooldownStorage {
    /**
     * True if the user with the given ID is allowed to remove their identity from the server, false if they are not.
     */
    suspend fun canUnlink(userId: String): Boolean

    /**
     * Refresh (or start) the unlink cooldown for the user with the given ID for the given amount of time in seconds.
     *
     * This must override any previously remembered cooldown duration.
     *
     * If [seconds] is 0, then the cooldown must be removed for this user.
     */
    suspend fun refreshCooldown(userId: String, seconds: Long)
}