/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import kotlinx.coroutines.*
import org.epilink.bot.LinkEndpointException
import org.epilink.bot.config.LinkDiscordConfig
import org.epilink.bot.config.isMonitored
import org.epilink.bot.rulebook.Rule
import org.epilink.bot.rulebook.Rulebook
import org.epilink.bot.rulebook.StrongIdentityRule
import org.epilink.bot.rulebook.WeakIdentityRule
import org.epilink.bot.db.Disallowed
import org.epilink.bot.db.LinkServerDatabase
import org.epilink.bot.db.LinkUser
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.debug
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.slf4j.LoggerFactory

/**
 * The role manager interface
 */
interface LinkRoleManager {
    /**
     * Updates all the roles of a Discord user on a given collection of guilds. The user does not have to be present on
     * the given guilds -- this function handles the case where the member is absent well. If tellUserIfFailed is true,
     * this additionally sends the user a DM on why the roles may have failed to update (e.g. banned user).
     *
     * @param discordId The user's Discord ID
     * @param guilds The guilds on which the role update should be performed
     * @param tellUserIfFailed If true, the user is warned if he is banned via a Discord DM. If false, the role
     * update is stopped silently.
     * @throws LinkEndpointException If something fails during the member retrieval from each guild.
     */
    suspend fun updateRolesOnGuilds(
        discordId: String,
        guilds: Collection<String>,
        tellUserIfFailed: Boolean
    )

    /**
     * Returns a list of every rule that is relevant for the given guilds, paired with which guilds (by ID) are
     * asked for the rule.
     *
     * To get only the rules, use the result with `result.map { it.first }`
     *
     * The guild IDs are only intended for displaying them (or the name of the guild) as part of ID access notifications.
     *
     * @param guilds Vararg of guildIDs
     */
    suspend fun getRulesRelevantForGuilds(vararg guilds: String): List<RuleWithRequestingGuilds>

    /**
     * Handles the event where a user joins a server where the bot is.
     *
     * @param guildId The guild where the user just joined
     * @param guildName The name of the guild the user just joined
     * @param memberId The user who just joined
     */
    suspend fun handleNewUser(guildId: String, guildName: String, memberId: String)

    /**
     * Update the roles of a member on a given guild, based on the given EpiLink roles from the role list.
     *
     * @param discordId The Discord ID of the user to update
     * @param guildId The guild in which to update the user
     * @param roles The list of EpiLink roles the user should have. May contain roles that are not configured for the
     * guild.
     */
    suspend fun updateUserWithRoles(discordId: String, guildId: String, roles: Collection<String>)

    /**
     * Run an update of roles in all of the guilds the bot is connected to for the given user elsewhere.
     *
     * This function returns immediately and the operation is ran in a separate coroutine scope.
     */
    fun updateRolesOnAllGuildsLater(discordId: String): Job

    /**
     * Get the role list for a user. The user may have an EpiLink or not, the user may be banned or not.
     *
     * [strongIdGuildNames] should contain the name of the guilds that required strong identity rules. It is ignored
     * if no such rules were used.
     *
     * This function triggers an identity access if the user is known, is identifiable and a strong rule is passed in
     * the rules parameter.
     *
     * @param userId The user we want to determine roles for
     * @param rules The rules that will be called to determine additional roles
     * @param strongIdGuildNames The name of guilds that require strong rules. Only used for building the ID access
     * reason
     * @param tellUserIfFailed If true, the user is warned if he is banned via a Discord DM. If false, the role
     * update is stopped silently.
     */
    @OptIn(UsesTrueIdentity::class)
    suspend fun getRolesForUser(
        userId: String,
        rules: Collection<Rule>,
        strongIdGuildNames: Collection<String>,
        tellUserIfFailed: Boolean
    ): Set<String>
}

/**
 * This class is responsible for managing and updating the roles of Discord users.
 */
