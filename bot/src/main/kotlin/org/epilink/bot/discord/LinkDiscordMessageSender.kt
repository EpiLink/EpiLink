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