package org.epilink.bot.discord

import discord4j.core.DiscordClient
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.PrivateChannel
import discord4j.core.`object`.util.Permission
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.EventDispatcher
import discord4j.core.event.domain.Event
import discord4j.core.event.domain.guild.MemberJoinEvent
import discord4j.core.event.domain.lifecycle.ReadyEvent
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.epilink.bot.config.LinkDiscordConfig
import org.epilink.bot.config.LinkDiscordServerSpec
import org.epilink.bot.config.LinkPrivacy
import org.epilink.bot.db.User
import org.epilink.bot.logger
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.reactivestreams.Publisher
import java.awt.Color
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KClass
import discord4j.core.`object`.entity.User as DUser


/**
 * This class manages the Discord bot for EpiLink.
 *
 * Because the bot may have joined guilds it does not have configurations for, the bot has a concept of monitored
 * guilds.
 *
 * - Guilds in which the bot is connected and has configurations for is **monitored**.
 * - Guilds in which the bot is connected but does *not* have configurations for is **unmonitored**.
 * - Guilds in which the bot is not but has configurations for is **orphaned**. Not currently checked.
 */
class LinkDiscordBot(
    /**
     * The token to be used for logging in the bot
     */
    token: String,
    /**
     * The Discord client ID associated with the bot. Used for generating an invite link.
     */
    private val discordClientId: String
) : KoinComponent {
    private val config: LinkDiscordConfig by inject()

    private val privacyConfig: LinkPrivacy by inject()

    private val roleManager: LinkRoleManager by inject()

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
     * Retrieve a list of all of the monitored guilds that the bot is on
     */
    private suspend fun getMonitoredGuilds(): List<Guild> =
        client.guilds.collectList().awaitSingle()
            .filter { isMonitored(it.id) }

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
        if (!isMonitored(this.guildId)) {
            return // Ignore unmonitored guilds' join events
        }
        roleManager.handleNewUser(this)
    }

    /**
     * Send a DM to the user about why connecting failed
     */
    suspend fun sendCouldNotJoin(user: DUser, guild: Guild?, reason: String) {
        val guildName = guild?.name ?: "any server"
        sendDirectMessage(user) {
            setTitle(":x: Could not authenticate on $guildName")
            setDescription("Failed to authenticate you on $guildName. Please contact an administrator if you think that should not be happening.")
            addField("Reason", reason, true)
            setFooter("Powered by EpiLink", null)
            setColor(Color.red)
        }
    }


    /**
     * Initial server message sent upon connection with the server not knowing who the person is
     */
    suspend fun sendGreetings(member: Member, guild: Guild) {
        val guildConfig = config.getConfigForGuild(guild.id.asString())
        if (!guildConfig.enableWelcomeMessage)
            return
        sendDirectMessage(member) {
            guildConfig.welcomeEmbed ?: DiscordEmbed(
                title = ":closed_lock_with_key: Authentication required for ${guild.name}",
                description =
                """
                    **Welcome to ${guild.name}**. Access to this server is restricted. Please log in using the link
                    below to get full access to the server's channels.
                    """.trimIndent(),
                fields = run {
                    val ml = mutableListOf<DiscordEmbedField>()
                    val welcomeUrl = config.welcomeUrl
                    if (welcomeUrl != null)
                        ml += DiscordEmbedField("Log in", welcomeUrl)
                    ml += DiscordEmbedField(
                        "Need help?",
                        "Contact the administrators of ${guild.name} if you need help with the procedure."
                    )
                    ml
                },
                footer = DiscordEmbedFooter("Powered by EpiLink"),
                color = "#ffff00"
            ).let(this::from)
        }
    }

    /**
     * Check if a guild is monitored: that is, EpiLink knows how to handle it and is expected to do so.
     */
    private fun isMonitored(guildId: Snowflake): Boolean {
        val id = guildId.asString()
        return config.servers.any { it.id == id }
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

    suspend fun sendIdentityAccessNotification(discordId: String, automated: Boolean, author: String, reason: String) {
        if (privacyConfig.shouldNotify(automated)) {
            val str = buildString {
                append("Your identity was accessed")
                if (privacyConfig.shouldDiscloseIdentity(automated)) {
                    append(" by *$author*")
                }
                if (automated) {
                    append(" automatically")
                }
                appendln(".")
                appendln("Reason: *$reason*")
            }
            sendDirectMessage(discordId, str)
        }
    }


    private suspend fun sendDirectMessage(discordId: String, embed: EmbedCreateSpec.() -> Unit) =
        sendDirectMessage(client.getUserById(Snowflake.of(discordId)).awaitSingle(), embed)

    private suspend fun sendDirectMessage(discordId: String, message: String) =
        sendDirectMessage(client.getUserById(Snowflake.of(discordId)).awaitSingle(), message)

    private suspend fun sendDirectMessage(discordUser: DUser, message: String) =
        discordUser.getCheckedPrivateChannel()
            .createMessage(message).awaitSingle()

    private suspend fun sendDirectMessage(discordUser: DUser, embed: EmbedCreateSpec.() -> Unit) =
        discordUser.getCheckedPrivateChannel()
            .createEmbed(embed).awaitSingle()

    // TODO move to RoleManager ?
    suspend fun updateRoles(dbUser: User, tellUserIfFailed: Boolean) {
        val discordId = dbUser.discordId
        val guilds = getMonitoredGuilds()
        val userSnowflake = Snowflake.of(discordId)
        val discordUser = try {
            client.getUserById(userSnowflake).awaitSingle()
        } catch (ex: ClientException) {
            if (ex.errorCode == "10013") {
                return // User has not joined any guild yet
            } else {
                throw ex
            }
        }
        roleManager.updateRolesOnGuilds(dbUser, guilds, discordUser, tellUserIfFailed)
    }

    suspend fun launchInScope(function: suspend CoroutineScope.() -> Unit): Job =
        scope.launch { function() }
}

private suspend fun DUser.getCheckedPrivateChannel(): PrivateChannel =
    try {
        this.privateChannel.awaitSingle()
    } catch (ex: ClientException) {
        if (ex.errorCode == "50007")
            throw UserDoesNotAcceptPrivateMessagesException(ex)
        else throw ex
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

/**
 * Retrieve the configuration for a given guild, or throw an error if such a configuration could not be found.
 *
 * Expects the guild to be monitored (i.e. expects a configuration to be present).
 */
fun LinkDiscordConfig.getConfigForGuild(guildId: String): LinkDiscordServerSpec =
    this.servers.firstOrNull { it.id == guildId }
        ?: error("Configuration not found, but guild was expected to be monitored")


internal val ClientException.errorCode: String?
    get() = this.errorResponse?.fields?.get("code")?.toString()