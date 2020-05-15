package org.epilink.bot.db

import org.epilink.bot.LinkException
import org.epilink.bot.discord.LinkDiscordMessageSender
import org.epilink.bot.discord.LinkDiscordMessages
import org.koin.core.KoinComponent
import org.koin.core.inject

interface LinkIdAccessor {
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