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
import discord4j.core.`object`.entity.User as DUser

class LinkRoleManager(
    private val database: LinkServerDatabase,
    private val bot: LinkDiscordBot,
    private val config: LinkDiscordConfig,
    private val rulebook: Rulebook
) {
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

        val roles =
            getRolesForAuthorizedUser(dbUser, discordUser, getRulesRelevantForGuilds(*guilds.toTypedArray()))
        guilds.mapNotNull { g ->
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
        }.forEach { (m, g) ->
            updateAuthorizedUserRoles(m, g, roles)
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
                    getRolesForAuthorizedUser(dbUser, ev.member, getRulesRelevantForGuilds(guild))
                )
                is Disallowed -> bot.sendCouldNotJoin(ev.member, guild, canJoin.reason)
            }
        } else {
            bot.sendGreetings(ev.member, guild)
        }
    }


    private fun getRulesRelevantForGuilds(vararg guilds: Guild): Set<Rule> {
        // Maps role names to their rules in the global config
        val rolesInGlobalConfig = config.roles?.associateBy({ it.name }, { it.rule }) ?: return setOf()
        return guilds
            // Get the guilds' configs
            .map { guild -> config.getConfigForGuild(guild.id.asString()) } // List<Guild config>
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
     * along with the list of rules to execute
     */
    @OptIn(UsesTrueIdentity::class)
    private suspend fun getRolesForAuthorizedUser(
        dbUser: User,
        discordUser: discord4j.core.`object`.entity.User,
        rules: Collection<Rule>
    ): Set<String> = withContext(Dispatchers.IO) {
        val did = discordUser.id.asString()
        val dname = discordUser.username
        val ddisc = discordUser.discriminator
        val identity = if (database.isUserIdentifiable(dbUser)) {
            database.accessIdentity(
                dbUser,
                automated = true,
                author = "EpiLink Discord Bot",
                reason = "EpiLink has accessed your identity automatically in order to update your roles on Discord servers.",
                discord = bot
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
}
