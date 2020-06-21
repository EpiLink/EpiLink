/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import org.epilink.bot.config.LinkDiscordConfig
import org.epilink.bot.config.isMonitored
import org.epilink.bot.db.AdminStatus.*
import org.epilink.bot.db.AdminStatus.NotAdmin
import org.epilink.bot.db.LinkDatabaseFacade
import org.epilink.bot.db.LinkPermissionChecks
import org.epilink.bot.db.LinkUser
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.debug
import org.epilink.bot.discord.MessageAcceptStatus.*
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.core.qualifier.named
import org.slf4j.LoggerFactory

/**
 * Component for receiving and handling Discord commands
 */
interface LinkDiscordCommands {
    /**
     * Handle the given message:
     *
     * - Determine if it should be ran or not
     * - Run it if everything looks good
     */
    suspend fun handleMessage(message: String, senderId: String, channelId: String, serverId: String)
}

/**
 * Indication on whether a message should be accepted or not
 */
sealed class MessageAcceptStatus {
    /**
     * The message is not a command and should therefore not be accepted. Should be silent.
     */
    object NotACommand : MessageAcceptStatus()

    /**
     * The sender is not an admin and the message should therefore not be accepted.
     */
    object NotAdmin : MessageAcceptStatus()

    /**
     * The sender is not registered and the message should therefore not be accepted
     */
    object NotRegistered : MessageAcceptStatus()

    /**
     * The sender is an admin but does not have their identity recorded and the message should therefore not be accepted
     */
    object AdminNoIdentity : MessageAcceptStatus()

    /**
     * The server the message was sent on is not monitored and the message should therefore not be accepted.
     */
    object ServerNotMonitored : MessageAcceptStatus()

    /**
     * The message should be accepted.
     *
     * @property user The sender, as a [LinkUser] database object.
     */
    class Accept(val user: LinkUser) : MessageAcceptStatus()
}

/**
 * Context given for a command.
 */
class CommandContext(
    /**
     * The full command message. Contains the prefix and the command name
     */
    @Suppress("unused") val fullCommand: String,
    /**
     * The body of the command: that is, the command message without the prefix and command name.
     *
     * For example, if the full command is `"e!hello world hi"`, commandBody would be `"world hi"`.
     */
    val commandBody: String,

    /**
     * The "sender", the user who sent the command.
     */
    val sender: LinkUser,

    /**
     * The ID of the channel where the message was sent.
     */
    val channelId: String,

    /**
     * The ID of the guild where the message was sent.
     */
    val guildId: String
)

/**
 * An EpiLink Discord command.
 */
class Command(
    /**
     * The name of the command
     */
    val name: String,
    /**
     * The action, which is just a lambda that is ran when the command is launched.
     */
    val action: suspend CommandContext.() -> Unit
)

/**
 * Launch a command with the given parameters. See [CommandContext] for more details on what is what.
 */
suspend fun Command.process(
    fullCommand: String,
    commandBody: String,
    sender: LinkUser,
    channelId: String,
    serverId: String
) =
    this.action(CommandContext(fullCommand, commandBody, sender, channelId, serverId))

internal class LinkDiscordCommandsImpl : LinkDiscordCommands, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.discord.cmd")

    private val discordCfg: LinkDiscordConfig by inject()
    private val admins: List<String> by inject(named("admins"))
    private val db: LinkDatabaseFacade by inject()
    private val permission: LinkPermissionChecks by inject()
    private val client: LinkDiscordClientFacade by inject()
    private val msg: LinkDiscordMessages by inject()
    private val roleManager: LinkRoleManager by inject()
    private val targetResolver: LinkDiscordTargets by inject()

    private val commands = listOf(
        Command("update") {
            val parsedTarget = targetResolver.parseDiscordTarget(commandBody)
            if (parsedTarget is TargetParseResult.Error) {
                client.sendChannelMessage(channelId, msg.getWrongTargetCommandReply(commandBody))
                return@Command
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
                    return@Command
                }
            }
            toInvalidate.forEach { roleManager.invalidateAllRoles(it, false) }
            client.sendChannelMessage(channelId, msg.getSuccessCommandReply(message))
        }
    ).associateBy { it.name }

    override suspend fun handleMessage(message: String, senderId: String, channelId: String, serverId: String) {
        when (val a = shouldAcceptMessage(message, senderId, serverId)) {
            NotACommand -> return // return silently
            MessageAcceptStatus.NotAdmin ->
                client.sendChannelMessage(channelId, msg.getNotAnAdminCommandReply())
            NotRegistered ->
                client.sendChannelMessage(channelId, msg.getNotRegisteredCommandReply())
            AdminNoIdentity ->
                client.sendChannelMessage(channelId, msg.getAdminWithNoIdentityCommandReply())
            ServerNotMonitored ->
                client.sendChannelMessage(channelId, msg.getServerNotMonitoredCommandReply())
            is Accept -> {
                // Do the thing
                val body = message.substring(discordCfg.commandsPrefix.length)
                val result = body.split(' ', limit = 2)
                val commandName = result[0]
                val command = commands[commandName]
                if (command == null) {
                    client.sendChannelMessage(channelId, msg.getInvalidCommandReply(commandName))
                    return
                }
                command.process(message, if (result.size == 1) "" else result[1], a.user, channelId, serverId)
            }
        }
    }

    @OptIn(UsesTrueIdentity::class)
    private suspend fun shouldAcceptMessage(message: String, senderId: String, serverId: String): MessageAcceptStatus {
        if (!message.startsWith(discordCfg.commandsPrefix))
            return NotACommand
        if (!discordCfg.isMonitored(serverId))
            return ServerNotMonitored
        // Quick admin check (avoids checking the database for a user object for obviously-not-an-admin cases)
        if (senderId !in admins) {
            return MessageAcceptStatus.NotAdmin
        }
        val user = db.getUser(senderId) ?: return NotRegistered
        return when (permission.canPerformAdminActions(user)) {
            NotAdmin -> MessageAcceptStatus.NotAdmin
            AdminNotIdentifiable -> AdminNoIdentity
            Admin -> Accept(user)
        }
    }
}