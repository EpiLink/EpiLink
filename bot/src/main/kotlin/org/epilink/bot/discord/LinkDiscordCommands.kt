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
import org.epilink.bot.discord.MessageAcceptStatus.*
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.core.qualifier.named

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
sealed class MessageAcceptStatus(val shouldAccept: Boolean) {
    /**
     * The message is not a command and should therefore not be accepted. Should be silent.
     */
    object NotACommand : MessageAcceptStatus(false)

    /**
     * The sender is not an admin and the message should therefore not be accepted.
     */
    object NotAdmin : MessageAcceptStatus(false)

    /**
     * The sender is not registered and the message should therefore not be accepted
     */
    object NotRegistered : MessageAcceptStatus(false)

    /**
     * The sender is an admin but does not have their identity recorded and the message should therefore not be accepted
     */
    object AdminNoIdentity : MessageAcceptStatus(false)

    /**
     * The server the message was sent on is not monitored and the message should therefore not be accepted.
     */
    object ServerNotMonitored : MessageAcceptStatus(false)

    /**
     * The message should be accepted.
     *
     * @property user The sender, as a [LinkUser] database object.
     */
    class Accept(val user: LinkUser) : MessageAcceptStatus(true)
}

/**
 * Context given for a command.
 */
class CommandContext(
    val fullCommand: String,
    val commandBody: String,
    val sender: LinkUser,
    val channelId: String,
    val serverId: String
)

/**
 * An EpiLink Discord command.
 */
class Command(val name: String, val action: suspend CommandContext.() -> Unit)

suspend fun Command.process(
    fullCommand: String,
    commandBody: String,
    sender: LinkUser,
    channelId: String,
    serverId: String
) =
    this.action(CommandContext(fullCommand, commandBody, sender, channelId, serverId))

internal class LinkDiscordCommandsImpl : LinkDiscordCommands, KoinComponent {
    private val discordCfg: LinkDiscordConfig by inject()
    private val admins: List<String> by inject(named("admins"))
    private val db: LinkDatabaseFacade by inject()
    private val permission: LinkPermissionChecks by inject()
    private val client: LinkDiscordClientFacade by inject()
    private val msg: LinkDiscordMessages by inject()
    private val roleManager: LinkRoleManager by inject()
    private val commands = listOf<Command>(
        Command("update") {
            // Update a single person for now
            val target = Regex("""<@!?(\d+)>""").matchEntire(commandBody)
            if (target == null) {
                client.sendChannelMessage(channelId, msg.getWrongTargetCommandReply(commandBody))
            } else {
                val targetId = target.groups[1]!!.value
                roleManager.invalidateAllRoles(targetId)
                client.sendChannelMessage(
                    channelId,
                    msg.getSuccessCommandReply("Updating the target's roles globally. This may take some time.")
                )

            }
        }
    ).associateBy { it.name }

    /*
     User selector:
     - Ping someone <@userid> (special Discord mention format)
     - Ping a role <@&roleid> (special Discord mention format)
     - By user id userid
     - By role name |rolename
     - By role id /roleid
     - Everyone !everyone
     */

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