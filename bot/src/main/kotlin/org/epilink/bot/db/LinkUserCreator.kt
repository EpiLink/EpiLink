package org.epilink.bot.db

import org.epilink.bot.LinkEndpointException
import org.epilink.bot.StandardErrorCodes
import org.epilink.bot.debug
import org.epilink.bot.infoOrDebug
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

interface LinkUserCreator {
    /**
     * Create a user in the database with all of the given parameters
     */
    suspend fun createUser(discordId: String, microsoftUid: String, email: String, keepIdentity: Boolean): LinkUser
}

internal class LinkUserCreatorImpl : LinkUserCreator, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.usercreator")
    private val perms: LinkPermissionChecks by inject()
    private val facade: LinkDatabaseFacade by inject()

    @OptIn(UsesTrueIdentity::class) // Creates a user's true identity: access is expected here.
    override suspend fun createUser(
        discordId: String,
        microsoftUid: String,
        email: String,
        keepIdentity: Boolean
    ): LinkUser {
        return when (val adv = isAllowedToCreateAccount(discordId, microsoftUid, email)) {
            is Disallowed -> throw LinkEndpointException(
                StandardErrorCodes.AccountCreationNotAllowed,
                adv.reason,
                true
            ).also { logger.debug { "Account creation disallowed for Discord $discordId / MS $microsoftUid: " + adv.reason } }
            is Allowed -> {
                logger.infoOrDebug("Creating a new user") {
                    """
                    Creating a new user with:
                            Discord ID: $discordId
                          Microsoft ID: $microsoftUid
                                E-Mail: $email
                         Keep identity: $keepIdentity
                    """.trimIndent()
                }
                facade.recordNewUser(discordId, microsoftUid.hashSha256(), email, keepIdentity, Instant.now())
            }
        }
    }

    private suspend fun isAllowedToCreateAccount(
        discordId: String,
        microsoftId: String,
        email: String
    ): DatabaseAdvisory =
        perms.isMicrosoftUserAllowedToCreateAccount(microsoftId, email) and {
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

/**
 * Utility function for hashing a String using the SHA-256 algorithm. The String is first converted to a byte array
 * using the UTF-8 charset.
 */
// TODO replace by common util func
private fun String.hashSha256(): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(this.toByteArray(StandardCharsets.UTF_8))