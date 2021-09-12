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
import org.epilink.bot.db.DatabaseFacade
import org.epilink.bot.db.User
import org.epilink.bot.discord.Command
import org.epilink.bot.discord.DiscordClientFacade
import org.epilink.bot.discord.DiscordMessages
import org.epilink.bot.discord.DiscordMessagesI18n
import org.epilink.bot.discord.PermissionLevel

/**
 * The e!lang command that allows any user to change the EpiLink Discord bot's language.
 */
class LangCommand(scope: InjectionScope) : Command {
    override val name = "lang"
    override val permissionLevel = PermissionLevel.Anyone
    override val requireMonitoredServer = false

    private val client by scope<DiscordClientFacade>()
    private val messages by scope<DiscordMessages>()
    private val i18n by scope<DiscordMessagesI18n>()
    private val db by scope<DatabaseFacade>()

    override suspend fun run(
        fullCommand: String,
        commandBody: String,
        sender: User?,
        senderId: String,
        channelId: String,
        guildId: String?
    ) {
        val previousLanguage = i18n.getLanguage(senderId)
        when {
            commandBody.isEmpty() ->
                // e!lang
                client.sendChannelMessage(channelId, messages.getLangHelpEmbed(previousLanguage))
            commandBody == "clear" -> {
                // e!lang clear
                db.clearLanguagePreference(senderId)
                client.sendChannelMessage(
                    channelId,
                    // We don't use previousLanguage because otherwise people may think their language wasn't actually
                    // cleared
                    messages.getSuccessCommandReply(i18n.getLanguage(senderId), "lang.clearSuccess")
                )
            }
            // Try to set the language
            i18n.setLanguage(senderId, commandBody) -> {
                client.sendChannelMessage(
                    channelId,
                    // We don't use previousLanguage because we should send this in the newly chosen language
                    messages.getSuccessCommandReply(i18n.getLanguage(senderId), "lang.success")
                )
            }
            else ->
                client.sendChannelMessage(
                    channelId,
                    messages.getErrorCommandReply(previousLanguage, "lang.invalidLanguage", listOf(commandBody))
                )
        }
    }
}
