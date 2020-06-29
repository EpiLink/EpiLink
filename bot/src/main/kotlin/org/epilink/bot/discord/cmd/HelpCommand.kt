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
import org.epilink.bot.discord.Command
import org.epilink.bot.discord.LinkDiscordClientFacade
import org.epilink.bot.discord.LinkDiscordMessages
import org.koin.core.KoinComponent
import org.koin.core.inject

class HelpCommand : Command, KoinComponent {
    private val client: LinkDiscordClientFacade by inject()
    private val msg: LinkDiscordMessages by inject()
    override val name: String = "help"
    override suspend fun run(
        fullCommand: String,
        commandBody: String,
        sender: LinkUser,
        channelId: String,
        guildId: String
    ) {
        client.sendChannelMessage(channelId, msg.getHelpMessage())
    }
}