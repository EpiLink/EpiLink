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

/**
 * Main configuration, this is actually the configuration file's object representation
 */
data class LinkConfiguration(
    /**
     * The name of the instance
     */
    val name: String,
    /**
     * Privacy options, defined using a [LinkPrivacy] option. Optional, uses the default values if absent.
     */
    val privacy: LinkPrivacy = LinkPrivacy(),
    /**
     * HTTP/Web server options, defined using a [LinkWebServerConfiguration]
     */
    val server: LinkWebServerConfiguration,
    /**
     * Path, preferably absolute, to the SQLite database used by EpiLink
     */
    val db: String,
    /**
     * Tokens used by EpiLink, defined using a [LinkTokens] object
     */
    val tokens: LinkTokens,
    /**
     * Discord-related configurations, defined using a [LinkDiscordConfig] object
     */
    val discord: LinkDiscordConfig,
    /**
     * URI of the Redis server to use. Format is from Lettuce
     *
     * [Format](https://github.com/lettuce-io/lettuce-core/wiki/Redis-URI-and-connection-details)
     */
    val redis: String?
)

/**
 * HTTP/Web server options
 *
 * @see LinkConfiguration.server
 */
data class LinkWebServerConfiguration(
    /**
     * The port to be used by the server
     */
    val port: Int,
    /**
     * The URL to the front-end, with a trailing slash, or null if the front-end is bundled
     */
    val frontendUrl: String?,
    /**
     * The duration of a session. Unused and deprecated.
     */
    // TODO Remove
    val sessionDuration: Long? = null
)

/**
 * Tokens and secrets options
 *
 * @see LinkConfiguration.tokens
 */
data class LinkTokens(
    /**
     * JWT secret. Unused and deprecated.
     */
    // TODO Remove
    val jwtSecret: String? = null,
    /**
     * Discord bot token
     */
    val discordToken: String?,
    /**
     * Discord OAuth Client ID
     */
    val discordOAuthClientId: String?,
    /**
     * Discord OAuth Client Secret
     */
    val discordOAuthSecret: String?,
    /**
     * Microsoft/Azure AD Client Id
     */
    val msftOAuthClientId: String?,
    /**
     * Microsoft/Azure Ad Client Secret
     */
    val msftOAuthSecret: String?,
    /**
     * Microsoft tenant. Check the maintainer guide for more information.
     */
    val msftTenant: String
)

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
     * The rule book, directly as Kotlin code. Mutually exclusive with [rulebookFile].
     */
    val rulebook: String? = null,
    /**
     * The rule book, as a path relative to the config file's path. Mutually exclusive with [rulebook]
     */
    val rulebookFile: String? = null,
    /**
     * The list of EpiLink custom roles (list of [LinkDiscordRoleSpec] objects)
     */
    val roles: List<LinkDiscordRoleSpec> = listOf(),
    /**
     * The list of Discord servers that should be monitored by EpiLink
     */
    val servers: List<LinkDiscordServerSpec> = listOf()
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
    val roles: Map<String, String>
)

/**
 * Privacy options for EpiLink, related to how some notifications should be handled.
 *
 * @see LinkConfiguration.privacy
 */
data class LinkPrivacy(
    /**
     * True if automated accesses should send a notification
     */
    val notifyAutomatedAccess: Boolean = true,
    /**
     * True if human accesses should send a notification
     */
    val notifyHumanAccess: Boolean = true,
    /**
     * True if the name of the person who requested the identity access should be disclosed.
     */
    val discloseHumanRequesterIdentity: Boolean = false
) {
    /**
     * Whether an access should be notified, based on this object's configuration
     *
     * @param automated True if the access was automated
     */
    fun shouldNotify(automated: Boolean): Boolean =
        if (automated) notifyAutomatedAccess else notifyHumanAccess

    /**
     * Whether the identity of the requester should be disclosed, based on this object's configuration.
     */
    fun shouldDiscloseIdentity(automated: Boolean): Boolean =
        if (automated) true else discloseHumanRequesterIdentity
}

private val yamlKotlinMapper = ObjectMapper(YAMLFactory()).apply {
    registerModule(KotlinModule())
}

/**
 * Load a [LinkConfiguration] object from a Path that points to a YAML file.
 */
fun loadConfigFromFile(path: Path): LinkConfiguration =
    Files.newBufferedReader(path).use {
        yamlKotlinMapper.readValue(it, LinkConfiguration::class.java)
    }

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

    if (tokens.jwtSecret != null) {
        report += ConfigWarning("The jwtSecret configuration field is deprecated and will be removed.")
    }

    if(redis == null) {
        report += ConfigWarning("No Redis URI provided: Redis is disabled, using in-memory instead. ONLY LEAVE REDIS DISABLED FOR DEVELOPMENT PURPOSES!")
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