package org.epilink.bot.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.epilink.bot.CliArgs
import org.epilink.bot.config.rulebook.Rulebook
import org.epilink.bot.discord.DiscordEmbed
import org.epilink.bot.discord.StandardRoles
import java.nio.file.Files
import java.nio.file.Path

data class LinkConfiguration(
    val name: String,
    val privacy: LinkPrivacy = LinkPrivacy(),
    val server: LinkWebServerConfiguration,
    val db: String,
    val tokens: LinkTokens,
    val discord: LinkDiscordConfig
)

data class LinkWebServerConfiguration(
    val port: Int,
    val frontendUrl: String?,
    val sessionDuration: Long? = null
)

data class LinkTokens(
    val jwtSecret: String? = null,
    val discordToken: String?,
    val discordOAuthClientId: String?,
    val discordOAuthSecret: String?,
    val msftOAuthClientId: String?,
    val msftOAuthSecret: String?,
    val msftTenant: String
)

data class LinkDiscordConfig(
    val welcomeUrl: String?,
    val rulebook: String? = null,
    // The path to the rulebook file (.kts) is relative to the path of the config file (.yaml)
    val rulebookFile: String? = null,
    val roles: List<LinkDiscordRoleSpec> = listOf(),
    val servers: List<LinkDiscordServerSpec> = listOf()
)

data class LinkDiscordRoleSpec(
    val name: String,
    val displayName: String? = null,
    val rule: String
)

data class LinkDiscordServerSpec(
    val id: String,
    val enableWelcomeMessage: Boolean = true,
    val welcomeEmbed: DiscordEmbed? = null,
    val roles: Map<String, String>
)

data class LinkPrivacy(
    val notifyAutomatedAccess: Boolean = true,
    val notifyHumanAccess: Boolean = true,
    val discloseHumanRequesterIdentity: Boolean = false
) {
    fun shouldNotify(automated: Boolean): Boolean =
        if (automated) notifyAutomatedAccess else notifyHumanAccess

    fun shouldDiscloseIdentity(automated: Boolean): Boolean =
        if (automated) true else discloseHumanRequesterIdentity
}

private val yamlKotlinMapper = ObjectMapper(YAMLFactory()).apply {
    registerModule(KotlinModule())
}

fun loadConfigFromFile(path: Path): LinkConfiguration =
    Files.newBufferedReader(path).use {
        yamlKotlinMapper.readValue(it, LinkConfiguration::class.java)
    }

fun loadConfigFromString(config: String): LinkConfiguration =
    yamlKotlinMapper.readValue(config, LinkConfiguration::class.java)

/**
 * Checks the sanity of configuration options and coherence with the rulebook, logging information and guidance
 * for resolving issues.
 */
fun LinkConfiguration.isConfigurationSane(
    @Suppress("UNUSED_PARAMETER") args: CliArgs,
    rulebook: Rulebook
): List<ConfigReportElement> {
    val report = mutableListOf<ConfigReportElement>()

    if (server.sessionDuration != null) {
        report += ConfigWarning("The sessionDuration configuration field is deprecated and will be removed.")
    }

    if(tokens.jwtSecret != null) {
        report += ConfigWarning("The jwtSecret configuration field is deprecated and will be removed.")
    }

    discord.roles.map { it.name }.filter { it.startsWith("_") }.forEach {
        report +=
            ConfigWarning("A role was registered with the name ${it}, which starts with an underscore. Underscores are reserved for standard EpiLink roles like _known.")
    }

    report += discord.checkCoherenceWithRulebook(rulebook)
    return report
}

/**
 * Report whether the rulebook and the config have some incoherent elements. Checks include:
 *
 * - Missing rules
 * - Missing role definitions
 * - Unused rules
 */
private fun LinkDiscordConfig.checkCoherenceWithRulebook(rulebook: Rulebook): List<ConfigReportElement> {
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