/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import org.epilink.bot.LinkException
import org.epilink.bot.db.LinkBan
import org.epilink.bot.db.LinkDatabaseFacade
import org.epilink.bot.db.LinkServerDatabase
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.Instant

interface LinkBanManager {
    suspend fun ban(userId: String, expiresOn: Instant? = null): LinkBan
}

internal class LinkBanManagerImpl : LinkBanManager, KoinComponent {
    private val db: LinkServerDatabase by inject()
    private val dbf: LinkDatabaseFacade by inject()
    private val roleManager: LinkRoleManager by inject()
    override suspend fun ban(userId: String, expiresOn: Instant?): LinkBan {
        val u = db.getUser(userId) ?:
            throw LinkException("User '$userId' does not exist")
        // TODO what should we do if a user is already banned?
        // TODO add reasons (that would break db compatibility)
        val ban = dbf.recordBan(u.msftIdHash, expiresOn)
        roleManager.invalidateAllRoles(userId)
        return ban
    }
}