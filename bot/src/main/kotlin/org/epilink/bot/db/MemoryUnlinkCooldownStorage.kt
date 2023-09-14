/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.db

import java.time.Duration
import java.time.Instant

/**
 * In-memory implementation of [UnlinkCooldownStorage]
 */
class MemoryUnlinkCooldownStorage : UnlinkCooldownStorage {
    private val map = HashMap<String, Instant>()

    override suspend fun canUnlink(userId: String): Boolean {
        val stopsAt = map[userId] ?: return true
        return if (stopsAt <= Instant.now()) {
            map.remove(userId)
            true
        } else {
            false
        }
    }

    override suspend fun refreshCooldown(userId: String, seconds: Long) {
        if (seconds <= 0) map.remove(userId)
        map[userId] = Instant.now() + Duration.ofSeconds(seconds)
    }
}
