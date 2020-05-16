/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import kotlinx.coroutines.*
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.slf4j.LoggerFactory

interface LinkDiscordMessageSender {
    fun sendDirectMessageLater(discordId: String, message: DiscordEmbed): Job
}

internal class LinkDiscordMessageSenderImpl : LinkDiscordMessageSender, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.bot.sender")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, t ->
        logger.error("Unexpected error in coroutine scope", t)
    })
    private val client: LinkDiscordClientFacade by inject()

    override fun sendDirectMessageLater(discordId: String, message: DiscordEmbed): Job =
        scope.launch {
            client.sendDirectMessage(discordId, message)
        }
}