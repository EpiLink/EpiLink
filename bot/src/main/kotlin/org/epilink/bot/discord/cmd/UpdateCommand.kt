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
import org.epilink.bot.debug
import org.epilink.bot.discord.*
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.slf4j.LoggerFactory

/**
 * Implementation of an update command, which can be used to invalidate roles of members.
 */
class UpdateCommand : Command, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.discord.cmd.update")
    private val roleManager: LinkRoleManager by inject()
    private val targetResolver: LinkDiscordTargets by inject()
    private val client: LinkDiscordClientFacade by inject()
    private val msg: LinkDiscordMessages by inject()

    override val name: String
        get() = "update"

    override val permissionLevel = PermissionLevel.Admin

    override val requireMonitoredServer = true

    override suspend fun run(
        fullCommand: String,
        commandBody: String,
        sender: LinkUser?,
        channelId: String,
        guildId: String?
    ) {
        requireNotNull(sender)
        requireNotNull(guildId)
        val parsedTarget = targetResolver.parseDiscordTarget(commandBody)
        if (parsedTarget is TargetParseResult.Error) {
            client.sendChannelMessage(channelId, msg.getWrongTargetCommandReply(commandBody))
            return
        }
        val target = targetResolver.resolveDiscordTarget(parsedTarget as TargetParseResult.Success, guildId)
        val (toInvalidate, message) = when (target) {
            is TargetResult.User -> {
                logger.debug { "Updating ${target.id}'s roles globally (cmd from ${sender.discordId} on channel $channelId, guild $guildId)" }
                listOf(target.id) to "Updating the target's roles globally. This may take some time."
            }
            is TargetResult.Role -> {
                logger.debug { "Updating everyone with role ${target.id} globally (cmd from ${sender.discordId} on channel $channelId, guild $guildId)" }
                client.getMembersWithRole(target.id, guildId).let {
                    it to "Updating members with role ${target.id} (${it.size} total) globally. This may take some time."
                }
            }
            TargetResult.Everyone -> {
                logger.debug { "Updating everyone on server $guildId globally (cmd from ${sender.discordId} on channel $channelId, guild $guildId)" }
                client.getMembers(guildId).let {
                    it to "Updating everyone present on this server (${it.size} total) globally. This may take some time."
                }
            }
            is TargetResult.RoleNotFound -> {
                logger.debug { "Attempted update on invalid target $commandBody (cmd from ${sender.discordId} on channel $channelId, guild $guildId)" }
                client.sendChannelMessage(channelId, msg.getWrongTargetCommandReply(commandBody))
                return
            }
        }
        toInvalidate.forEach { roleManager.invalidateAllRoles(it, false) }
        client.sendChannelMessage(channelId, msg.getSuccessCommandReply(message))
    }
}