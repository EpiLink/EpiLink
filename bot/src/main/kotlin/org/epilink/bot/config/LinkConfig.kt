/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.epilink.bot.CliArgs
import org.epilink.bot.rulebook.Rulebook
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
     * The OIDC Identity Provider to use
     */
    val idProvider: LinkIdProviderConfiguration,
    /**
     * Discord-related configurations, defined using a [LinkDiscordConfig] object
     */
    val discord: LinkDiscordConfig,
    /**
     * URI of the Redis server to use. Format is from Lettuce
     *
     * [Format](https://github.com/lettuce-io/lettuce-core/wiki/Redis-URI-and-connection-details)
     */
    val redis: String?,
    /**
     * The rule book, directly as Kotlin code. Mutually exclusive with [rulebookFile].
     */
    val rulebook: String? = null,
    /**
     * The rule book, as a path relative to the config file's path. Mutually exclusive with [rulebook]
     */
    val rulebookFile: String? = null,
    /**
     * True if the rulebook should be cached to avoid compiling it every time, false to re-compile it every time EpiLink
     * is launched.
     */
    val cacheRulebook: Boolean = true,
    /**
     * Legal configuration, with ToS and privacy policy configs
     */
    val legal: LinkLegalConfiguration = LinkLegalConfiguration(),
    /**
     * List of administrators (by Discord ID)
     */
    val admins: List<String> = listOf()
)


/**
 * Check if a guild is monitored: that is, EpiLink knows how to handle it and is expected to do so.
 */
fun LinkDiscordConfig.isMonitored(guildId: String): Boolean = servers.any { it.id == guildId }


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
    rulebook: Rulebook,
    availableDiscordLanguages: Set<String>
): List<ConfigReportElement> {
    val report = mutableListOf<ConfigReportElement>()
    if (redis == null) {
        report += ConfigWarning("No Redis URI provided: Redis is disabled, using in-memory instead. ONLY LEAVE REDIS DISABLED FOR DEVELOPMENT PURPOSES!")
    }
    report += server.check()
    report += tokens.check()
    report += legal.check()
    report += discord.check()
    report += discord.checkCoherenceWithRulebook(rulebook)
    report += discord.checkCoherenceWithLanguages(availableDiscordLanguages)

    return report
}

