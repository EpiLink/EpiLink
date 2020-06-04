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
import org.epilink.bot.CacheClient
import org.epilink.bot.LinkEndpointException
import org.epilink.bot.config.LinkDiscordConfig
import org.epilink.bot.config.isMonitored
import org.epilink.bot.db.*
import org.epilink.bot.rulebook.Rule
import org.epilink.bot.rulebook.Rulebook
import org.epilink.bot.rulebook.StrongIdentityRule
import org.epilink.bot.debug
import org.koin.core.KoinComponent
import org.koin.core.get
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
     * Reset all cached roles and run an update of roles in all of the guilds the bot is connected to for the given
     * user.
     *
     * This function returns immediately and the operation is ran in a separate coroutine scope.
     *
     * @param discordId The ID of the person whose roles should be reset and re-determined
     * @param tellUserIfFailed True if the user should be notified in case of a failure, false if such failure should
     * be silent
     */
    fun invalidateAllRoles(discordId: String, tellUserIfFailed: Boolean = false): Job

    /**
     * Get the role list for a user. The user may have an EpiLink or not, the user may be banned or not.
     *
     * This function triggers an identity access if the user is known, is identifiable and a strong rule is passed in
     * the rules parameter.
     *
     * @param userId The user we want to determine roles for
     * @param rulesInfo Rules alongside the guilds that request the use of such rules
     * @param tellUserIfFailed If true, the user is warned if he is banned via a Discord DM. If false, the role
     * update is stopped silently.
     * @param guildIds The IDs of the guilds where the roles are needed. Only used for displaying guild names to the
     * user in case of an error notification.
     */
    @OptIn(UsesTrueIdentity::class)
    suspend fun getRolesForUser(
        userId: String,
        rulesInfo: Collection<RuleWithRequestingGuilds>,
        tellUserIfFailed: Boolean,
        guildIds: Collection<String>
    ): Set<String>
}

/**
 * This class is responsible for managing and updating the roles of Discord users.
 */
