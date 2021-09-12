/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord.cmd

import guru.zoroark.shedinja.environment.InjectionScope
import guru.zoroark.shedinja.environment.invoke
import org.epilink.bot.db.User
import org.epilink.bot.discord.Command
import org.epilink.bot.discord.DiscordClientFacade
import org.epilink.bot.discord.DiscordEmbed
import org.epilink.bot.discord.DiscordMessages
import org.epilink.bot.discord.DiscordMessagesI18n
import org.epilink.bot.discord.DiscordTargets
import org.epilink.bot.discord.PermissionLevel
import org.epilink.bot.discord.TargetParseResult
import org.epilink.bot.discord.TargetResult

class CountCommand(scope: InjectionScope) : Command {
    private val msg: DiscordMessages by scope()
    private val i18n: DiscordMessagesI18n by scope()
    private val discord: DiscordClientFacade by scope()
    private val resolver: DiscordTargets by scope()

    override val name = "count"
    override val permissionLevel = PermissionLevel.Admin
    override val requireMonitoredServer = true

    override suspend fun run(
        fullCommand: String,
        commandBody: String,
        sender: User?,
        senderId: String,
        channelId: String,
        guildId: String?
    ) {
        requireNotNull(sender)
        requireNotNull(guildId)

        val result = resolver.parseDiscordTarget(commandBody)
            .takeIf { it is TargetParseResult.Success }
            ?.let { resolver.resolveDiscordTarget(it as TargetParseResult.Success, guildId) }
            ?.takeIf { it !is TargetResult.RoleNotFound }
        val embed = if (result == null) {
            msg.getWrongTargetCommandReply(i18n.getLanguage(senderId), commandBody)
        } else {
            when (result) {
                is TargetResult.Everyone ->
                    success(senderId, discord.getMembers(guildId).size)
                is TargetResult.Role ->
                    success(senderId, discord.getMembersWithRole(result.id, guildId).size)
                is TargetResult.User ->
                    if (discord.isUserInGuild(result.id, guildId)) success(senderId, 1)
                    else msg.getWrongTargetCommandReply(i18n.getLanguage(senderId), commandBody)
                is TargetResult.RoleNotFound ->
                    error("Invalid state: cannot get to TargetResult.RoleNotFound here")
            }
        }
        discord.sendChannelMessage(channelId, embed)
    }

    private suspend fun success(senderId: String, size: Int): DiscordEmbed {
        return msg.getSuccessCommandReply(i18n.getLanguage(senderId), "count.success", listOf(size))
    }
}
