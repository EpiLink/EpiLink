/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.db

import org.epilink.bot.EpiLinkException
import java.time.Instant

/**
 * The database facade is the interface that is used to communicate with the database.
 */
@Suppress("TooManyFunctions")
interface DatabaseFacade {
    /**
     * Starts the database and creates required elements
     */
    suspend fun start()

    /**
     * Returns the server user that has the given Discord account ID associated, or null if no such user exists.
     */
    suspend fun getUser(discordId: String): User?

    /**
     * Returns the user with the given Identity Provider ID hash, or null if no such user exists
     */
    suspend fun getUserFromIdpIdHash(idpIdHash: ByteArray): User?

    /**
     * Checks if a user exists with the given Discord ID.
     */
    suspend fun doesUserExist(discordId: String): Boolean

    /**
     * Checks if a user exists with the given identity provider ID hash.
     */
    suspend fun isIdentityAccountAlreadyLinked(hash: ByteArray): Boolean

    /**
     * Get the list of bans associated with the given identity provider ID hash.
     */
    suspend fun getBansFor(hash: ByteArray): List<Ban>

    /**
     * Gets a ban with the given ban ID, or null if no such ban exists
     */
    suspend fun getBan(banId: Int): Ban?

    /**
     * Record a new ban against a user.
     */
    suspend fun recordBan(target: ByteArray, until: Instant?, author: String, reason: String): Ban

    /**
     * Mark a ban as revoked. Does nothing if the ban was already revoked.
     */
    suspend fun revokeBan(banId: Int)

    /**
     * Create a user in the database using the given registration session's information, without any check on the data's
     * coherence. It is the caller's job to check that the parameters are correct.
     *
     * @param newDiscordId The Discord ID to use
     * @param newIdpIdHash The SHA256 hash of the identity provider ID to use
     * @param newEmail The email address to use
     * @param keepIdentity If true, the database should also record the email in the session. If false, the database
     * must not keep it.
     */
    suspend fun recordNewUser(
        newDiscordId: String,
        newIdpIdHash: ByteArray,
        newEmail: String,
        keepIdentity: Boolean,
        timestamp: Instant
    ): User

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
    @UsesTrueIdentity
    suspend fun recordNewIdentity(user: User, newEmail: String)

    /**
     * Erase the identity of the given user. Does nothing if the user does not have a recorded identity.
     *
     * @param user The user whose identity should be erased.
     */
    @UsesTrueIdentity
    suspend fun eraseIdentity(user: User)

    /**
     * Checks whether the given Discord ID has its identity linked to it.
     */
    @UsesTrueIdentity
    suspend fun isUserIdentifiable(user: User): Boolean

    /**
     * Retrieves the identity for the given Discord ID.
     *
     * @throws EpiLinkException If the user does not have his identity recorded in the database
     */
    @UsesTrueIdentity
    suspend fun getUserEmailWithAccessLog(user: User, automated: Boolean, author: String, reason: String): String

    /**
     * Retrieve all of the identity accesses where the target has the given Discord ID
     */
    suspend fun getIdentityAccessesFor(user: User): Collection<IdentityAccess>

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
     * Update the Identity Provider ID of a given user to the new hash. Can be used for updating user hashes in case of
     * format changes or other backwards-compatibility concerns.
     */
    suspend fun updateIdpId(user: User, newIdHash: ByteArray)

    /**
     * Removes the given user
     */
    suspend fun deleteUser(user: User)

    /**
     * Search users who have the given string in their hash (in hex representation). The [partialHashHex] argument must
     * be lowercase.
     */
    suspend fun searchUserByPartialHash(partialHashHex: String): List<User>
}
