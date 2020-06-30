/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import discord4j.core.DiscordClient
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.entity.PrivateChannel
import discord4j.core.`object`.entity.TextChannel
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.util.Permission
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.EventDispatcher
import discord4j.core.event.domain.Event
import discord4j.core.event.domain.guild.MemberJoinEvent
import discord4j.core.event.domain.lifecycle.ReadyEvent
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.epilink.bot.LinkException
import org.epilink.bot.debug
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KClass

/**
 * Implementation of a Discord client facade that uses Discord4J
 */
internal class LinkDiscord4JFacadeImpl(
    private val discordClientId: String,
    token: String
) : LinkDiscordClientFacade, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.bot.discord4j")
    private val roleManager: LinkRoleManager by inject()
    private val commands: LinkDiscordCommands by inject()

    /**
     * Coroutine scope used for firing things in events
     */
    private val scope =
        CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, ex ->
            logger.error("Uncaught exception in Discord4J client", ex)
        })

    /**
     * The actual Discord client
     */
    private val client = DiscordClientBuilder.create(token).build().apply {
        eventDispatcher.onEvent(MemberJoinEvent::class) { handle() }
        eventDispatcher.onEvent(MessageCreateEvent::class) { handle() }
    }

    override suspend fun sendDirectMessage(discordId: String, embed: DiscordEmbed) {
        sendDirectMessage(client.getUserById(Snowflake.of(discordId)).awaitSingle()) { from(embed) }
    }

    override suspend fun sendChannelMessage(channelId: String, embed: DiscordEmbed) {
        val channel =
            client.getChannelById(Snowflake.of(channelId)).awaitSingle() as? TextChannel ?: error("Not a text channel")
        channel.createEmbed { it.from(embed) }.awaitSingle()
    }

    private suspend fun sendDirectMessage(discordUser: User, embed: EmbedCreateSpec.() -> Unit) =
        discordUser.getCheckedPrivateChannel()
            .createEmbed(embed).awaitSingle()

    override suspend fun getGuilds(): List<String> =
        client.guilds.map { it.id.asString() }.collectList().awaitSingle()

    override suspend fun start() {
        client.loginAndAwaitReady()
        logger.info("Discord bot launched, invite link: " + getInviteLink())
    }

    override suspend fun isUserInGuild(userId: String, guildId: String): Boolean {
        return try {
            logger.debug { "Checking if $userId is in guild $guildId" }
            client.getMemberById(
                Snowflake.of(guildId),
                Snowflake.of(userId)
            ).awaitSingle()
            logger.debug { "$userId is in guild $guildId" }
            true
        } catch (ex: ClientException) {
            if (ex.errorCode == "10007") {
                logger.debug { "$userId is NOT in guild $guildId" }
                false
            } else {
                logger.error("Unexpected reply on isUserInGuild($userId) (error code ${ex.errorCode})")
                throw ex
            }
        }
    }

    override suspend fun getGuildName(guildId: String): String =
        client.getGuildById(Snowflake.of(guildId)).awaitSingle().name

    override suspend fun manageRoles(
        discordId: String,
        guildId: String,
        toAdd: Set<String>,
        toRemove: Set<String>
    ) {
        logger.debug {
            """
            Role transaction for user $discordId in guild $guildId 
                [+]    To add: ${toAdd.joinToString(", ").orIfEmpty("(none)")}
                [-] To remove: ${toRemove.joinToString(", ").orIfEmpty("(none)")}
            """.trimIndent()
        }
        val member = client.getMemberById(
            Snowflake.of(guildId),
            Snowflake.of(discordId)
        ).awaitSingle()
        coroutineScope {
            val currentRoles = member.roles.collectList().awaitSingle().map { it.id }
            val adding =
                toAdd.map { Snowflake.of(it) }
                    .minus(currentRoles)
                    .also { set ->
                        if (set.isNotEmpty())
                            logger.debug { "Will only add " + set.joinToString(", ") { it.asString() } }
                    }
                    .map { async { member.addRole(it).await() } }
            val removing =
                toRemove.map { Snowflake.of(it) }
                    .intersect(currentRoles)
                    .also { set ->
                        if (set.isNotEmpty())
                            logger.debug { "Will only remove " + set.joinToString(", ") { it.asString() } }
                    }
                    .map { async { member.removeRole(it).await() } }
            if (adding.isEmpty() && removing.isEmpty()) {
                logger.debug { "Nothing to change" }
            } else {
                (adding + removing).awaitAll()
            }
        }
    }

    override suspend fun getDiscordUserInfo(discordId: String): DiscordUserInfo {
        logger.debug { "Retrieving user info for $discordId" }
        val user = client.getUserById(Snowflake.of(discordId)).awaitSingle()
        return DiscordUserInfo(
            user.id.asString(),
            user.username,
            user.discriminator
        ).also { logger.debug { "Received $it" } }
    }

    override suspend fun getRoleIdByName(roleName: String, guildId: String): String? {
        val guild = client.getGuildById(Snowflake.of(guildId)).awaitSingle()
        val foundRole = guild.roles.filter { it.name == roleName }.awaitFirstOrNull()
        return foundRole?.id?.asString()
    }

    // TODO handle when role ID does not exist
    override suspend fun getMembersWithRole(roleId: String, guildId: String): List<String> {
        val guild = client.getGuildById(Snowflake.of(guildId)).awaitSingle()
        val members = guild.members.collectList().awaitSingle()
        val roleSnowflake = Snowflake.of(roleId)
        return coroutineScope {
            members.map { m ->
                async {
                    if (m.roles.any { it.id == roleSnowflake }.awaitSingle())
                        m.id.asString()
                    else
                        null
                }
            }.awaitAll().filterNotNull()
        }
    }

    override suspend fun getMembers(guildId: String): List<String> =
        client.getGuildById(Snowflake.of(guildId)).awaitSingle()
            .members.map { it.id.asString() }.collectList().awaitSingle()

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
        roleManager.handleNewUser(guildId.asString(), guild.awaitSingle().name, member.id.asString())
    }

    private suspend fun MessageCreateEvent.handle() {
        commands.handleMessage(
            message.content.orElse(null) ?: return,
            message.author.orElse(null)?.id?.asString() ?: return,
            message.channelId.asString(),
            message.guild.awaitFirstOrNull()?.id?.asString() ?: return
        )
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

    /**
     * @throws UserDoesNotAcceptPrivateMessagesException if the user does not accept private messages
     * @throws LinkException if an unexpected exception happens upon retrieval of the private channel
     */
    private suspend fun User.getCheckedPrivateChannel(): PrivateChannel =
        try {
            this.privateChannel.awaitSingle()
        } catch (ex: ClientException) {
            if (ex.errorCode == "50007")
                throw UserDoesNotAcceptPrivateMessagesException(ex)
            else throw LinkException("Unexpected exception on private channel retrieval", ex)
        }

    /**
     * Logs into the Discord client, suspending until a Ready event is received.
     */
    private suspend fun DiscordClient.loginAndAwaitReady() {
        suspendCoroutine<Unit> { cont ->
            this.eventDispatcher.on(ReadyEvent::class.java)
                .take(1)
                .subscribe({
                    logger.debug { "Discord client has signaled that it is ready" }
                    cont.resume(Unit)
                }, {
                    // I don't think that can happen, but let's log it either way
                    logger.error("Unexpected exception in Discord ready event receiver", it)
                })
            this.login()
                .doOnError {
                    logger.error("Encountered general Discord error", it)
                }.subscribe()
        }
    }

    /**
     * Awaits the completion of a Publisher<Void>
     */
    private suspend fun Publisher<Void>.await() {
        if (awaitFirstOrNull() != null) error("Did not expect a return value here")
    }

    private val ClientException.errorCode: String?
        get() = this.errorResponse?.fields?.get("code")?.toString()

}

private fun String.orIfEmpty(other: String) = if (this.isEmpty()) other else this