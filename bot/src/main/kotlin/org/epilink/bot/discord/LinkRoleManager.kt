package org.epilink.bot.discord

import kotlinx.coroutines.*
import org.epilink.bot.LinkEndpointException
import org.epilink.bot.config.LinkDiscordConfig
import org.epilink.bot.config.isMonitored
import org.epilink.bot.config.rulebook.Rule
import org.epilink.bot.config.rulebook.Rulebook
import org.epilink.bot.config.rulebook.StrongIdentityRule
import org.epilink.bot.config.rulebook.WeakIdentityRule
import org.epilink.bot.db.*
import org.epilink.bot.logger
import org.koin.core.KoinComponent
import org.koin.core.inject

/**
 * The role manager interface
 */
interface LinkRoleManager {
    /**
     * Updates all the roles of a Discord user on a given collection of guilds. The user does not have to be present on
     * the given guilds -- this function handles the case where the member is absent well. If tellUserIfFailed is true,
     * this additionally sends the user a DM on why the roles may have failed to update (e.g. banned user).
     *
     * @throws LinkEndpointException If something fails during the member retrieval from each guild.
     */
    suspend fun updateRolesOnGuilds(
        dbUser: LinkUser,
        guilds: Collection<String>,
        tellUserIfFailed: Boolean
    )

    /**
     * Handles the event where a user joins a monitored server. This assumes that the guild is monitored.
     */
    suspend fun handleNewUser(guildId: String, guildName: String, memberId: String)

    /**
     * Run an update of roles in all of the guilds the bot is connected to for the given user elsewhere.
     *
     * This function returns immediately and the operation is ran in a separate coroutine scope.
     */
    fun updateRolesOnAllGuildsLater(u: LinkUser): Job
}

/**
 * This class is responsible for managing and updating the roles of Discord users.
 */
internal class LinkRoleManagerImpl : LinkRoleManager, KoinComponent {
    private val database: LinkServerDatabase by inject()
    private val messages: LinkDiscordMessages by inject()
    private val config: LinkDiscordConfig by inject()
    private val rulebook: Rulebook by inject()
    private val facade: LinkDiscordClientFacade by inject()
    private val scope = CoroutineScope(Dispatchers.Default)

    override suspend fun updateRolesOnGuilds(
        dbUser: LinkUser,
        guilds: Collection<String>,
        tellUserIfFailed: Boolean
    ) {
        val adv = database.canUserJoinServers(dbUser)
        if (adv is Disallowed) {
            if (tellUserIfFailed)
                facade.sendDirectMessage(
                    dbUser.discordId,
                    messages.getCouldNotJoinEmbed("any server at all", adv.reason)
                )
            return
        }
        coroutineScope {
            val whereConnected =
                guilds.filter { config.isMonitored(it) && facade.isUserInGuild(dbUser.discordId, it) }
            val rules = getRulesRelevantForGuilds(*whereConnected.toTypedArray())
            val roles = getRolesForAuthorizedUser(
                dbUser,
                rules.map { it.first },
                rules.filter { it.first is StrongIdentityRule }
                    .flatMap { it.second }.distinct().map { facade.getGuildName(it) }
            )

            whereConnected.forEach { guildId ->
                updateAuthorizedUserRoles(dbUser.discordId, guildId, roles)
            }
        }
    }

    override suspend fun handleNewUser(guildId: String, guildName: String, memberId: String) {
        if (!config.isMonitored(guildId)) {
            return // Ignore unmonitored guilds' join events
        }
        val dbUser = database.getUser(memberId)
        if (dbUser != null) {
            when (val canJoin = database.canUserJoinServers(dbUser)) {
                is Allowed -> updateAuthorizedUserRoles(
                    memberId,
                    guildId,
                    getRolesForAuthorizedUser(
                        dbUser,
                        getRulesRelevantForGuilds(guildId).map { it.first },
                        // We can put the guild name here even if the guild does not require strong rules
                        // because this argument is ignored if no such rule is used.
                        // So, either a strong rule is used and the only guild it can be used on is [guild], or
                        // there is no strong rule at all and this is ignored. So we can just pass the guild in
                        // either way.
                        listOf(guildName)
                    )
                )
                is Disallowed -> facade.sendDirectMessage(
                    memberId,
                    messages.getCouldNotJoinEmbed(guildName, canJoin.reason)
                )
            }
        } else {
            messages.getGreetingsEmbed(guildId, guildName)?.let { facade.sendDirectMessage(memberId, it) }
        }
    }

