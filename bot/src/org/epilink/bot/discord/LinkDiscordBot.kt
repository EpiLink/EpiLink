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
import org.epilink.bot.config.LinkDiscordServerSpec
import org.epilink.bot.config.rulebook.Rule
import org.epilink.bot.config.rulebook.Rulebook
import org.epilink.bot.config.rulebook.StrongIdentityRule
import org.epilink.bot.config.rulebook.WeakIdentityRule
import org.epilink.bot.db.Allowed
import org.epilink.bot.db.Disallowed
import org.epilink.bot.db.User
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.logger
import org.reactivestreams.Publisher
import java.awt.Color
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KClass
import discord4j.core.`object`.entity.User as DUser

private val ClientException.errorCode: String?
    get() = this.errorResponse?.fields?.get("code")?.toString()

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
    private val discordClientId: String,
    private val rulebook: Rulebook
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
        val discordUser = try {
            client.getUserById(userSnowflake).awaitSingle()
        } catch (ex: ClientException) {
            if (ex.errorCode == "10013") {
                return // User has not joined any guild yet
            } else {
                throw ex
            }
        }
        when (val adv = env.database.canUserJoinServers(dbUser)) {
            is Allowed -> {
                val roles =
                    getRolesForAuthorizedUser(dbUser, discordUser, getRulesRelevantForGuilds(*guilds.toTypedArray()))
                guilds.mapNotNull { g ->
                    try {
                        g.getMemberById(userSnowflake).awaitSingle()?.to(g)
                    } catch (ex: ClientException) {
                        // We need to catch the exception when Discord tells us the member could not be found.
                        // This happens when the user is not a member of the guild.
                        // This kind of error has the error code 10007 in the response.
                        if (ex.errorCode == "10007") {
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
                is Allowed -> updateAuthorizedUserRoles(
                    member, guild, getRolesForAuthorizedUser(dbUser, this.member, getRulesRelevantForGuilds(guild))
                )
                is Disallowed -> sendCouldNotJoin(member, guild, canJoin.reason)
            }
        } else {
            sendGreetings(member, guild)
        }
    }

    private fun getRulesRelevantForGuilds(vararg guilds: Guild): Set<Rule> {
        // Maps role names to their rules in the
        val rolesInGlobalConfig = config.roles?.associateBy({ it.name }, { it.rule }) ?: return setOf()
        return guilds
            // Get the guilds' configs
            .map { guild -> getConfigForGuild(guild.id.asString()) } // List<Guild config>
            // Get the epilink roles required by each guild
            .map { it.roles.keys } // List<List<EpiLink role name>>
            // Get all of that as a single flattened list of roles in the guilds
            .flatten() // List<EpiLink role name>
            // Get rid of the EpiLink implicit roles (_known, _identified, ...)
            .filter { !it.startsWith("_") }
            // Get rid of duplicates, we want a list of distinct roles
            .distinct()
            // Map each role to the name of the rule that defines it
            .mapNotNull {
                rolesInGlobalConfig[it]
            } // List<Rule name>
            // Get rid of duplicates: a rule may define more than one role
            .distinct()
            // Finally, match each rule name to the actual rule
            .mapNotNull {
                rulebook.rules[it]
            } // List<Rule>
            // And return all of that as a set
            .toSet() // Set<Rule>
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
    private suspend fun updateAuthorizedUserRoles(member: Member, guild: Guild, roles: Collection<String>) {
        val guildId = guild.id.asString()
        val serverConfig = getConfigForGuild(guildId)
        coroutineScope { // Put all of that in a scope to avoid leaking the coroutines launched by async
            roles.mapNotNull { serverConfig.roles[it] } // Get roles that can be added
                .map { Snowflake.of(it) } // Turn the IDs to snowflakes
                .map { async { member.addRole(it).await() } } // Add the roles asynchronously
                .awaitAll() // Await all the role additions
        }

    }

    /**
     * Get the role list for a user who we assume has the right to connect to servers (i.e. is not banned),
     * along with the list of rules to execute
     */
    @OptIn(UsesTrueIdentity::class)
    private suspend fun getRolesForAuthorizedUser(
        dbUser: User,
        discordUser: DUser,
        rules: Collection<Rule>
    ): Set<String> = withContext(Dispatchers.IO) {
        val did = discordUser.id.asString()
        val dname = discordUser.username
        val ddisc = discordUser.discriminator
        val identity = if (env.database.isUserIdentifiable(dbUser)) {
            env.database.accessIdentity(
                dbUser,
                automated = true,
                author = "EpiLink Discord Bot",
                reason = "EpiLink has accessed your identity automatically in order to update your roles on Discord servers.",
                discord = this@LinkDiscordBot
            )
        } else null
        val baseSet = if (identity != null) setOf("_known", "_identified") else setOf("_known")
        rules.map { rule ->
            async {
                runCatching {
                    when (rule) {
                        is WeakIdentityRule -> rule.determineRoles(did, dname, ddisc)
                        is StrongIdentityRule ->
                            if (identity == null)
                                listOf()
                            else rule.determineRoles(did, dname, ddisc, identity)
                    }
                }.getOrElse { ex ->
                    logger.error("Failed to apply rule ${rule.name} due to an unexpected exception.", ex)
                    listOf()
                }
            }
        }.awaitAll().flatten().toSet().union(baseSet)
    }

    /**
     * Initial server message sent upon connection with the server not knowing who the person is
     */
    private suspend fun sendGreetings(member: Member, guild: Guild) {
        val guildConfig = getConfigForGuild(guild.id.asString())
        if (!guildConfig.enableWelcomeMessage)
            return

        member.privateChannel.awaitSingle().createEmbed {
            it.from(
                guildConfig.welcomeEmbed ?: DiscordEmbed(
                    title = ":closed_lock_with_key: Authentication required for ${guild.name}",
                    description =
                    """
                    **Welcome to ${guild.name}**. Access to this server is restricted. Please log in using the link
                    below to get full access to the server's channels.
                    """.trimIndent(),
                    fields = run {
                        val ml = mutableListOf<DiscordEmbedField>()
                        if (config.welcomeUrl != null)
                            ml += DiscordEmbedField("Log in", config.welcomeUrl)
                        ml += DiscordEmbedField(
                            "Need help?",
                            "Contact the administrators of ${guild.name} if you need help with the procedure."
                        )
                        ml
                    },
                    footer = DiscordEmbedFooter("Powered by EpiLink"),
                    color = "#ffff00"
                )
            )
        }.awaitSingle()
    }

    /**
     * Retrieve the configuration for a given guild, or throw an error if such a configuration could not be found.
     *
     * Expects the guild to be monitored.
     */
    private fun getConfigForGuild(guildId: String): LinkDiscordServerSpec =
        config.servers?.first { it.id == guildId }
            ?: error("Configuration not found, but guild was expected to be monitored")

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

    suspend fun sendIdentityAccessNotification(discordId: String, automated: Boolean, author: String, reason: String) {
        // TODO properly notify following the privacy options of the backend
        client.getUserById(Snowflake.of(discordId)).awaitSingle().privateChannel.awaitSingle()
            .createMessage(
                "Your identity was accessed by **$author**" + (if (automated) " automatically." else ".") + "\n*$reason*"
            ).awaitSingle()
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