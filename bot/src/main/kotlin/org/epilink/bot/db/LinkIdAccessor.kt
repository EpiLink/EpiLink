/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.db

import org.epilink.bot.LinkEndpointException
import org.epilink.bot.LinkException
import org.epilink.bot.StandardErrorCodes
import org.epilink.bot.StandardErrorCodes.IdentityAlreadyKnown
import org.epilink.bot.StandardErrorCodes.NewIdentityDoesNotMatch
import org.epilink.bot.config.LinkPrivacy
import org.epilink.bot.debug
import org.epilink.bot.discord.LinkDiscordMessageSender
import org.epilink.bot.discord.LinkDiscordMessages
import org.epilink.bot.http.data.IdAccess
import org.epilink.bot.http.data.IdAccessLogs
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Component that implements ID accessing logic
 */
// TODO rename to LinkIdManager
// TODO replace targetId by a LinkUser object
interface LinkIdAccessor {
    /**
     * Access the identity of the target.
     *
     * @param targetId The Discord ID of the person whose identity should be retrieved
     * @param automated True if this action is done automatically, false if it is made from an admin's request
     * @param author The person who requested the identity. May be displayed to the user depending on the server's
     * privacy settings.
     * @param reason The reason for the identity access. Will be displayed to the user
     * @return The identity of the user (e-mail address)
     * @throws LinkException if the user is not identifiable (i.e. does not have their identity stored in the database)
     */
    @UsesTrueIdentity
    suspend fun accessIdentity(targetId: String, automated: Boolean, author: String, reason: String): String


    /**
     * Get the identity access logs as an [IdAccessLogs] object, ready to be sent.
     */
    suspend fun getIdAccessLogs(discordId: String): IdAccessLogs

    /**
     * Record the identity of the user with the given discordId, using the given email. The ID hash given must be the
     * hash associated with the new e-mail address.
     *
     * This function checks if the user already has their identity recorded in the database, in which case this function
     * throws a [LinkEndpointException] with error [IdentityAlreadyKnown].
     *
     * This function also checks whether the Microsoft ID we remember for them matches the new one. If not, this
     * function throws a [LinkEndpointException] with error [NewIdentityDoesNotMatch].
     *
     * If all goes well, the user then has a true identity created for them.
     *
     * @param discordId The Discord ID of the user of whom we want to relink the identity
     * @param email The new e-mail address
     * @param associatedMsftId The Microsoft ID (not hashed) associated with the new e-mail address
     * @throws LinkEndpointException If the identity of the user is already known, or if the given new ID does not
     * match the previous one
     */
    @UsesTrueIdentity
    suspend fun relinkMicrosoftIdentity(discordId: String, email: String, associatedMsftId: String)

    /**
     * Delete the identity of the user with the given Discord ID from the database, or throw a [LinkEndpointException]
     * if no such identity exists.
     *
     * @param discordId The Discord ID of the user whose identity we should remove
     * @throws LinkEndpointException If the user does not have any identity recorded in the first place.
     */
    @UsesTrueIdentity
    suspend fun deleteUserIdentity(discordId: String)
}

internal class LinkIdAccessorImpl : LinkIdAccessor, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.idaccessor")
    private val facade: LinkDatabaseFacade by inject()
    private val messages: LinkDiscordMessages by inject()
    private val discordSender: LinkDiscordMessageSender by inject()
    private val privacy: LinkPrivacy by inject()


    @UsesTrueIdentity
    override suspend fun accessIdentity(targetId: String, automated: Boolean, author: String, reason: String): String {
        // TODO replace all the exceptions with a distinct return value (sealed class or something)
        //      This should probably be done in the facade implementation itself though
        val user = facade.getUser(targetId) ?: throw LinkException("User does not exist")
        if (!facade.isUserIdentifiable(user)) {
            throw LinkException("User is not identifiable")
        }
        val embed = messages.getIdentityAccessEmbed(automated, author, reason)
        if (embed != null)
            discordSender.sendDirectMessageLater(targetId, embed)
        return facade.getUserEmailWithAccessLog(user, automated, author, reason)
    }

    @UsesTrueIdentity
    override suspend fun relinkMicrosoftIdentity(discordId: String, email: String, associatedMsftId: String) {
        val u = facade.getUser(discordId) ?: throw LinkException("User not found: $discordId")
        if (facade.isUserIdentifiable(u)) {
            throw LinkEndpointException(IdentityAlreadyKnown, "Cannot update identity, it is already known", true)
        }
        val knownHash = u.msftIdHash
        val newHash = associatedMsftId.hashSha256()
        if (!knownHash.contentEquals(newHash)) {
            throw LinkEndpointException(NewIdentityDoesNotMatch, isEndUserAtFault = true)
        }
        facade.recordNewIdentity(u, email)
    }

    override suspend fun getIdAccessLogs(discordId: String): IdAccessLogs {
        val u = facade.getUser(discordId) ?: throw LinkException("User not found: $discordId")
        return IdAccessLogs(
            manualAuthorsDisclosed = privacy.shouldDiscloseIdentity(false),
            accesses = facade.getIdentityAccessesFor(u).map { a ->
                IdAccess(
                    a.automated,
                    a.authorName.takeIf { privacy.shouldDiscloseIdentity(a.automated) },
                    a.reason,
                    a.timestamp.toString()
                )
            }.also { logger.debug { "Acquired access logs for $discordId" } }
        )
    }

    @UsesTrueIdentity
    override suspend fun deleteUserIdentity(discordId: String) {
        val u = facade.getUser(discordId) ?: throw LinkException("User not found: $discordId")
        if (!facade.isUserIdentifiable(u))
            throw LinkEndpointException(StandardErrorCodes.IdentityAlreadyUnknown, isEndUserAtFault = true)
        facade.eraseIdentity(u)
    }
}

/**
 * Utility function for hashing a String using the SHA-256 algorithm. The String is first converted to a byte array
 * using the UTF-8 charset.
 */
// TODO replace by common util func
private fun String.hashSha256(): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(this.toByteArray(StandardCharsets.UTF_8))