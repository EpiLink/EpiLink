package org.epilink.bot.discord

import discord4j.core.DiscordClient
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.util.Permission
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.EventDispatcher
import discord4j.core.event.domain.Event
import discord4j.core.event.domain.guild.MemberJoinEvent
import discord4j.core.event.domain.lifecycle.ReadyEvent
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.epilink.bot.LinkServerEnvironment
import org.epilink.bot.config.LinkDiscordConfig
import org.epilink.bot.db.Allowed
import org.epilink.bot.db.Disallowed
import org.epilink.bot.db.User
import org.epilink.bot.logger
import org.reactivestreams.Publisher
import java.awt.Color
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KClass
import discord4j.core.`object`.entity.User as DUser

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
     * Updates all the roles of a Discord user. If tellUserIfFailed is true, this additionally sends the user a DM
     * on why the roles may have failed to update (e.g. banned user).
     */
    suspend fun updateRoles(dbUser: User, tellUserIfFailed: Boolean) {
        val discordId = dbUser.discordId
        val guilds = getMonitoredGuilds()
        val userSnowflake = Snowflake.of(discordId)
        when (val adv = env.database.canUserJoinServers(dbUser)) {
            is Allowed -> {
                val roles = getRolesForAuthorizedUser(dbUser)
                guilds.mapNotNull { g ->
                    try {
                        g.getMemberById(userSnowflake).awaitSingle()?.to(g)
                    } catch (ex: ClientException) {
                        // We need to catch the exception when Discord tells us the member could not be found.
                        // This happens when the user is not a member of the guild.
                        // This kind of error has the error code 10007 in the response.
                        val resp = ex.errorResponse ?: throw ex
                        if (resp.fields["code"].toString() == "10007") {
                            null // Simply ignore this one
                        } else {
                            throw ex
                        }
                    }
                }.forEach { (m, g) ->
                    updateAuthorizedUserRoles(m, g, roles)
                }
            }
            is Disallowed -> {
                if (tellUserIfFailed)
                    sendCouldNotJoin(client.getUserById(userSnowflake).awaitSingle(), null, adv.reason)
            }
        }
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
        val guild = this.guild.awaitSingle()
        val dbUser = env.database.getUser(member.id.asString())
        if (dbUser != null) {
            when (val canJoin = env.database.canUserJoinServers(dbUser)) {
                is Allowed -> updateAuthorizedUserRoles(member, guild, getRolesForAuthorizedUser(dbUser))
                is Disallowed -> sendCouldNotJoin(member, guild, canJoin.reason)
            }
        } else {
            sendGreetings(member, guild)
        }
    }

    /**
     * Send a DM to the user about why connecting failed
     */
    private suspend fun sendCouldNotJoin(user: DUser, guild: Guild?, reason: String) {
        val guildName = guild?.name ?: "any server"
        user.privateChannel.awaitSingle().createEmbed {
            with(it) {
                setTitle(":x: Could not authenticate on $guildName")
                setDescription("Failed to authenticate you on $guildName. Please contact an administrator if you think that should not be happening.")
                addField("Reason", reason, true)
                setFooter("Powered by EpiLink", null)
                setColor(Color.red)
            }
        }.awaitSingle()
    }

    /**
     * Update the roles of a member on a given guild, based on the given EpiLink roles from the role list.
     */
    private suspend fun updateAuthorizedUserRoles(member: Member, guild: Guild, roles: List<String>) {
        withContext(Dispatchers.Default) {
            val guildId = guild.id.asString()
            val serverConfig = config.servers!!.first { it.id == guildId }
            roles.mapNotNull { serverConfig.roles[it] } // Get roles that can be added
                .map { Snowflake.of(it) } // Turn the IDs to snowflakes
                .map { async { member.addRole(it).await() } } // Add the roles asynchronously
                .forEach { it.await() } // Await all the role additions
        }
    }

    /**
     * Get the role list for a user who we assume has the right to connect to servers (i.e. is not banned)
     */
    private suspend fun getRolesForAuthorizedUser(dbUser: User): List<String> {
        val list = mutableListOf("_known")
        if (env.database.isUserIdentifiable(dbUser))
            list += "_identified"
        return list
    }

    /**
     * Initial server message sent upon connection with the server not knowing who the person is
     */
    private suspend fun sendGreetings(member: Member, guild: Guild) {
        member.privateChannel.awaitSingle().createEmbed {
            with(it) {
                setTitle(":warning: Protected server")
                setDescription(
                    """
                    **Welcome to ${guild.name}**. Access to this server is restricted. Please log in using the link
                    below to get full access to the server's channels.
                    """.trimIndent()
                )
                if (config.welcomeUrl != null)
                    addField("Log in", config.welcomeUrl, true)
                addField(
                    "Need help?",
                    "Contact the administrators of ${guild.name} if you need help with the procedure.",
                    true
                )
                addField(
                    "My data",
                    "Your data is processed following the Terms of Services and Privacy Policy you can review during the registering process.",
                    true
                )
                setFooter("Powered by EpiLink", null)
                setColor(Color.green)
                // TODO set logo if available in config
            }
        }.awaitSingle()
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