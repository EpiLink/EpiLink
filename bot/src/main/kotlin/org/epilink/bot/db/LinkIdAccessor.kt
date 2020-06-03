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
import org.epilink.bot.discord.LinkDiscordMessageSender
import org.epilink.bot.discord.LinkDiscordMessages
import org.koin.core.KoinComponent
import org.koin.core.inject

/**
 * Component that implements ID accessing logic
 */
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
}

internal class LinkIdAccessorImpl : LinkIdAccessor, KoinComponent {
    private val db: LinkServerDatabase by inject()
    private val messages: LinkDiscordMessages by inject()
    private val discordSender: LinkDiscordMessageSender by inject()

    @UsesTrueIdentity
    override suspend fun accessIdentity(targetId: String, automated: Boolean, author: String, reason: String): String {
        // TODO replace all the exceptions with a distinct return value (sealed class or something)
        val u = db.getUser(targetId) ?: throw LinkException("User does not exist")
        if (!db.isUserIdentifiable(targetId)) {
            throw LinkException("User is not identifiable")
        }
        val embed = messages.getIdentityAccessEmbed(automated, author, reason)
        if (embed != null)
            discordSender.sendDirectMessageLater(targetId, embed)
        return db.accessIdentity(u, automated, author, reason)
    }
}