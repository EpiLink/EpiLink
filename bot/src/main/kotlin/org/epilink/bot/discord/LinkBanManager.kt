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
import org.epilink.bot.StandardErrorCodes.InvalidId
import org.epilink.bot.db.LinkBan
import org.epilink.bot.db.LinkBanLogic
import org.epilink.bot.db.LinkDatabaseFacade
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.Instant
import java.util.*

/**
 * Component that implements logic for actually banning people
 */
interface LinkBanManager {
    /**
     * Bans the given Base64-URL-safe-encoded Microsoft ID hash with the given parameters.
     *
     * If the corresponding user actually exists, their roles are invalidated (e.g. recomputed).
     *
     * @param msftHashBase64 Base64 URL safe encoded Microsoft ID hash of the user to ban
     * @param expiresOn The instant when the ban should expire, or null if the ban should not expire
     * @param author The author of the ban. This value is never displayed to the user, and is only here for logging
     * purposes.
     * @param reason The reason for the ban. This may be displayed to the user.
     * @return The ban that was created
     */
    suspend fun ban(msftHashBase64: String, expiresOn: Instant?, author: String, reason: String): LinkBan

    /**
     * Revoke an existing ban.
     *
     * @param msftHashBase64 The Base64 URL safe encoded Microsoft ID hash of the user who is banned with the given
     * ban ID
     * @param banId The ID of the ban that should be revoked
     */
    suspend fun revokeBan(msftHashBase64: String, banId: Int)
}

internal class LinkBanManagerImpl : LinkBanManager, KoinComponent {
    private val dbf: LinkDatabaseFacade by inject()
    private val roleManager: LinkRoleManager by inject()
    private val banLogic: LinkBanLogic by inject()

    override suspend fun ban(msftHashBase64: String, expiresOn: Instant?, author: String, reason: String): LinkBan {
        val actualHash = Base64.getUrlDecoder().decode(msftHashBase64)
        val ban = dbf.recordBan(actualHash, expiresOn, author, reason)
        val user = dbf.getUserFromMsftIdHash(actualHash)
        if (user != null)
            roleManager.invalidateAllRoles(user.discordId)
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