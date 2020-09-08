package org.epilink.bot.config

import org.epilink.bot.discord.DiscordEmbed
import org.epilink.bot.discord.StandardRoles
import org.epilink.bot.rulebook.Rulebook

/**
 * Configuration related to the Discord bot
 *
 * @see LinkConfiguration.discord
 */
data class LinkDiscordConfig(
    /**
     * The URL the default welcome-I-don't-know-you message should redirect to.
     */
    val welcomeUrl: String?,
    /**
     * The prefix for commands. "e!" by default.
     */
    val commandsPrefix: String = "e!",
    /**
     * The list of EpiLink custom roles (list of [LinkDiscordRoleSpec] objects)
     */
    val roles: List<LinkDiscordRoleSpec> = listOf(),
    /**
     * The list of Discord servers that should be monitored by EpiLink
     */
    val servers: List<LinkDiscordServerSpec> = listOf(),
    /**
     * The default language to use in the bot
     */
    val defaultLanguage: String = "en",
    /**
     * The preferred languages list. Used for the first "change your language" prompt (only preferred languages are
     * shown) + preferred languages are shown first in the e!lang list.
     */
    val preferredLanguages: List<String> = listOf(defaultLanguage),
    /**
     * Roles that EpiLink will never remove on all servers. (they "stick" to the person once added)
     */
    val stickyRoles: List<String> = listOf()
)

/**
 * A Discord EpiLink custom role
 *
 * @see LinkDiscordConfig.roles
 */
data class LinkDiscordRoleSpec(
    /**
     * The name of this role
     */
    val name: String,
    /**
     * The human-friendly name of this role. Only used for display.
     */
    val displayName: String? = null,
    /**
     * The rule that should be used to determine this role.
     */
    val rule: String
)

/**
 * A Discord server that should be monitored by EpiLink
 *
 * @see LinkDiscordConfig.servers
 */
data class LinkDiscordServerSpec(
    /**
     * The Discord ID of the server
     */
    val id: String,
    /**
     * True if a welcome message should be displayed, false otherwise
     */
    val enableWelcomeMessage: Boolean = true,
    /**
     * A Discord embed if a custom welcome message should be used, null (or absent) if the default message should be
     * used.
     */
    val welcomeEmbed: DiscordEmbed? = null,
    /**
     * The role mapping. Keys are EpiLink roles, values are Discord roles.
     */
    val roles: Map<String, String>,
    /**
     * Roles that EpiLink will never remove on this server only. (they "stick" to the person once added)
     */
    val stickyRoles: List<String>
)

/**
 * Check the coherence of the configuration with the available languages
 */
fun LinkDiscordConfig.checkCoherenceWithLanguages(available: Set<String>): List<ConfigReportElement> {
    val languages = available.joinToString(", ")
    val report = mutableListOf<ConfigReportElement>()
    if (defaultLanguage !in available) {
        report += ConfigError(
            true,
            "The default language you chose ($defaultLanguage) is not available. The available languages are $languages"
        )
    }

    val unavailablePreferredLanguages = preferredLanguages - available
    if (unavailablePreferredLanguages.isNotEmpty()) {
        report += ConfigError(
            true,
            "The preferred languages contain unavailable languages (${unavailablePreferredLanguages.joinToString(", ")}). The available languages are $languages"
        )
    }
    if (defaultLanguage !in preferredLanguages) {
        report += ConfigError(
            true,
            "The preferred languages list ${preferredLanguages.joinToString(", ")} must contain the default language ($defaultLanguage)."
        )
    }
    return report
}

/**
 * Check the general Discord configuration
 */
fun LinkDiscordConfig.check(): List<ConfigReportElement> {
    val report = mutableListOf<ConfigReportElement>()
    roles.map { it.name }.filter { it.startsWith("_") }.forEach {
        report +=
            ConfigWarning("A role was registered with the name ${it}, which starts with an underscore. Underscores are reserved for standard EpiLink roles like _known.")
    }
    return report
}

/**
 * Report whether the rulebook and the config have some incoherent elements. Checks include:
 *
 * - Missing rules
 * - Missing role definitions
 * - Unused rules
 */
fun LinkDiscordConfig.checkCoherenceWithRulebook(rulebook: Rulebook): List<ConfigReportElement> {
    val report = mutableListOf<ConfigReportElement>()
    val roleNamesUsedInServers =
        servers.map { it.roles.keys }.flatten().toSet() - StandardRoles.values().map { it.roleName }
    val rolesDeclaredInRoles = roles
    val rulesDeclared = rulebook.rules.keys

    val roleNamesDeclaredInRoles = rolesDeclaredInRoles.map { it.name }.toSet()
    val rolesMissingInRoles = roleNamesUsedInServers - roleNamesDeclaredInRoles
    for (role in rolesMissingInRoles) {
        report += ConfigError(
            true,
            "Role $role is referenced in a server config but is not defined in the Discord roles config"
        )
    }

    val ruleNamesUsedInRoles = rolesDeclaredInRoles.map { it.rule }.toSet()
    val missingRules = ruleNamesUsedInRoles - rulesDeclared
    for (rule in missingRules) {
        report += ConfigError(
            true,
            "Rule $rule is used in a Discord role config but is not defined in the rulebook"
        )
    }

    val unusedRules = rulesDeclared - ruleNamesUsedInRoles
    for (rule in unusedRules) {
        report += ConfigWarning("Rule $rule is defined in the rulebook but is never used")
    }

    return report
}