    override fun updateRolesOnAllGuildsLater(u: LinkUser): Job =
        scope.launch {
            updateRolesOnGuilds(u, facade.getGuilds(), true)
        }

    /**
     * Returns a list of every rule that is relevant for the given guilds, paired with which guilds (by ID) are
     * *actually* going to be used for each rule.
     *
     * To get only the rules, use the result with `result.map { it.first }`
     *
     * The guild IDs are only intended for displaying them (or the name of the guild) as part of ID access notifications.
     *
     * @param guilds Vararg of guildIDs
     */
    private suspend fun getRulesRelevantForGuilds(
        vararg guilds: String
    ): List<Pair<Rule, Set<String>>> = withContext(Dispatchers.Default) {
        // Maps role names to their rules in the global config
        val rolesInGlobalConfig = config.roles.associateBy({ it.name }, { it.rule })
        guilds
            // Get each guild's config, paired with the guild's name
            .map { it to config.getConfigForGuild(it) } // List<Pair<Guild ID, Guild config>>
            // Get the EpiLink roles required by each guild, paired with the guild's ID
            .flatMap { (id, cfg) -> cfg.roles.keys.map { it to id } } // List<Pair<Role name, Guild ID>>
            // .also { println("#### 1 #### $it") }
            // Group using the role name, mapped to the list of guild IDs
            // Semantics: A map of the role names and the guilds that wish to use these roles
            .groupBy({ it.first }, { it.second }) // Map<Role name, List<Guild ID>>
            // .also { println("#### 2 #### $it") }
            // Get rid of the EpiLink implicit roles (_known, _identified, ...)
            .minus(StandardRoles.values().map { it.roleName }) // Map<Role name, List<Guild ID>>
            // .also { println("#### 3 ####$it") }
            // Map each role to the name of the rule that defines it
            // Semantics: A list of rules paired with the guilds that require this rule.
            .mapNotNull {
                rolesInGlobalConfig[it.key]?.to(it.value)
            } // List<Pair<Rule name, List<Guild ID>>>
            // Turn this into a clean map
            .groupBy({ it.first }, { it.second }) // Map<Rule name, List<List<Guild ID>>>
            // Clean the set of guild names
            // Semantics: A map of each rule name and the guilds that require that rule.
            .mapValues { it.value.flatten().toSet() } // Map<Rule name, Set<Guild ID>>
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
    private suspend fun updateAuthorizedUserRoles(discordId: String, guildId: String, roles: Collection<String>) {
        val serverConfig = config.getConfigForGuild(guildId)
        val toObtain = roles.mapNotNull { serverConfig.roles[it] }.toSet()
        val toRemove = serverConfig.roles.values.toSet().minus(toObtain)
        facade.manageRoles(discordId, guildId, toObtain, toRemove)
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
        dbUser: LinkUser,
        rules: Collection<Rule>,
        strongIdGuildNames: Collection<String>
    ): Set<String> = withContext(Dispatchers.IO) {
        val (did, discordName, discordDiscriminator) = facade.getDiscordUserInfo(dbUser.discordId)
        val identity =
            if (database.isUserIdentifiable(dbUser.discordId) && rules.any { it is StrongIdentityRule }) {
                val author = "EpiLink Discord Bot"
                val reason =
                    "EpiLink has accessed your identity automatically in order to update your roles on the following Discord servers: " +
                            strongIdGuildNames.joinToString(", ")
                val id = database.accessIdentity(dbUser, true, author, reason)
                messages.getIdentityAccessEmbed(true, author, reason)?.let {
                    facade.sendDirectMessage(dbUser.discordId, it)
                }
                id
            } else null
        val baseSet =
            if (identity != null) setOf(StandardRoles.Identified.roleName, StandardRoles.Known.roleName)
            else setOf(StandardRoles.Known.roleName)
        rules.map { rule ->
            async {
                runCatching {
                    when {
                        rule is WeakIdentityRule ->
                            rule.determineRoles(did, discordName, discordDiscriminator)
                        rule is StrongIdentityRule && identity != null ->
                            rule.determineRoles(did, discordName, discordDiscriminator, identity)
                        else ->
                            listOf()
                    }
                }.getOrElse { ex ->
                    logger.error("Failed to apply rule ${rule.name} due to an unexpected exception.", ex)
                    listOf()
                }
            }
        }.awaitAll().flatten().toSet().union(baseSet)
    }
}
