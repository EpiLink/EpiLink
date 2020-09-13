/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import discord4j.common.util.Snowflake
import discord4j.core.DiscordClientBuilder
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.channel.PrivateChannel
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.EventDispatcher
import discord4j.core.event.domain.Event
import discord4j.core.event.domain.guild.MemberJoinEvent
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Permission
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.epilink.bot.LinkException
import org.epilink.bot.debug
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

/**
 * Implementation of a Discord client facade that uses Discord4J
 */
internal class LinkDiscord4JFacadeImpl(
    private val discordClientId: String,
    private val token: String
) : LinkDiscordClientFacade, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.bot.discord4j")
    private val roleManager: LinkRoleManager by inject()
    private val commands: LinkDiscordCommands by inject()
    private val messages: LinkDiscordMessages by inject()

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
    private var cclient: GatewayDiscordClient? = null

    private val client
        get() = cclient ?: error("Discord client uninitialized")

    override suspend fun sendDirectMessage(discordId: String, embed: DiscordEmbed) {
        sendDirectMessage(client.getUserById(Snowflake.of(discordId)).awaitSingle()) { from(embed) }
    }

    override suspend fun sendChannelMessage(channelId: String, embed: DiscordEmbed): String {
        val channel =
            client.getChannelById(Snowflake.of(channelId)).awaitSingle() as? MessageChannel
                ?: error("Not a message channel")
        sendWelcomeMessageIfEmptyDmChannel(channel)
        return channel.createEmbed { it.from(embed) }.awaitSingle().id.asString()
    }

    private suspend inline fun sendWelcomeMessageIfEmptyDmChannel(channel: MessageChannel) {
        if (channel is PrivateChannel && channel.lastMessageId.isEmpty) {
            channel.createEmbed { it.from(messages.getWelcomeChooseLanguageEmbed()) }.awaitSingle()
        }
    }

    private suspend fun sendDirectMessage(discordUser: User, embed: EmbedCreateSpec.() -> Unit) =
        discordUser.getCheckedPrivateChannel().also { sendWelcomeMessageIfEmptyDmChannel(it) }
            .createEmbed(embed).awaitSingle()

    override suspend fun getGuilds(): List<String> =
        client.guilds.map { it.id.asString() }.collectList().awaitSingle()

    override suspend fun start() {
        cclient = DiscordClientBuilder.create(token).build().login().awaitSingle().apply {
            eventDispatcher.onEvent(MemberJoinEvent::class) { handle() }
            eventDispatcher.onEvent(MessageCreateEvent::class) { handle() }
        }

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
                [+]    To add: ${toAdd.joinToString(", ").ifEmpty { "(none)" }}
                [-] To remove: ${toRemove.joinToString(", ").ifEmpty { "(none)" }}
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

    override suspend fun addReaction(channelId: String, messageId: String, reactionUnicode: String) {
        client.getMessageById(Snowflake.of(channelId), Snowflake.of(messageId))
            .awaitSingle()
            .addReaction(ReactionEmoji.of(null, reactionUnicode, false))
            .await()
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
        return "https://discord.com/api/oauth2/authorize?client_id=$discordClientId&scope=bot&permissions=$permissions"
    }

    /**
     * Handler for members joining a guild
     */
    private suspend fun MemberJoinEvent.handle() {
        roleManager.handleNewUser(guildId.asString(), guild.awaitSingle().name, member.id.asString())
    }

    private suspend fun MessageCreateEvent.handle() {
        commands.handleMessage(
            message.content.ifEmpty { return },
            message.author.orElse(null)?.id?.asString() ?: return,
            message.channelId.asString(),
            message.guild.awaitFirstOrNull()?.id?.asString()
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
     * Awaits the completion of a Publisher<Void>
     */
    private suspend fun Publisher<Void>.await() {
        if (awaitFirstOrNull() != null) error("Did not expect a return value here")
    }

    private val ClientException.errorCode: String?
        get() = this.errorResponse.orElse(null)?.fields?.get("code")?.toString()

}
