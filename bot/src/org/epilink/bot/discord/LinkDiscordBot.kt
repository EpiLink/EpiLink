package org.epilink.bot.discord

import discord4j.core.DiscordClient
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.util.Permission
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.EventDispatcher
import discord4j.core.event.domain.Event
import discord4j.core.event.domain.guild.MemberJoinEvent
import discord4j.core.event.domain.lifecycle.ReadyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.*
import org.epilink.bot.LinkServerEnvironment
import org.epilink.bot.config.LinkDiscordConfig
import org.epilink.bot.logger
import org.reactivestreams.Publisher
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KClass

/**
 * This class manages the Discord bot for EpiLink
 */
class LinkDiscordBot(
    /**
     * The environment the bot lives in
     */
    private val env: LinkServerEnvironment,
    /**
     * The Discord configuration that is used for checking servers, roles, etc.
     */
    private val config: LinkDiscordConfig,
    /**
     * The token to be used for logging in the bot
     */
    token: String,
    /**
     * The Discord client ID associated with the bot. Used for generating an invite link.
     */
    private val discordClientId: String
) {
    /**
     * Coroutine scope used for firing coroutines as answers to events
     */
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * The actual Discord client
     */
    private val client = DiscordClientBuilder(token).build().apply {
        eventDispatcher.onEvent(MemberJoinEvent::class) { handle() }
    }

    /**
     * Starts the Discord bot, suspending until the bot is ready.
     */
    suspend fun start() {
        client.loginAndAwaitReady()
        logger.info("Discord bot launched, invite link: " + getInviteLink())
    }

    /**
     * Generates an invite link for the bot and returns it
     */
    private fun getInviteLink(): String {
        val permissions = listOf(
            Permission.MANAGE_ROLES,
            Permission.KICK_MEMBERS,
            Permission.CHANGE_NICKNAME,
            Permission.MANAGE_EMOJIS,
            Permission.VIEW_CHANNEL,
            Permission.EMBED_LINKS,
            Permission.READ_MESSAGE_HISTORY,
            Permission.SEND_MESSAGES,
            Permission.ATTACH_FILES,
            Permission.ADD_REACTIONS
        ).map { it.value }.sum().toString()
        return "https://discordapp.com/api/oauth2/authorize?client_id=$discordClientId&scope=bot&permissions=$permissions"
    }

    /**
     * Handler for members joining a guild
     */
    private suspend fun MemberJoinEvent.handle() {
        member.privateChannel.awaitSingle().createMessage("Hello!").awaitSingle()
        if (!isMonitored(this.guildId)) {
            return
        }
        member.privateChannel.awaitSingle().createMessage("You entered a monitored server!").awaitSingle()
    }

    /**
     * Check if a guild is monitored: that is, EpiLink knows how to handle it and is expected to do so.
     */
    private fun isMonitored(guildId: Snowflake): Boolean {
        val id = guildId.asString()
        return config.servers?.any { it.id == id } ?: false
    }

    /**
     * Utility function for registering an event in a less verbose way
     */
    private fun <T : Event> EventDispatcher.onEvent(
        event: KClass<T>,
        handler: suspend T.() -> Unit
    ) {
        on(event.java).subscribe { scope.launch { handler(it) } }
    }
}

/**
 * Logs into the Discord client, suspending until a Ready event is received.
 */
private suspend fun DiscordClient.loginAndAwaitReady() {
    suspendCoroutine<Unit> { cont ->
        this.eventDispatcher.on(ReadyEvent::class.java)
            .subscribe {
                cont.resume(Unit)
            }
        this.login()
            .doOnError {
                cont.resumeWithException(it)
            }.subscribe()
    }
}

/**
 * Awaits the completion of a Publisher<Void>
 */
suspend fun Publisher<Void>.await() {
    if (awaitFirstOrNull() != null) error("Did not expect a return value here")
}