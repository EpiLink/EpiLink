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
import org.epilink.bot.StandardErrorCodes.IdentityAlreadyKnown
import org.epilink.bot.StandardErrorCodes.IdentityAlreadyUnknown
import org.epilink.bot.StandardErrorCodes.IdentityRemovalOnCooldown
import org.epilink.bot.StandardErrorCodes.NewIdentityDoesNotMatch
import org.epilink.bot.UserEndpointException
import org.epilink.bot.config.IdentityProviderConfiguration
import org.epilink.bot.config.PrivacyConfiguration
import org.epilink.bot.debug
import org.epilink.bot.discord.DiscordMessageSender
import org.epilink.bot.discord.DiscordMessages
import org.epilink.bot.discord.DiscordMessagesI18n
import org.epilink.bot.http.data.IdAccess
import org.epilink.bot.http.data.IdAccessLogs
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

/**
 * Component that implements ID accessing logic
 */
interface IdentityManager {
    /**
     * Access the identity of the target.
     *
     * @param user The user whose identity should be retrieved
     * @param automated True if this action is done automatically, false if it is made from an admin's request
     * @param author The person who requested the identity. May be displayed to the user depending on the server's
     * privacy settings.
     * @param reason The reason for the identity access. Will be displayed to the user
     * @return The identity of the user (e-mail address)
     * @throws EpiLinkException if the user is not identifiable (i.e. does not have their identity stored in the
     * database)
     */
    @UsesTrueIdentity
    suspend fun accessIdentity(user: User, automated: Boolean, author: String, reason: String): String

    /**
     * Get the identity access logs as an [IdAccessLogs] object, ready to be sent.
     */
    suspend fun getIdAccessLogs(user: User): IdAccessLogs

    /**
     * Record the identity of the user with the given discordId, using the given email. The ID hash given must be the
     * hash associated with the new e-mail address.
     *
     * This function checks if the user already has their identity recorded in the database, in which case this function
     * throws a [UserEndpointException] with error [IdentityAlreadyKnown].
     *
     * This function also checks whether the Identity Provider ID we remember for them matches the new one. If not, this
     * function throws a [UserEndpointException] with error [NewIdentityDoesNotMatch].
     *
     * If all goes well, the user then has a true identity created for them.
     *
     * @param user The user of whom we want to relink the identity
     * @param email The new e-mail address
     * @param associatedIdpId The Identity Provider ID (not hashed) associated with the new e-mail address
     * @throws UserEndpointException If the identity of the user is already known, or if the given new ID does not
     * match the previous one
     */
    @UsesTrueIdentity
    suspend fun relinkIdentity(user: User, email: String, associatedIdpId: String)

    /**
     * Delete the identity of the user with the given Discord ID from the database, or throw a
     * [UserEndpointException] if no such identity exists or the identity deletion is
     * [on cooldown][UnlinkCooldown].
     *
     * @param user The user whose identity we should remove
     * @throws UserEndpointException If the user does not have any identity recorded in the first place.
     */
    @UsesTrueIdentity
    suspend fun deleteUserIdentity(user: User)
}

@OptIn(KoinApiExtension::class)
internal class IdentityManagerImpl : IdentityManager, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.idaccessor")
    private val facade: DatabaseFacade by inject()
    private val messages: DiscordMessages by inject()
    private val i18n: DiscordMessagesI18n by inject()
    private val discordSender: DiscordMessageSender by inject()
    private val privacy: PrivacyConfiguration by inject()
    private val idpConfig: IdentityProviderConfiguration by inject()
    private val cooldown: UnlinkCooldown by inject()

    @UsesTrueIdentity
    override suspend fun accessIdentity(user: User, automated: Boolean, author: String, reason: String): String {
        // TODO replace all the exceptions with a distinct return value (sealed class or something)
        //      This should probably be done in the facade implementation itself though
        if (!facade.isUserIdentifiable(user)) {
            throw EpiLinkException("User is not identifiable")
        }
        val embed = messages.getIdentityAccessEmbed(i18n.getLanguage(user.discordId), automated, author, reason)
        if (embed != null) {
            discordSender.sendDirectMessageLater(user.discordId, embed)
        }
        cooldown.refreshCooldown(user.discordId)
        return facade.getUserEmailWithAccessLog(user, automated, author, reason)
    }

    @UsesTrueIdentity
    override suspend fun relinkIdentity(user: User, email: String, associatedIdpId: String) {
        logger.debug { "Relinking $user with new email $email and IDP id $associatedIdpId" }
        if (facade.isUserIdentifiable(user)) {
            throw UserEndpointException(IdentityAlreadyKnown)
        }
        val knownHash = user.idpIdHash
        val newHash = associatedIdpId.hashSha256()
        if (!knownHash.contentEquals(newHash)) {
            // Upgrade path: For some accounts, the MS Graph API returned an ID format that does not correspond to the
            // OIDC ID (old: non-padded + no hyphens, new: padded with hyphens). Updates to the OIDC-style ID
            if (idpConfig.microsoftBackwardsCompatibility && associatedIdpId.startsWith("00000000-0000-0000-")) {
                // Convert the new ID format to the old one and compare
                val oldIdFormat = associatedIdpId.substringAfter("00000000-0000-0000-").replace("-", "")
                if (oldIdFormat.hashSha256().contentEquals(knownHash)) {
                    // If the "oldified" new one matches, replace by the correct new one
                    logger.info(
                        "Updating hash for user ${user.discordId} due to format changes between Microsoft " +
                            "Graph & OIDC APIs"
                    )
                    // Update known hash
                    facade.updateIdpId(user, newHash)
                } else throw UserEndpointException(NewIdentityDoesNotMatch)
            } else throw UserEndpointException(NewIdentityDoesNotMatch)
        }
        facade.recordNewIdentity(user, email)
        cooldown.refreshCooldown(user.discordId)
    }

    override suspend fun getIdAccessLogs(user: User): IdAccessLogs =
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
    override suspend fun deleteUserIdentity(user: User) {
        if (!facade.isUserIdentifiable(user)) {
            throw UserEndpointException(IdentityAlreadyUnknown)
        }
        if (!cooldown.canUnlink(user.discordId)) {
            throw UserEndpointException(IdentityRemovalOnCooldown)
        }
        facade.eraseIdentity(user)
    }
}
