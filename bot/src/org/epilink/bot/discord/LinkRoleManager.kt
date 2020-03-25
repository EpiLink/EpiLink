package org.epilink.bot.discord

import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.guild.MemberJoinEvent
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitSingle
import org.epilink.bot.config.LinkDiscordConfig
import org.epilink.bot.config.rulebook.Rule
import org.epilink.bot.config.rulebook.Rulebook
import org.epilink.bot.config.rulebook.StrongIdentityRule
import org.epilink.bot.config.rulebook.WeakIdentityRule
import org.epilink.bot.db.*
import org.epilink.bot.logger
import org.koin.core.KoinComponent
import org.koin.core.inject
import discord4j.core.`object`.entity.User as DUser

/**
 * This class is responsible for managing and updating the roles of Discord users.
 */
class LinkRoleManager : KoinComponent {
    private val database: LinkServerDatabase by inject()
    private val bot: LinkDiscordBot by inject()
    private val config: LinkDiscordConfig by inject()
    private val rulebook: Rulebook by inject()

    /**
     * Updates all the roles of a Discord user on a given collection of guilds. The user does not have to be present on
     * the given guilds -- this function handles the case where the member is absent well. If tellUserIfFailed is true,
     * this additionally sends the user a DM on why the roles may have failed to update (e.g. banned user).
     */
    suspend fun updateRolesOnGuilds(
        dbUser: User,
        guilds: Collection<Guild>,
        discordUser: DUser,
        tellUserIfFailed: Boolean
    ) {
        val adv = database.canUserJoinServers(dbUser)
        if (adv is Disallowed) {
            if (tellUserIfFailed)
                bot.sendCouldNotJoin(discordUser, null, adv.reason)
            return
        }
        coroutineScope {
            val connectedToAs = guilds.map { g ->
                async {
                    try {
                        g.getMemberById(discordUser.id).awaitSingle()?.to(g)
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
                }
            }.awaitAll().filterNotNull()
            val guildsToCheck = connectedToAs.map { it.second }
            val rules = getRulesRelevantForGuilds(*guildsToCheck.toTypedArray())
            val roles = getRolesForAuthorizedUser(
                dbUser,
                discordUser,
                rules.map { it.first },
                rules.filter { it.first is StrongIdentityRule }.map { it.second }.flatten().distinct()
            )

            connectedToAs.forEach { (m, g) ->
                updateAuthorizedUserRoles(m, g, roles)
            }
        }
    }

    /**
     * Handles the event where a user joins a monitored server. This assumes that the guild is monitored.
     */
    suspend fun handleNewUser(ev: MemberJoinEvent) {
        val guild = ev.guild.awaitSingle()
        val dbUser = database.getUser(ev.member.id.asString())
        if (dbUser != null) {
            when (val canJoin = database.canUserJoinServers(dbUser)) {
                is Allowed -> updateAuthorizedUserRoles(
                    ev.member,
                    guild,
                    getRolesForAuthorizedUser(
                        dbUser,
                        ev.member,
                        getRulesRelevantForGuilds(guild).map { it.first },
                        // We can put the guild name here even if the guild does not require strong rules
                        // because this argument is ignored if no such rule is used.
                        // So, either a strong rule is used and the only guild it can be used on is [guild], or
                        // there is no strong rule at all and this is ignored. So we can just pass the guild in
                        // either way.
                        listOf(guild.name)
                    )
                )
                is Disallowed -> bot.sendCouldNotJoin(ev.member, guild, canJoin.reason)
            }
        } else {
            bot.sendGreetings(ev.member, guild)
        }
    }

    /**
     * Returns a list of every rule that is relevant for the given guilds, paired with which guilds (by name) are
     * *actually* going to be used for each rule.
     *
     * To get only the rules, use the result with `result.map { it.first }`
     *
     * The guild names are only intended for displaying them as part of ID access notifications.
     */
    private suspend fun getRulesRelevantForGuilds(
        vararg guilds: Guild
    ): List<Pair<Rule, Set<String>>> = withContext(Dispatchers.Default) {
        // Maps role names to their rules in the global config
        val rolesInGlobalConfig = config.roles.associateBy({ it.name }, { it.rule })
        guilds
            // Get each guild's config, paired with the guild's name
            .map { it.name to config.getConfigForGuild(it.id.asString()) } // List<Pair<Guild name, Guild config>>
            // Get the EpiLink roles required by each guild, paired with the guild's name
            .flatMap { (name, cfg) -> cfg.roles.keys.map { it to name } } // List<Pair<Role name, Guild name>>
            // .also { println("#### 1 #### $it") }
            // Group using the role name, mapped to the list of guild names
            // Semantics: A map of the role names and the guilds that wish to use these roles
            .groupBy({ it.first }, { it.second }) // Map<Role name, List<Guild name>>
            // .also { println("#### 2 #### $it") }
            // Get rid of the EpiLink implicit roles (_known, _identified, ...)
            .minus(StandardRoles.values().map { it.roleName }) // Map<Role name, List<Guild name>>
            // .also { println("#### 3 ####$it") }
            // Map each role to the name of the rule that defines it
            // Semantics: A list of rules paired with the guilds that require this rule.
            .mapNotNull {
                rolesInGlobalConfig[it.key]?.to(it.value)
            } // List<Pair<Rule name, List<Guild name>>>
            // Turn this into a clean map
            .groupBy({ it.first }, { it.second }) // Map<Rule name, List<List<Guild name>>>
            // Clean the set of guild names
            // Semantics: A map of each rule name and the guilds that require that rule.
            .mapValues { it.value.flatten().toSet() } // Map<Rule name, Set<Guild name>>
            // .also { println("#### 4 ####$it") }
            // Finally, match each rule name to the actual rule object.
            // Semantics: The list of each rule paired with the guilds that require that rule.
            .mapNotNull {
                rulebook.rules[it.key]?.to(it.value)
            } // List<Pair<Rule, Set<String>>>
        // .also { println("#### F ####$it") }
    }

    /**
     * Update the roles of a member on a given guild, based on the given EpiLink roles from the role list.
     */
    private suspend fun updateAuthorizedUserRoles(member: Member, guild: Guild, roles: Collection<String>) {
        val guildId = guild.id.asString()
        val serverConfig = config.getConfigForGuild(guildId)
        coroutineScope { // Put all of that in a scope to avoid leaking the coroutines launched by async
            roles.mapNotNull { serverConfig.roles[it] } // Get roles that can be added
                .map { Snowflake.of(it) } // Turn the IDs to snowflakes
                .map { async { member.addRole(it).await() } } // Add the roles asynchronously
                .awaitAll() // Await all the role additions
        }

    }

    /**
     * Get the role list for a user who we assume has the right to connect to servers (i.e. is not banned),
     * along with the list of rules to execute.
     *
     * [strongIdGuildNames] should contain the name of the guilds that required strong identity rules. It is ignored
     * if no such rules were used.
     */
    @OptIn(UsesTrueIdentity::class)
    private suspend fun getRolesForAuthorizedUser(
        dbUser: User,
        discordUser: discord4j.core.`object`.entity.User,
        rules: Collection<Rule>,
        strongIdGuildNames: Collection<String>
    ): Set<String> = withContext(Dispatchers.IO) {
        val did = discordUser.id.asString()
        val dname = discordUser.username
        val ddisc = discordUser.discriminator
        val identity =
            if (database.isUserIdentifiable(dbUser) && rules.any { it is StrongIdentityRule }) {
                database.accessIdentity(
                    dbUser,
                    automated = true,
                    author = "EpiLink Discord Bot",
                    reason =
                    "EpiLink has accessed your identity automatically in order to update your roles on the following Discord servers: "
                            + strongIdGuildNames.joinToString(", "),
                    discord = bot
                )
            } else null
        val baseSet =
            if (identity != null) setOf(StandardRoles.Identified.roleName, StandardRoles.Known.roleName)
            else setOf(StandardRoles.Known.roleName)
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
}
