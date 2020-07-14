/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord.cmd

import org.epilink.bot.db.LinkUser
import org.epilink.bot.discord.*
import org.koin.core.KoinComponent
import org.koin.core.inject

class LangCommand : Command, KoinComponent {
    override val name = "lang"
    override val permissionLevel = PermissionLevel.Anyone
    override val requireMonitoredServer = false

    private val client by inject<LinkDiscordClientFacade>()
    private val messages by inject<LinkDiscordMessages>()
    private val i18n by inject<LinkDiscordMessagesI18n>()

    override suspend fun run(
        fullCommand: String,
        commandBody: String,
        sender: LinkUser?,
        senderId: String,
        channelId: String,
        guildId: String?
    ) {
        if (commandBody.isEmpty()) {
            client.sendChannelMessage(channelId, messages.getLangHelpEmbed(i18n.getLanguage(sender?.discordId)))
            return
        }
        if(i18n.setLanguage(senderId, commandBody)) {
            client.sendChannelMessage(channelId, messages.getSuccessCommandReply(i18n.getLanguage(sender?.discordId), "lang.success"))
        } else {
            client.sendChannelMessage(channelId, messages.getErrorCommandReply(i18n.getLanguage(sender?.discordId), "lang.invalidLanguage"))
        }
    }

}