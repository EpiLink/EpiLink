package org.epilink.bot.discord

import discord4j.core.DiscordClient
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.entity.PrivateChannel
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.util.Permission
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.EventDispatcher
import discord4j.core.event.domain.Event
import discord4j.core.event.domain.guild.MemberJoinEvent
import discord4j.core.event.domain.lifecycle.ReadyEvent
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.epilink.bot.LinkException
import org.epilink.bot.logger
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.reactivestreams.Publisher
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

    private val roleManager: LinkRoleManager by inject()

    /**
     * Coroutine scope used for firing things in events
     */
    private val scope =
        CoroutineScope(Dispatchers.Default)

    /**
     * The actual Discord client
     */
    private val client = DiscordClientBuilder(token).build().apply {
        eventDispatcher.onEvent(MemberJoinEvent::class) { handle() }
    }

    override suspend fun sendDirectMessage(discordId: String, embed: DiscordEmbed) {
        sendDirectMessage(client.getUserById(Snowflake.of(discordId)).awaitSingle()) { from(embed) }
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
            client.getMemberById(
                Snowflake.of(guildId),
                Snowflake.of(userId)
            ).awaitSingle()
            true
        } catch (ex: ClientException) {
            if (ex.errorCode == "10007") {
                false
            } else {
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
        val member = client.getMemberById(
            Snowflake.of(guildId),
            Snowflake.of(discordId)
        ).awaitSingle()
        coroutineScope {
            val currentRoles = member.roles.collectList().awaitSingle().map { it.id }
            val adding =
                toAdd.map { Snowflake.of(it) }.minus(currentRoles).map {
                    async { member.addRole(it).await() }
                }
            val removing =
                toAdd.map { Snowflake.of(it) }.intersect(currentRoles)
                    .map {
                        async { member.removeRole(it).await() }
                    }
            (adding + removing).awaitAll()
        }
    }

    override suspend fun getDiscordUserInfo(discordId: String): DiscordUserInfo {
        val user = client.getUserById(Snowflake.of(discordId)).awaitSingle()
        return DiscordUserInfo(
            user.id.asString(),
            user.username,
            user.discriminator
        )
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
        roleManager.handleNewUser(guildId.asString(), guild.awaitSingle().name, member.id.asString())
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
                .subscribe {
                    cont.resume(Unit)
                }
            this.login()
                .doOnError {
                    logger.error("Encountered general Discord error", it)
                }.subscribe()
        }
    }

    /**
     * Awaits the completion of a Publisher<Void>
     */
    suspend fun Publisher<Void>.await() {
        if (awaitFirstOrNull() != null) error("Did not expect a return value here")
    }

    private val ClientException.errorCode: String?
        get() = this.errorResponse?.fields?.get("code")?.toString()

}