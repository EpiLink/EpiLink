/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.db

import guru.zoroark.tegral.di.environment.InjectionScope
import guru.zoroark.tegral.di.environment.invoke
import guru.zoroark.tegral.di.environment.named
import org.apache.commons.codec.binary.Hex
import org.epilink.bot.debug
import org.epilink.bot.rulebook.Rulebook
import org.slf4j.LoggerFactory

/**
 * Component for checking permissions (e.g. is a discord user allowed to create an account)
 */
interface PermissionChecks {
    /**
     * Checks whether an account with the given Discord user ID would be allowed to create an account.
     */
    suspend fun isDiscordUserAllowedToCreateAccount(discordId: String): DatabaseAdvisory

    /**
     * Checks whether an account with the given Identity Provider ID and e-mail address would be allowed to create an
     * account.
     */
    suspend fun isIdentityProviderUserAllowedToCreateAccount(idpId: String, email: String): DatabaseAdvisory

    /**
     * Checks whether a user should be able to join a server (i.e. not banned, no irregularities)
     *
     * At present, the only reason for a known user to be denied joining servers is if they are banned.
     *
     * @return a database advisory with a end-user friendly reason.
     */
    suspend fun canUserJoinServers(dbUser: User): DatabaseAdvisory

    /**
     * Checks whether a user can perform an admin action or not.
     *
     * @return a [AdminStatus] that describes the ability for the user to perform admin actions.
     */
    @UsesTrueIdentity
    suspend fun canPerformAdminActions(dbUser: User): AdminStatus
}

/**
 * Enum for the different possible outputs of [PermissionChecks.canPerformAdminActions]
 */
enum class AdminStatus {
    /**
     * The user is not present in the admin list
     */
    NotAdmin,

    /**
     * The user is present in the admin list but is not identifiable
     */
    AdminNotIdentifiable,

    /**
     * The user is present in the admin and is identifiable
     */
    Admin
}

internal class PermissionChecksImpl(scope: InjectionScope) : PermissionChecks {
    private val logger = LoggerFactory.getLogger("epilink.perms")
    private val facade: DatabaseFacade by scope()
    private val rulebook: Rulebook by scope()
    private val banLogic: BanLogic by scope()
    private val admins: List<String> by scope(named("admins"))

    override suspend fun isDiscordUserAllowedToCreateAccount(discordId: String): DatabaseAdvisory {
        return if (facade.doesUserExist(discordId)) {
            Disallowed("This Discord account already exists", "pc.dae")
        } else {
            Allowed
        }
    }

    override suspend fun isIdentityProviderUserAllowedToCreateAccount(idpId: String, email: String): DatabaseAdvisory {
        val hash = idpId.hashSha256()
        return if (facade.isIdentityAccountAlreadyLinked(hash)) {
            Disallowed(
                "This identity provider account is already linked to another account",
                "pc.ala",
                mapOf("idpIdHashHex" to hash.toHexString().let { it.substring(0, it.length / 2) })
            )
        } else if (rulebook.validator?.invoke(email) == false) { // == false because the left side can be true or null
            Disallowed(
                "This e-mail address was rejected. Are you sure you are using the correct identity provider account?",
                "pc.erj"
            )
        } else {
            val b = facade.getBansFor(hash)
            val ban = b.firstOrNull { banLogic.isBanActive(it) }
            if (ban != null) {
                // cba = "creation ban"
                Disallowed(
                    "This identity provider account is banned (reason: ${ban.reason})",
                    "pc.cba",
                    mapOf("reason" to ban.reason)
                )
            } else {
                Allowed
            }
        }
    }

    private fun ByteArray.toHexString(): String =
        Hex.encodeHexString(this)

    override suspend fun canUserJoinServers(dbUser: User): DatabaseAdvisory {
        val activeBan = facade.getBansFor(dbUser.idpIdHash).firstOrNull { banLogic.isBanActive(it) }
        if (activeBan != null) {
            logger.debug { "Active ban found for user ${dbUser.discordId} (with reason ${activeBan.reason})." }
            // jba = "joined ban"
            return Disallowed(
                "You are banned from joining any server at the moment. (Ban reason: ${activeBan.reason})",
                "pc.jba",
                mapOf("reason" to activeBan.reason)
            )
        }
        return Allowed
    }

    @UsesTrueIdentity
    override suspend fun canPerformAdminActions(dbUser: User): AdminStatus =
        when {
            dbUser.discordId !in admins -> AdminStatus.NotAdmin
            !facade.isUserIdentifiable(dbUser) -> AdminStatus.AdminNotIdentifiable
            else -> AdminStatus.Admin
        }
}