internal class LinkRoleManagerImpl : LinkRoleManager, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.bot.roles")
    private val database: LinkServerDatabase by inject()
    private val messages: LinkDiscordMessages by inject()
    private val config: LinkDiscordConfig by inject()
    private val rulebook: Rulebook by inject()
    private val facade: LinkDiscordClientFacade by inject()
    private val ruleMediator: RuleMediator by inject()
    private val scope = CoroutineScope(Dispatchers.Default)

    override suspend fun updateRolesOnGuilds(
        discordId: String,
        guilds: Collection<String>,
        tellUserIfFailed: Boolean
    ) = coroutineScope {
        // Determine where the user is among the given guilds
        val whereConnected =
            guilds.filter { config.isMonitored(it) && facade.isUserInGuild(discordId, it) }
        logger.debug { "Only updating ${discordId}'s roles on ${whereConnected.joinToString(", ")}" }
        // Get the relevant rules
        val rules = getRulesRelevantForGuilds(*whereConnected.toTypedArray())
        logger.debug { "Updating ${discordId}'s roles requires calling the rules ${rules.joinToString(", ") { it.rule.name }}" }
        // Compute the roles
        val roles = getRolesForUser(
            discordId,
            rules.map { it.rule },
            rules.filter { it.rule is StrongIdentityRule }
                .flatMap { it.requestingGuilds }.map { facade.getGuildName(it) },
            tellUserIfFailed
        )
        logger.debug {
            "Computed EpiLink roles for $discordId for guilds ${whereConnected.joinToString(", ")}:" +
                    roles.joinToString(", ")
        }
        // Update the roles
        whereConnected.forEach { guildId ->
            updateUserWithRoles(discordId, guildId, roles)
        }
    }

    override suspend fun handleNewUser(guildId: String, guildName: String, memberId: String) {
        if (!config.isMonitored(guildId)) {
            logger.debug { "Ignoring member $memberId joining unmonitored guild $guildId ($guildName)" }
            return // Ignore unmonitored guilds' join events
        }
        val dbUser = database.getUser(memberId)
        if (dbUser == null)
            messages.getGreetingsEmbed(guildId, guildName)?.let { facade.sendDirectMessage(memberId, it) }
        else
            updateRolesOnGuilds(memberId, listOf(guildId), true)
    }

    override fun updateRolesOnAllGuildsLater(discordId: String): Job =
        scope.launch {
            updateRolesOnGuilds(discordId, facade.getGuilds(), true)
        }

    override suspend fun getRulesRelevantForGuilds(
        vararg guilds: String
    ): List<RuleWithRequestingGuilds> = withContext(Dispatchers.Default) {
        // Maps role names to their rules in the global config
        val rolesInGlobalConfig = config.roles.associateBy({ it.name }, { it.rule })
        guilds
            // Get each guild's config, paired with the guild's ID
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
                    ?: run { logger.error("Could not find any role in roles config with id ${it.key}"); null }
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
                rulebook.rules[it.key]?.let { r -> RuleWithRequestingGuilds(r, it.value) }
                    ?: run { logger.error("Could not find any rule with id ${it.key}"); null }
            } // List<RuleWithRequestingGuilds>
        // .also { println("#### F ####$it") }
    }

    override suspend fun updateUserWithRoles(discordId: String, guildId: String, roles: Collection<String>) {
        logger.debug { "Updating roles for $discordId in $guildId: they should have ${roles.joinToString(", ")}" }
        val serverConfig = config.getConfigForGuild(guildId)
        val toObtain = roles.mapNotNull { serverConfig.roles[it] }.toSet()
        val toRemove = serverConfig.roles.values.toSet().minus(toObtain)
        logger.debug {
            "For $discordId in $guildId: remove ${toRemove.joinToString(", ")} and add ${toObtain.joinToString(", ")}"
        }
        facade.manageRoles(discordId, guildId, toObtain, toRemove)
    }

    @OptIn(UsesTrueIdentity::class)
    override suspend fun getRolesForUser(
        userId: String,
        rules: Collection<Rule>,
        strongIdGuildNames: Collection<String>,
        tellUserIfFailed: Boolean
    ): Set<String> = withContext(Dispatchers.IO) {
        // Get the user. If the user is unknown, return an empty set: they should not have any role
        val dbUser = database.getUser(userId)
        if (dbUser == null) {
            logger.debug { "Unidentified user $userId roles determined: none" }
            return@withContext setOf()
        }
        // Check if the user is allowed to join servers. If not, return an empty set.
        val adv = database.canUserJoinServers(dbUser)
        if (adv is Disallowed) {
            if (tellUserIfFailed) {
                facade.sendDirectMessage(userId, messages.getCouldNotJoinEmbed("any server at all", adv.reason))
            }
            logger.debug { "Disallowed user $userId roles determined: none (${adv.reason})" }
            return@withContext setOf()
        }
        // Check the user's roles
        logger.debug { "Determining ${userId}'s roles..." }
        val identifiable = database.isUserIdentifiable(userId)
        // The base set is the set of standard EpiLink roles (_known, _identified)
        val baseSet =
            if (identifiable) setOf(StandardRoles.Identified.roleName, StandardRoles.Known.roleName)
            else setOf(StandardRoles.Known.roleName)
        if(rules.isEmpty()) {
            return@withContext baseSet // No rules to apply
        }
        // The identity of the user, or null if the user is not identifiable (or if no rules are strong identity)
        val identity = if (identifiable) roleUpdateIdAccess(dbUser, rules, strongIdGuildNames) else null
        // Information required for applying rules
        val (did, discordName, discordDiscriminator) = facade.getDiscordUserInfo(userId)
        // Run all of the rules asynchronously
        rules.map { async { ruleMediator.runRule(it, did, discordName, discordDiscriminator, identity) } }
            // Await, flatten, turn into a set
            .awaitAll().flatten().toSet()
            .union(baseSet)
    }

    @UsesTrueIdentity
    private suspend fun roleUpdateIdAccess(
        dbUser: LinkUser,
        rules: Collection<Rule>,
        strongIdGuildNames: Collection<String>
    ): String? {
        if (rules.none { it is StrongIdentityRule }) {
            return null
        }
        logger.debug {
            "Identity required for strong rules (Discord user ${dbUser.discordId}, rules " +
                    rules.filterIsInstance<StrongIdentityRule>().joinToString(", ") { it.name } + ")"
        }
        val author = "EpiLink Discord Bot"
        val reason =
            "EpiLink has accessed your identity automatically in order to update your roles on the following Discord servers: " +
                    strongIdGuildNames.joinToString(", ")
        val id = database.accessIdentity(dbUser, true, author, reason)
        messages.getIdentityAccessEmbed(true, author, reason)?.let {
            facade.sendDirectMessage(dbUser.discordId, it)
        }
        return id

    }
}

/**
 * A rule paired with guilds that require this rule.
 */
data class RuleWithRequestingGuilds(
    /**
     * The rule
     */
    val rule: Rule,
    /**
     * Set of the IDs of the guilds that require checking this rule for gathering additional roles
     */
    val requestingGuilds: Set<String>
)