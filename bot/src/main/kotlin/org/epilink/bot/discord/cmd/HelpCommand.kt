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
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

/**
 * Implementation for the help command
 */
@OptIn(KoinApiExtension::class)
class HelpCommand : Command, KoinComponent {
    private val client: LinkDiscordClientFacade by inject()
    private val msg: LinkDiscordMessages by inject()
    private val i18n: LinkDiscordMessagesI18n by inject()
    private val admins by inject<List<String>>(named("admins"))

    override val name: String = "help"
    override val permissionLevel = PermissionLevel.Anyone
    override val requireMonitoredServer = false

    override suspend fun run(
        fullCommand: String,
        commandBody: String,
        sender: LinkUser?,
        senderId: String,
        channelId: String,
        guildId: String?
    ) {
        client.sendChannelMessage(
            channelId,
            msg.getHelpMessage(i18n.getLanguage(senderId), sender != null && sender.discordId in admins)
        )
    }
}