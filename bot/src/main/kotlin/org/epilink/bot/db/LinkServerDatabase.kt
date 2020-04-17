package org.epilink.bot.db

import org.epilink.bot.*
import org.epilink.bot.config.LinkPrivacy
import org.epilink.bot.config.rulebook.Rulebook
import org.epilink.bot.http.data.IdAccess
import org.epilink.bot.http.data.IdAccessLogs
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

/**
 * Interface for using the server database
 */
interface LinkServerDatabase {

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
     * @return a database advisory with a end-user friendly reason.
     */
    suspend fun canUserJoinServers(dbUser: LinkUser): DatabaseAdvisory

    /**
     * Checks whether the user with the given Discord ID has his true identity stored in the database.
     *
     * @throws LinkException If no user exists with the given Discord ID
     */
    @UsesTrueIdentity
    suspend fun isUserIdentifiable(discordId: String): Boolean

    /**
     * Retrieve the identity of a user. This access is logged within the system and the user is notified.
     */
    @UsesTrueIdentity
    suspend fun accessIdentity(
        dbUser: LinkUser,
        automated: Boolean,
        author: String,
        reason: String
    ): String

    /**
     * Create a user in the database with all of the given parameters
     */
    suspend fun createUser(discordId: String, microsoftUid: String, email: String, keepIdentity: Boolean): LinkUser

    /**
     * Get the user with the given Discord ID, or null if said user does not exist
     */
    suspend fun getUser(discordId: String): LinkUser?

    /**
     * Get the identity access logs as an [IdAccessLogs] object, ready to be sent.
     */
    suspend fun getIdAccessLogs(discordId: String): IdAccessLogs
}

/**
 * The class that manages the database and handles all business logic.
 *
 * Most of the functions declared here are suspending functions intended to be used
 * from within Ktor responses.
 */
internal class LinkServerDatabaseImpl : LinkServerDatabase, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.db")

    private val facade: LinkDatabaseFacade by inject()

    private val privacy: LinkPrivacy by inject()

    private val rulebook: Rulebook by inject()

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
                        Discord ID      $discordId
                        Microsoft ID    $microsoftUid
                        E-Mail          $email
                        Keep identity   $keepIdentity
                    """.trimIndent()
                }
                facade.recordNewUser(discordId, microsoftUid.hashSha256(), email, keepIdentity, Instant.now())
            }
        }
    }

    override suspend fun getUser(discordId: String): LinkUser? =
        facade.getUser(discordId)

    /**
     * Checks whether a ban is currently active or not
     */
    private fun LinkBan.isActive(): Boolean {
        val expiry = expiresOn
        return /* Ban does not expire */ expiry == null || /* Ban has not expired */ expiry.isAfter(Instant.now())
    }

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
        if (!rulebook.validator(email)) {
            return Disallowed("This e-mail address was rejected. Are you sure you are using the correct Microsoft account?")
        }
        val b = facade.getBansFor(hash)
        if (b.any { it.isActive() }) {
            return Disallowed("This Microsoft account is banned")
        }
        return Allowed
    }

    private suspend fun isAllowedToCreateAccount(discordId: String, microsoftId: String, email: String): DatabaseAdvisory {
        val msAdv = isMicrosoftUserAllowedToCreateAccount(microsoftId, email)
        if (msAdv is Disallowed) {
            return msAdv
        }
        return isDiscordUserAllowedToCreateAccount(discordId)
    }

    override suspend fun canUserJoinServers(dbUser: LinkUser): DatabaseAdvisory {
        if (facade.getBansFor(dbUser.msftIdHash).any { it.isActive() }) {
            logger.debug { "Active bans found for user ${dbUser.discordId}" }
            return Disallowed("You are banned from joining any server at the moment.")
        }
        return Allowed
    }

    @UsesTrueIdentity
    override suspend fun isUserIdentifiable(discordId: String): Boolean =
        facade.isUserIdentifiable(discordId)

    @UsesTrueIdentity
    override suspend fun accessIdentity(
        dbUser: LinkUser,
        automated: Boolean,
        author: String,
        reason: String
    ): String =
        facade.getUserEmailWithAccessLog(
            discordId = dbUser.discordId,
            automated = automated,
            author = author,
            reason = reason
        )

    override suspend fun getIdAccessLogs(discordId: String): IdAccessLogs =
        IdAccessLogs(
            manualAuthorsDisclosed = privacy.shouldDiscloseIdentity(false),
            accesses = facade.getIdentityAccessesFor(discordId).map { a ->
                IdAccess(
                    a.automated,
                    a.authorName.takeIf { privacy.shouldDiscloseIdentity(a.automated) },
                    a.reason,
                    a.timestamp.toString()
                )
            }.also { logger.debug { "Acquired access logs for $discordId" } }
        )

}

/**
 * Utility function for hashing a String using the SHA-256 algorithm. The String is first converted to a byte array
 * using the UTF-8 charset.
 */
private fun String.hashSha256(): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(this.toByteArray(StandardCharsets.UTF_8))