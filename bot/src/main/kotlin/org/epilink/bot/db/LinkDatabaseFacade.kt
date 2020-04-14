package org.epilink.bot.db

import org.epilink.bot.LinkEndpointException
import org.epilink.bot.LinkException
import java.time.Instant

/**
 * The database facade is the interface that is used to communicate with the database.
 */
interface LinkDatabaseFacade {

    /**
     * Starts the database and creates required elements
     */
    suspend fun start()

    /**
     * Returns the server user that has the given Discord account ID associated, or null if no such user exists.
     */
    suspend fun getUser(discordId: String): LinkUser?

    /**
     * Checks if a user exists with the given Discord ID.
     */
    suspend fun doesUserExist(discordId: String): Boolean

    /**
     * Checks if a user exists with the given Microsoft ID hash.
     */
    suspend fun isMicrosoftAccountAlreadyLinked(hash: ByteArray): Boolean

    /**
     * Get the list of bans associated with the given Microsoft ID hash.
     */
    suspend fun getBansFor(hash: ByteArray): List<LinkBan>

    /**
     * Create a user in the database using the given registration session's information, without any check on the data's
     * coherence. It is the caller's job to check that the parameters are correct.
     *
     * @param newDiscordId The Discord ID to use
     * @param newMsftIdHash The SHA256 hash of the Microsoft ID to use
     * @param newEmail The email address to use
     * @param keepIdentity If true, the database should also record the email in the session. If false, the database
     * must not keep it.
     * @throws LinkEndpointException If the session's data is invalid, the user is banned, or another erroneous scenario
     */
    suspend fun recordNewUser(
        newDiscordId: String,
        newMsftIdHash: ByteArray,
        newEmail: String,
        keepIdentity: Boolean,
        timestamp: Instant
    ): LinkUser

    /**
     * Checks whether the given Discord ID has its identity linked to it.
     *
     * @throws LinkException Thrown if no user exists with the given Discord ID
     */
    @UsesTrueIdentity
    suspend fun isUserIdentifiable(discordId: String): Boolean

    /**
     * Retrieves the identity for the given Discord ID.
     *
     * @throws LinkException Thrown if no user exists with the given Discord ID or if the user does not have his
     * identity recorded in the database
     */
    @UsesTrueIdentity
    suspend fun getUserEmailWithAccessLog(discordId: String, automated: Boolean, author: String, reason: String): String
}