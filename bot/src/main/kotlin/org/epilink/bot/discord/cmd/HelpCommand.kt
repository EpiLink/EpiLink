/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord.cmd

import guru.zoroark.tegral.di.environment.InjectionScope
import guru.zoroark.tegral.di.environment.invoke
import guru.zoroark.tegral.di.environment.named
import org.epilink.bot.db.User
import org.epilink.bot.discord.Command
import org.epilink.bot.discord.DiscordClientFacade
import org.epilink.bot.discord.DiscordMessages
import org.epilink.bot.discord.DiscordMessagesI18n
import org.epilink.bot.discord.PermissionLevel

/**
 * Implementation for the help command
 */
class HelpCommand(scope: InjectionScope) : Command {
    private val client: DiscordClientFacade by scope()
    private val msg: DiscordMessages by scope()
    private val i18n: DiscordMessagesI18n by scope()
    private val admins by scope<List<String>>(named("admins"))

    override val name: String = "help"
    override val permissionLevel = PermissionLevel.Anyone
    override val requireMonitoredServer = false

    override suspend fun run(
        fullCommand: String,
        commandBody: String,
        sender: User?,
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
