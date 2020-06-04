/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.db

import org.epilink.bot.debug
import org.epilink.bot.rulebook.Rulebook
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

interface LinkPermissionChecks {
    /**
     * Checks whether an account with the given Discord user ID would be allowed to create an account.
     */
    suspend fun isDiscordUserAllowedToCreateAccount(discordId: String): DatabaseAdvisory

    /**
     * Checks whether an account with the given Microsoft ID and e-mail address would be allowed to create an account.
     */
    suspend fun isMicrosoftUserAllowedToCreateAccount(microsoftId: String, email: String): DatabaseAdvisory

    /**
     * Checks whether a user should be able to join a server (i.e. not banned, no irregularities)
     *
     * At present, the only reason for a known user to be denied joining servers is if they are banned.
     *
     * @return a database advisory with a end-user friendly reason.
     */
    suspend fun canUserJoinServers(dbUser: LinkUser): DatabaseAdvisory
}

internal class LinkPermissionChecksImpl : LinkPermissionChecks, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.perms")
    private val facade: LinkDatabaseFacade by inject()
    private val rulebook: Rulebook by inject()
    private val banLogic: LinkBanLogic by inject()

    override suspend fun isDiscordUserAllowedToCreateAccount(discordId: String): DatabaseAdvisory {
        return if (facade.doesUserExist(discordId))
            Disallowed("This Discord account already exists")
        else
            Allowed
    }

    override suspend fun isMicrosoftUserAllowedToCreateAccount(microsoftId: String, email: String): DatabaseAdvisory {
        val hash = microsoftId.hashSha256()
        if (facade.isMicrosoftAccountAlreadyLinked(hash))
            return Disallowed("This Microsoft account is already linked to another account")
        if (rulebook.validator?.invoke(email) == false) { // == false because the left side can be true or null
            return Disallowed("This e-mail address was rejected. Are you sure you are using the correct Microsoft account?")
        }
        val b = facade.getBansFor(hash)
        if (b.any { banLogic.isBanActive(it) }) {
            return Disallowed("This Microsoft account is banned")
        }
        return Allowed
    }

    override suspend fun canUserJoinServers(dbUser: LinkUser): DatabaseAdvisory {
        val activeBan = facade.getBansFor(dbUser.msftIdHash).firstOrNull { banLogic.isBanActive(it) }
        if (activeBan != null) {
            logger.debug { "Active ban found for user ${dbUser.discordId} (with reason ${activeBan.reason})." }
            return Disallowed("You are banned from joining any server at the moment. (Ban reason: ${activeBan.reason})")
        }
        return Allowed
    }
}

/**
 * Utility function for hashing a String using the SHA-256 algorithm. The String is first converted to a byte array
 * using the UTF-8 charset.
 */
// TODO replace by common util func
private fun String.hashSha256(): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(this.toByteArray(StandardCharsets.UTF_8))
