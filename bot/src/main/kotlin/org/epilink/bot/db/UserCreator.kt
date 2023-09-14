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
import org.epilink.bot.StandardErrorCodes
import org.epilink.bot.UserEndpointException
import org.epilink.bot.debug
import org.epilink.bot.infoOrDebug
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Component for creating a user.
 */
interface UserCreator {
    /**
     * Create a user in the database with all of the given parameters
     */
    suspend fun createUser(discordId: String, idpId: String, email: String, keepIdentity: Boolean): User
}

internal class UserCreatorImpl(scope: InjectionScope) : UserCreator {
    private val logger = LoggerFactory.getLogger("epilink.usercreator")
    private val perms: PermissionChecks by scope()
    private val facade: DatabaseFacade by scope()

    @OptIn(UsesTrueIdentity::class) // Creates a user's true identity: access is expected here.
    override suspend fun createUser(
        discordId: String,
        idpId: String,
        email: String,
        keepIdentity: Boolean
    ): User {
        return when (val adv = isAllowedToCreateAccount(discordId, idpId, email)) {
            is Disallowed -> throw UserEndpointException(
                StandardErrorCodes.AccountCreationNotAllowed,
                adv.reason,
                adv.reasonI18n,
                adv.reasonI18nData
            ).also { logger.debug { "Account creation disallowed for Discord $discordId / MS $idpId: " + adv.reason } }

            is Allowed -> {
                logger.infoOrDebug("Creating a new user") {
                    """
                    Creating a new user with:
                            Discord ID: $discordId
                                IDP ID: $idpId
                                E-Mail: $email
                         Keep identity: $keepIdentity
                    """.trimIndent()
                }
                facade.recordNewUser(discordId, idpId.hashSha256(), email, keepIdentity, Instant.now())
            }
        }
    }

    private suspend fun isAllowedToCreateAccount(
        discordId: String,
        idpId: String,
        email: String
    ): DatabaseAdvisory =
        perms.isIdentityProviderUserAllowedToCreateAccount(idpId, email) and {
            perms.isDiscordUserAllowedToCreateAccount(discordId)
        }
}

/**
 * Performs an "and" on two database advisories. If the first operand is Allowed, then the block is executed and its
 * result is returned. If the first operand is Disallowed, it is returned directly.
 */
private inline infix fun DatabaseAdvisory.and(lazyOther: () -> DatabaseAdvisory): DatabaseAdvisory =
    when (this) {
        is Disallowed -> this
        Allowed -> lazyOther()
    }
