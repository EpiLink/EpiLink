/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.db

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
     * Returns the user with the given Microsoft ID hash, or null if no such user exists
     */
    suspend fun getUserFromMsftIdHash(msftIdHash: ByteArray): LinkUser?

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
     * Gets a ban with the given ban ID, or null if no such ban exists
     */
    suspend fun getBan(banId: Int): LinkBan?

    /**
     * Record a new ban against a user.
     */
    suspend fun recordBan(target: ByteArray, until: Instant?, author: String, reason: String) : LinkBan

    /**
     * Mark a ban as revoked. Does nothing if the ban was already revoked.
     */
    suspend fun revokeBan(banId: Int)

    /**
     * Create a user in the database using the given registration session's information, without any check on the data's
     * coherence. It is the caller's job to check that the parameters are correct.
     *
     * @param newDiscordId The Discord ID to use
     * @param newMsftIdHash The SHA256 hash of the Microsoft ID to use
     * @param newEmail The email address to use
     * @param keepIdentity If true, the database should also record the email in the session. If false, the database
     * must not keep it.
     */
    suspend fun recordNewUser(
        newDiscordId: String,
        newMsftIdHash: ByteArray,
        newEmail: String,
        keepIdentity: Boolean,
        timestamp: Instant
    ): LinkUser

    /**
     * Record the new identity for the given user. This function does not perform any checks on the given parameters,
     * it is the caller's job to check that the parameters are correct.
     *
     * Do not call this after calling [recordNewUser] if you are only creating a new account, [recordNewUser] also
     * records the identity of its `keepIdentity` is true. This function should only be used in cases where the user
     * already exists but does not have his identity recorded in the database
     *
     * @param user The user who the new e-mail address should be associated to
     * @param newEmail The e-mail address to associate with the Discord ID
     */
    suspend fun recordNewIdentity(user: LinkUser, newEmail: String)

    /**
     * Erase the identity of the given user. This function does not perform any checks on the given parameters.
     *
     * @param user The user whose identity should be erased.
     */
    suspend fun eraseIdentity(user: LinkUser)

    /**
     * Checks whether the given Discord ID has its identity linked to it.
     */
    @UsesTrueIdentity
    suspend fun isUserIdentifiable(user: LinkUser): Boolean

    /**
     * Retrieves the identity for the given Discord ID.
     *
     * @throws LinkException If the user does not have his identity recorded in the database
     */
    @UsesTrueIdentity
    suspend fun getUserEmailWithAccessLog(user: LinkUser, automated: Boolean, author: String, reason: String): String

    /**
     * Retrieve all of the identity accesses where the target has the given Discord ID
     */
    suspend fun getIdentityAccessesFor(user: LinkUser): Collection<LinkIdentityAccess>

    /**
     * Get the language preference for the given Discord user, or null if no preference is recorded. The returned
     * preference should be checked for validity.
     */
    suspend fun getLanguagePreference(discordId: String): String?

    /**
     * Record the language preference for the given Discord user, replacing any existing preference. The preference is
     * not checked for validity.
     */
    suspend fun recordLanguagePreference(discordId: String, preference: String)

    /**
     * Clear the language preference for the given Discord user.
     */
    suspend fun clearLanguagePreference(discordId: String)

    /**
     * Update the Microsoft ID of a given user to the new hash. Can be used for updating user hashes in case of format
     * changes or other backwards-compatibility concerns.
     */
    suspend fun updateMsftId(user: LinkUser, newIdHash: ByteArray)
}