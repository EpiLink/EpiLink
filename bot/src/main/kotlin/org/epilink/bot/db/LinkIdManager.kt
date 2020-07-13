/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.db

import org.epilink.bot.*
import org.epilink.bot.StandardErrorCodes.IdentityAlreadyKnown
import org.epilink.bot.StandardErrorCodes.NewIdentityDoesNotMatch
import org.epilink.bot.config.LinkPrivacy
import org.epilink.bot.discord.LinkDiscordMessageSender
import org.epilink.bot.discord.LinkDiscordMessages
import org.epilink.bot.http.data.IdAccess
import org.epilink.bot.http.data.IdAccessLogs
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.slf4j.LoggerFactory

/**
 * Component that implements ID accessing logic
 */
interface LinkIdManager {
    /**
     * Access the identity of the target.
     *
     * @param user The user whose identity should be retrieved
     * @param automated True if this action is done automatically, false if it is made from an admin's request
     * @param author The person who requested the identity. May be displayed to the user depending on the server's
     * privacy settings.
     * @param reason The reason for the identity access. Will be displayed to the user
     * @return The identity of the user (e-mail address)
     * @throws LinkException if the user is not identifiable (i.e. does not have their identity stored in the database)
     */
    @UsesTrueIdentity
    suspend fun accessIdentity(user: LinkUser, automated: Boolean, author: String, reason: String): String


    /**
     * Get the identity access logs as an [IdAccessLogs] object, ready to be sent.
     */
    suspend fun getIdAccessLogs(user: LinkUser): IdAccessLogs

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
     * @param user The user of whom we want to relink the identity
     * @param email The new e-mail address
     * @param associatedMsftId The Microsoft ID (not hashed) associated with the new e-mail address
     * @throws LinkEndpointException If the identity of the user is already known, or if the given new ID does not
     * match the previous one
     */
    @UsesTrueIdentity
    suspend fun relinkMicrosoftIdentity(user: LinkUser, email: String, associatedMsftId: String)

    /**
     * Delete the identity of the user with the given Discord ID from the database, or throw a [LinkEndpointException]
     * if no such identity exists.
     *
     * @param user The user whose identity we should remove
     * @throws LinkEndpointException If the user does not have any identity recorded in the first place.
     */
    @UsesTrueIdentity
    suspend fun deleteUserIdentity(user: LinkUser)
}

internal class LinkIdManagerImpl : LinkIdManager, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.idaccessor")
    private val facade: LinkDatabaseFacade by inject()
    private val messages: LinkDiscordMessages by inject()
    private val discordSender: LinkDiscordMessageSender by inject()
    private val privacy: LinkPrivacy by inject()


    @UsesTrueIdentity
    override suspend fun accessIdentity(user: LinkUser, automated: Boolean, author: String, reason: String): String {
        // TODO replace all the exceptions with a distinct return value (sealed class or something)
        //      This should probably be done in the facade implementation itself though
        if (!facade.isUserIdentifiable(user)) {
            throw LinkException("User is not identifiable")
        }
        val embed = messages.getIdentityAccessEmbed(automated, author, reason)
        if (embed != null)
            discordSender.sendDirectMessageLater(user.discordId, embed)
        return facade.getUserEmailWithAccessLog(user, automated, author, reason)
    }

    @UsesTrueIdentity
    override suspend fun relinkMicrosoftIdentity(user: LinkUser, email: String, associatedMsftId: String) {
        if (facade.isUserIdentifiable(user)) {
            throw LinkEndpointUserException(IdentityAlreadyKnown)
        }
        val knownHash = user.msftIdHash
        val newHash = associatedMsftId.hashSha256()
        if (!knownHash.contentEquals(newHash)) {
            throw LinkEndpointUserException(NewIdentityDoesNotMatch)
        }
        facade.recordNewIdentity(user, email)
    }

    override suspend fun getIdAccessLogs(user: LinkUser): IdAccessLogs =
        IdAccessLogs(
            manualAuthorsDisclosed = privacy.shouldDiscloseIdentity(false),
            accesses = facade.getIdentityAccessesFor(user).map { a ->
                IdAccess(
                    a.automated,
                    a.authorName.takeIf { privacy.shouldDiscloseIdentity(a.automated) },
                    a.reason,
                    a.timestamp.toString()
                )
            }.also { logger.debug { "Acquired access logs for ${user.discordId}" } }
        )

    @UsesTrueIdentity
    override suspend fun deleteUserIdentity(user: LinkUser) {
        if (!facade.isUserIdentifiable(user))
            throw LinkEndpointUserException(StandardErrorCodes.IdentityAlreadyUnknown)
        facade.eraseIdentity(user)
    }
}