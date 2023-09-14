/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import guru.zoroark.tegral.di.environment.InjectionScope
import guru.zoroark.tegral.di.environment.invoke
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * A general purpose class for "sending messages later", with "fire and forget" async logic.
 */
interface DiscordMessageSender {
    /**
     * Send the given message to the user with the given Discord ID at some point in the future.
     *
     * The coroutine is immediately scheduled for execution. It is returned as a Job.
     *
     * @param discordId The ID of the person who this message should be sent to
     * @param message The message ot send
     * @return The task represented as a job
     */
    fun sendDirectMessageLater(discordId: String, message: DiscordEmbed): Job
}

internal class DiscordMessageSenderImpl(scope: InjectionScope) : DiscordMessageSender {
    private val logger = LoggerFactory.getLogger("epilink.bot.sender")

    @Suppress("InjectDispatcher")
    private val scope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, t ->
            logger.error("Unexpected error in coroutine scope", t)
        }
    )
    private val client: DiscordClientFacade by scope()

    override fun sendDirectMessageLater(discordId: String, message: DiscordEmbed): Job =
        scope.launch {
            client.sendDirectMessage(discordId, message)
        }
}
