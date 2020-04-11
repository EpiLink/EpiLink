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
 * This interface should be implemented by facades that abstract away an actual Discord client for the EpiLink Discord
 * bot.
 *
 * All of the functions are suspending and, no matter what the original client uses as a method for providing asynchronous
 * operations, the operations of this interface *must not be blocking*, only suspending. They should suspend until the
 * operation has been successfully processed by Discord.
 *
 * Because the bot may have joined guilds it does not have configurations for, the bot has a concept of monitored
 * guilds.
 *
 * - Guilds in which the bot is connected and has configurations for are said to be **monitored**.
 * - Guilds in which the bot is connected but does *not* have configurations for are said to be **unmonitored**.
 * - Guilds in which the bot is not but has configurations for are said to be **orphaned**. Not currently checked.
 */
interface LinkDiscordClientFacade {

    /**
     * Start this client. This function should suspend until the bot is ready to process all other functions.
     */
    suspend fun start()

    /**
     * Send a direct message to a user.
     *
     * @param discordId The Discord user who should receive the message
     * @param embed The embed to send to the user
     */
    suspend fun sendDirectMessage(discordId: String, embed: DiscordEmbed)

    /**
     * Get the guilds the bot is connected to, no matter whether they are monitored or not.
     *
     * @return A list of the IDs of ALL the guilds the bot is connected to.
     */
    suspend fun getGuilds(): List<String>

    /**
     * Checks whether a user is in a given guild or not
     *
     * @param userId The ID of the user to check
     * @param guildId The ID of the guild in which to check the presence of the user
     * @return True if the user with the given ID is present in the guild with the given ID, false otherwise
     */
    suspend fun isUserInGuild(userId: String, guildId: String): Boolean

    /**
     * Get the name of a guild with the given ID
     *
     * @param guildId The guild of which to get the name
     * @return The name of the guild with the given ID
     */
    suspend fun getGuildName(guildId: String): String

    /**
     * General-purpose role management function. Upon being called, the facade should:
     *
     * - Get the membership of the user with the given Discord ID and guild ID
     * - Add all of the given roles in the [toAdd] parameter. The member may already have some of these roles.
     * - Remove all of the given roles in the [toRemove] parameter. The member may already have some of these roles.
     *
     * The last two steps should be done in parallel and, if possible, each role addition/removal should be done in
     * parallel as well. The function should suspend until all of the operations are done.
     *
     * Given that the roles to add and remove may require no change, implementations may skip roles which are already
     * present/absent.
     *
     * @param discordId The ID of the Discord user whose roles should be modified
     * @param guildId The guild in which to modify the roles of the given user
     * @param toAdd Collection of role IDs that should be added to the user. May contain roles the user already has.
     * @param toRemove Collection of role IDs that should be removed from the user. May contain roles the user does not have.
     */
    suspend fun manageRoles(discordId: String, guildId: String, toAdd: Collection<String>, toRemove: Collection<String>)

    /**
     * Get a [DiscordUserInfo] object for the given user ID
     *
     * @param discordId The user of whom information should be gathered
     * @return A [DiscordUserInfo] object giving information on the user
     */
    suspend fun getDiscordUserInfo(discordId: String): DiscordUserInfo
}

/**
 * Implementation-agnostic class for storing user information
 */
data class DiscordUserInfo(
    /**
     * The Discord ID of the user
     */
    val id: String,
    /**
     * The user's username (without the discriminator)
     */
    val username: String,
    /**
     * The user's discriminator (without the `#`)
     */
    val discriminator: String
)

/**
 * Implementation of a Discord client facade that uses Discord4J
 */
internal class LinkDiscord4JFacadeImpl(
    private val discordClientId: String,
    token: String
) :
    LinkDiscordClientFacade, KoinComponent {

    private val roleManager: LinkRoleManager by inject()

    /**
     * Coroutine scope used for firing things in events
     */
    private val scope = CoroutineScope(Dispatchers.Default)

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
            client.getMemberById(Snowflake.of(guildId), Snowflake.of(userId)).awaitSingle()
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
        toAdd: Collection<String>,
        toRemove: Collection<String>
    ) {
        val member = client.getMemberById(Snowflake.of(guildId), Snowflake.of(discordId)).awaitSingle()
        coroutineScope {
            val currentRoles = member.roles.collectList().awaitSingle().map { it.id }
            val adding =
                toAdd.map { Snowflake.of(it) }.minus(currentRoles).map {
                    async { member.addRole(it).await() }
                }
            val removing =
                toAdd.map { Snowflake.of(it) }.intersect(currentRoles).map {
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

    /*
     * try {
                        g.getMemberById(dbUser.discordId).awaitSingle()?.to(g)
                    } catch (ex: ClientException) {
                        // We need to catch the exception when Discord tells us the member could not be found.
                        // This happens when the user is not a member of the guild.
                        // This kind of error has the error code 10007 in the response.
                        if (ex.errorCode == "10007") {
                            null // Simply ignore this one
                        } else {
                            throw LinkException("Unexpected exception upon member retrieval for guild ${g.name}", ex)
                        }
                    }
     */

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