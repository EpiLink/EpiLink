/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import org.epilink.bot.StandardErrorCodes.InvalidId
import org.epilink.bot.UserEndpointException
import org.epilink.bot.db.Ban
import org.epilink.bot.db.BanLogic
import org.epilink.bot.db.DatabaseFacade
import org.epilink.bot.db.UnlinkCooldown
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant
import java.util.Base64

/**
 * Component that implements logic for actually banning people
 */
interface BanManager {
    /**
     * Bans the given Base64-URL-safe-encoded Identity Provider ID hash with the given parameters.
     *
     * If the corresponding user actually exists, their roles are invalidated (e.g. recomputed).
     *
     * @param idpHashBase64 Base64 URL safe encoded Identity Provider ID hash of the user to ban
     * @param expiresOn The instant when the ban should expire, or null if the ban should not expire
     * @param author The author of the ban. This value is never displayed to the user, and is only here for logging
     * purposes.
     * @param reason The reason for the ban. This may be displayed to the user.
     * @return The ban that was created
     */
    suspend fun ban(idpHashBase64: String, expiresOn: Instant?, author: String, reason: String): Ban

    /**
     * Revoke an existing ban.
     *
     * @param idpHashBase64 The Base64 URL safe encoded Identity Provider ID hash of the user who is banned with the
     * given ban ID
     * @param banId The ID of the ban that should be revoked
     */
    suspend fun revokeBan(idpHashBase64: String, banId: Int)
}

@OptIn(KoinApiExtension::class)
internal class BanManagerImpl : BanManager, KoinComponent {
    private val dbf: DatabaseFacade by inject()
    private val roleManager: RoleManager by inject()
    private val banLogic: BanLogic by inject()
    private val messages: DiscordMessages by inject()
    private val i18n: DiscordMessagesI18n by inject()
    private val sender: DiscordMessageSender by inject()
    private val cooldown: UnlinkCooldown by inject()

    override suspend fun ban(idpHashBase64: String, expiresOn: Instant?, author: String, reason: String): Ban {
        val actualHash = Base64.getUrlDecoder().decode(idpHashBase64)
        val ban = dbf.recordBan(actualHash, expiresOn, author, reason)
        val user = dbf.getUserFromIdpIdHash(actualHash)
        if (user != null) {
            roleManager.invalidateAllRolesLater(user.discordId)
            cooldown.refreshCooldown(user.discordId)
            messages.getBanNotification(i18n.getLanguage(author), reason, expiresOn)?.let {
                sender.sendDirectMessageLater(user.discordId, it)
            }
        }
        return ban
    }

    override suspend fun revokeBan(idpHashBase64: String, banId: Int) {
        val actualHash = Base64.getUrlDecoder().decode(idpHashBase64)
        val ban = dbf.getBan(banId) ?: throw UserEndpointException(InvalidId, "Unknown ban ID", "bm.ubi")
        if (!ban.idpIdHash.contentEquals(actualHash)) {
            throw UserEndpointException(InvalidId, "Identity Provider ID hash does not match given ban ID", "bm.mid")
        }
        val previouslyActive = banLogic.isBanActive(ban)
        dbf.revokeBan(banId)
        if (previouslyActive) {
            val user = dbf.getUserFromIdpIdHash(actualHash) ?: return
            roleManager.invalidateAllRolesLater(user.discordId)
        }
    }
}