internal class LinkRoleManagerImpl : LinkRoleManager, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.bot.roles")
    private val messages: LinkDiscordMessages by inject()
    private val config: LinkDiscordConfig by inject()
    private val rulebook: Rulebook by inject()
    private val facade: LinkDiscordClientFacade by inject()
    private val idAccessor: LinkIdAccessor by inject()
    private val dbFacade: LinkDatabaseFacade by inject()
    private val perms: LinkPermissionChecks by inject()
    private val ruleMediator: RuleMediator by lazy {
        get<CacheClient>().newRuleMediator("el_rc_")
    }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, ex ->
        logger.error("Uncaught exception in role manager launched coroutine", ex)
    })

    // TODO Add some tests for this function. Its individual components are tested but the whole *apparently* isn't
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
        val roles = getRolesForUser(discordId, rules, tellUserIfFailed, guilds)
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
        val dbUser = dbFacade.getUser(memberId)
        if (dbUser == null)
            messages.getGreetingsEmbed(guildId, guildName)?.let { facade.sendDirectMessage(memberId, it) }
        else
            updateRolesOnGuilds(memberId, listOf(guildId), true)
    }

    override fun invalidateAllRoles(discordId: String, tellUserIfFailed: Boolean): Job =
        scope.launch {
            ruleMediator.invalidateCache(discordId)
            updateRolesOnGuilds(discordId, facade.getGuilds(), tellUserIfFailed)
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
        rulesInfo: Collection<RuleWithRequestingGuilds>,
        tellUserIfFailed: Boolean,
        guildIds: Collection<String>
    ): Set<String> {
        // Get the user. If the user is unknown, return an empty set: they should not have any role
        val dbUser = dbFacade.getUser(userId)
        val adv = dbUser?.let { perms.canUserJoinServers(it) }
        return when {
            dbUser == null ->
                // Unknown user
                setOf<String>().also { logger.debug { "Unidentified user $userId roles determined: none" } }
            adv is Disallowed -> {
                // Disallowed user
                if (tellUserIfFailed)
                    facade.sendDirectMessage(
                        userId,
                        // See the other SimplifiableCallChain in this file for explanations
                        @Suppress("SimplifiableCallChain")
                        messages.getCouldNotJoinEmbed(guildIds.map { facade.getGuildName(it) }.joinToString(", "), adv.reason)
                    )
                setOf<String>().also { logger.debug { "Disallowed user $userId roles determined: none (${adv.reason})" } }
            }
            // At this point the user is known and allowed
            else -> {
                val identifiable = dbFacade.isUserIdentifiable(userId)
                val baseSet = getBaseRoleSetForKnown(identifiable)
                if (rulesInfo.isEmpty())
                    baseSet
                else
                    baseSet.union(computeAllowedUserRoles(dbUser, identifiable, rulesInfo))
            }
        }
    }

    // Computes the roles (based on rules) for a user who we assume is known and allowed.
    @UsesTrueIdentity
    private suspend fun computeAllowedUserRoles(
        dbUser: LinkUser,
        identifiable: Boolean,
        rulesInfo: Collection<RuleWithRequestingGuilds>
    ): Set<String> {
        val userId = dbUser.discordId
        // Use cached values directly
        val (cachedRules, cachedRoles) = getCachedRulesAndRoles(rulesInfo, userId)
        return rulesInfo.filter { it.rule !in cachedRules }.let { remaining ->
            remaining.runAll(
                facade.getDiscordUserInfo(userId),
                if (identifiable) roleUpdateIdAccess(dbUser, remaining) else null
            )
        }.union(cachedRoles)
    }

    // Runs all of the rules in the given list
    @UsesTrueIdentity
    private suspend fun List<RuleWithRequestingGuilds>.runAll(info: DiscordUserInfo, identity: String?) =
        coroutineScope {
            map {
                async { ruleMediator.runRule(it.rule, info.id, info.username, info.discriminator, identity) }
            }.awaitAll().flatten().toSet()
        }

    // Tries all the rules for caching, and returns a pair with the rules where the cache was hit, and the resulting
    // roles
    private suspend fun getCachedRulesAndRoles(
        rulesInfo: Collection<RuleWithRequestingGuilds>,
        userId: String
    ): Pair<Collection<Rule>, Collection<String>> =
        rulesInfo
            .map { it.rule to ruleMediator.tryCache(it.rule, userId) }
            .filter { it.second is CacheResult.Hit }
            .let { li -> li.map { it.first } to li.flatMap { (it.second as CacheResult.Hit).roles } }

    // Computes the base set is the set of standard EpiLink roles (_known, _identified)
    private fun getBaseRoleSetForKnown(identifiable: Boolean): Set<String> =
        mutableSetOf(StandardRoles.Known.roleName).apply {
            if (identifiable) add(StandardRoles.Identified.roleName)
        }

    @UsesTrueIdentity
    private suspend fun roleUpdateIdAccess(
        dbUser: LinkUser,
        rulesInfo: Collection<RuleWithRequestingGuilds>
    ): String? {
        val strongIdRulesInfo = rulesInfo.filter { it.rule is StrongIdentityRule }
        if (strongIdRulesInfo.isEmpty()) {
            return null
        }
        logger.debug {
            "Identity required for strong rules (Discord user ${dbUser.discordId}, rules " +
                    strongIdRulesInfo.joinToString(", ") { it.rule.name } + ")"
        }
        // IntelliJ wants to put facade.getGuildName in joinToString which is not possible because joinToString is not
        // inline-able and we need coroutines here
        @Suppress("SimplifiableCallChain")
        return idAccessor.accessIdentity(
            targetId = dbUser.discordId,
            automated = true,
            author = "EpiLink Discord Bot",
            reason = "EpiLink has accessed your identity automatically in order to update your roles on the following Discord servers: " +
                    strongIdRulesInfo.flatMap { it.requestingGuilds }.distinct().map { facade.getGuildName(it) }
                        .joinToString(", ")
        )
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