/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import org.epilink.bot.LinkEndpointException
import org.epilink.bot.LinkErrorCode
import org.epilink.bot.LinkException
import org.epilink.bot.StandardErrorCodes
import org.epilink.bot.StandardErrorCodes.*
import org.epilink.bot.db.LinkBan
import org.epilink.bot.db.LinkBanLogic
import org.epilink.bot.db.LinkDatabaseFacade
import org.epilink.bot.db.LinkServerDatabase
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.Instant
import java.util.*

interface LinkBanManager {
    suspend fun ban(userId: String, expiresOn: Instant?, author: String, reason: String): LinkBan

    suspend fun revokeBan(msftHashBase64: String, banId: Int)
}

internal class LinkBanManagerImpl : LinkBanManager, KoinComponent {
    private val db: LinkServerDatabase by inject()
    private val dbf: LinkDatabaseFacade by inject()
    private val roleManager: LinkRoleManager by inject()
    private val banLogic: LinkBanLogic by inject()

    override suspend fun ban(userId: String, expiresOn: Instant?, author: String, reason: String): LinkBan {
        val u = db.getUser(userId) ?: throw LinkException("User '$userId' does not exist")
        val ban = dbf.recordBan(u.msftIdHash, expiresOn, author, reason)
        roleManager.invalidateAllRoles(userId)
        return ban
    }

    override suspend fun revokeBan(msftHashBase64: String, banId: Int) {
        val actualHash = Base64.getUrlDecoder().decode(msftHashBase64)
        val ban = dbf.getBan(banId) ?: throw LinkEndpointException(InvalidId, "Unknown ban ID $banId", true)
        if (!ban.msftIdHash.contentEquals(actualHash)) {
            throw LinkEndpointException(InvalidId, "Microsoft ID hash does not match given ban ID $banId", true)
        }
        val previouslyActive = banLogic.isBanActive(ban)
        dbf.revokeBan(banId)
        if (previouslyActive) {
            val user = dbf.getUserFromMsftIdHash(actualHash) ?: return
            roleManager.invalidateAllRoles(user.discordId)
        }
    }
}