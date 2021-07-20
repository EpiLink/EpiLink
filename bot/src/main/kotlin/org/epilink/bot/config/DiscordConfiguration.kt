/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.config

import org.epilink.bot.discord.DiscordEmbed
import org.epilink.bot.rulebook.Rulebook

/**
 * Configuration related to the Discord bot
 *
 * @see Configuration.discord
 */
data class DiscordConfiguration(
    /**
     * The URL the default welcome-I-don't-know-you message should redirect to.
     */
    val welcomeUrl: String?,
    /**
     * The prefix for commands. "e!" by default.
     */
    val commandsPrefix: String = "e!",
    /**
     * The list of Discord servers that should be monitored by EpiLink
     */
    val servers: List<DiscordServerSpec> = listOf(),
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
 * A Discord server that should be monitored by EpiLink
 *
 * @see DiscordConfiguration.servers
 */
data class DiscordServerSpec(
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
     * The list of rules that must be launched in order to determine the roles in this server
     */
    val requires: List<String> = listOf(),
    /**
     * The role mapping. Keys are EpiLink roles, values are Discord roles.
     */
    val roles: Map<String, String>,
    /**
     * Roles that EpiLink will never remove on this server only. (they "stick" to the person once added)
     */
    val stickyRoles: List<String> = listOf()
)

/**
 * Check the coherence of the configuration with the available languages
 */
fun DiscordConfiguration.checkCoherenceWithLanguages(available: Set<String>): List<ConfigReportElement> {
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
 * Report whether the rulebook and the config have some incoherent elements. Checks include:
 *
 * - Missing rules
 * - Missing role definitions
 * - Unused rules
 */
fun DiscordConfiguration.checkCoherenceWithRulebook(rulebook: Rulebook): List<ConfigReportElement> {
    val report = mutableListOf<ConfigReportElement>()
    val ruleNamesUsedInServers =
        servers.flatMap { it.requires.map { ruleName -> ruleName to it } }
            .groupBy({ it.first }, { it.second.id })

    val rulesDeclared = rulebook.rules.keys


    val missingRules = ruleNamesUsedInServers - rulesDeclared
    for ((rule, serversWhereUsed) in missingRules) {
        report += ConfigError(
            true,
            "Rule $rule is used in but is not defined in the rulebook. " +
                    "Used in: ${serversWhereUsed.joinToString(", ")}"
        )
    }

    val unusedRules = rulesDeclared - ruleNamesUsedInServers.keys
    for (rule in unusedRules) {
        report += ConfigWarning("Rule $rule is defined in the rulebook but is never used")
    }

    return report
}
