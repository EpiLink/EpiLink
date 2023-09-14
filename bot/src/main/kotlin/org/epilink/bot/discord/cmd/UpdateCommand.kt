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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.epilink.bot.db.User
import org.epilink.bot.debug
import org.epilink.bot.discord.Command
import org.epilink.bot.discord.DiscordClientFacade
import org.epilink.bot.discord.DiscordMessages
import org.epilink.bot.discord.DiscordMessagesI18n
import org.epilink.bot.discord.DiscordTargets
import org.epilink.bot.discord.PermissionLevel
import org.epilink.bot.discord.RoleManager
import org.epilink.bot.discord.TargetParseResult
import org.epilink.bot.discord.TargetResult
import org.slf4j.LoggerFactory

private const val UPDATE_CHUNK_SIZE = 10
private const val UPDATE_DELAY_BETWEEN_INVALIDATION = 20L

/**
 * Implementation of an update command, which can be used to invalidate roles of members.
 */
class UpdateCommand(scope: InjectionScope) : Command {
    private val logger = LoggerFactory.getLogger("epilink.discord.cmd.update")
    private val roleManager: RoleManager by scope()
    private val targetResolver: DiscordTargets by scope()
    private val client: DiscordClientFacade by scope()
    private val msg: DiscordMessages by scope()
    private val i18n: DiscordMessagesI18n by scope()
    private val updateLauncherScope = CoroutineScope(SupervisorJob())

    override val name: String
        get() = "update"

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
        val parsedTarget = targetResolver.parseDiscordTarget(commandBody)
        if (parsedTarget is TargetParseResult.Error) {
            client.sendChannelMessage(
                channelId,
                msg.getWrongTargetCommandReply(i18n.getLanguage(senderId), commandBody)
            )
            return
        }
        val target = targetResolver.resolveDiscordTarget(parsedTarget as TargetParseResult.Success, guildId)
        val (toInvalidate, messageAndReplace) = when (target) {
            is TargetResult.User -> {
                logger.debug {
                    "Updating ${target.id}'s roles globally (cmd from ${sender.discordId} on channel " +
                        "$channelId, guild $guildId)"
                }
                listOf(target.id) to ("update.user" to listOf())
            }

            is TargetResult.Role -> {
                logger.debug {
                    "Updating everyone with role ${target.id} globally (cmd from ${sender.discordId} on " +
                        "channel $channelId, guild $guildId)"
                }
                client.getMembersWithRole(target.id, guildId).let {
                    it to ("update.role" to listOf(target.id, it.size))
                }
            }

            TargetResult.Everyone -> {
                logger.debug {
                    "Updating everyone on server $guildId globally (cmd from ${sender.discordId} on " +
                        "channel $channelId, guild $guildId)"
                }
                client.getMembers(guildId).let {
                    @Suppress("ArrayPrimitive") // Required to use Array<Int> for typing consistency
                    it to ("update.everyone" to listOf(it.size))
                }
            }

            is TargetResult.RoleNotFound -> {
                logger.debug {
                    "Attempted update on invalid target $commandBody (cmd from ${sender.discordId} on " +
                        "channel $channelId, guild $guildId)"
                }
                client.sendChannelMessage(
                    channelId,
                    msg.getWrongTargetCommandReply(i18n.getLanguage(senderId), commandBody)
                )
                return
            }
        }
        val (message, replace) = messageAndReplace
        val messageId = client.sendChannelMessage(
            channelId,
            msg.getSuccessCommandReply(i18n.getLanguage(senderId), message, replace)
        )
        startUpdate(toInvalidate, channelId, messageId)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun startUpdate(
        targets: List<String>,
        originalChannelId: String,
        originalMessageId: String
    ) = updateLauncherScope.launch {
        var errorsEncountered = false
        targets.asSequence().chunked(UPDATE_CHUNK_SIZE).forEach { part ->
            part.map {
                delay(UPDATE_DELAY_BETWEEN_INVALIDATION)
                it to async(SupervisorJob()) { roleManager.invalidateAllRoles(it, false) }
            }.forEach {
                try {
                    it.second.await()
                } catch (e: Exception) {
                    logger.error("Exception caught during role update for user ${it.first}", e)
                    errorsEncountered = true
                }
            }
        }
        // All done
        client.addReaction(originalChannelId, originalMessageId, "✅")
        if (errorsEncountered) {
            client.addReaction(originalChannelId, originalMessageId, "⚠")
        }
    }
}
