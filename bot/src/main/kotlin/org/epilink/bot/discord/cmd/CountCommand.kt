package org.epilink.bot.discord.cmd

import org.epilink.bot.db.LinkUser
import org.epilink.bot.discord.*
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(KoinApiExtension::class)
class CountCommand : Command, KoinComponent {
    private val msg: LinkDiscordMessages by inject()
    private val i18n: LinkDiscordMessagesI18n by inject()
    private val discord: LinkDiscordClientFacade by inject()

    override val name = "count"
    override val permissionLevel = PermissionLevel.Admin
    override val requireMonitoredServer = true

    override suspend fun run(
        fullCommand: String,
        commandBody: String,
        sender: LinkUser?,
        senderId: String,
        channelId: String,
        guildId: String?
    ) {
        requireNotNull(sender)
        requireNotNull(guildId)
        val embed = msg.getWrongTargetCommandReply(i18n.getLanguage(senderId), commandBody)
        discord.sendChannelMessage(channelId, embed)

    }
}
