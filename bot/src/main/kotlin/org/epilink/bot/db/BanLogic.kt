/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.db

import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import java.time.Instant

/**
 * Component that implements some implementation-agnostic logic on bans
 */
interface BanLogic {
    /**
     * Returns true if the given ban is considered active, false if the ban is considered inactive.
     *
     * A user who has at least one active ban is considered banned.
     *
     * A ban is considered inactive if it is revoked or has expired. Otherwise, it is considered active.
     */
    fun isBanActive(ban: Ban): Boolean
}

@OptIn(KoinApiExtension::class)
internal class BanLogicImpl : KoinComponent, BanLogic {
    override fun isBanActive(ban: Ban): Boolean {
        if (ban.revoked) {
            return false
        }
        val expiry = ban.expiresOn ?: return true
        return expiry.isAfter(Instant.now())
    }
}
