package org.epilink.bot.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.epilink.bot.CliArgs
import org.epilink.bot.discord.DiscordEmbed
import java.nio.file.Files
import java.nio.file.Path

data class LinkConfiguration(
    val name: String,
    val server: LinkWebServerConfiguration,
    val db: String,
    val tokens: LinkTokens,
    val discord: LinkDiscordConfig
)

data class LinkWebServerConfiguration(
    val port: Int,
    val frontendUrl: String?,
    val sessionDuration: Long
)

data class LinkTokens(
    val jwtSecret: String,
    val discordToken: String?,
    val discordOAuthClientId: String?,
    val discordOAuthSecret: String?,
    val msftOAuthClientId: String?,
    val msftOAuthSecret: String?,
    val msftTenant: String
)

data class LinkDiscordConfig(
    val welcomeUrl: String?,
    val roles: List<LinkDiscordRoleSpec>?,
    val servers: List<LinkDiscordServerSpec>?
)

data class LinkDiscordRoleSpec(
    val name: String
    // Later on, the rules to follow for each role will also be defined here.
)

data class LinkDiscordServerSpec(
    val id: String,
    val enableWelcomeMessage: Boolean = true,
    val welcomeEmbed: DiscordEmbed? = null,
    val roles: Map<String, String>
)

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
 * Checks the sanity of configuration options, logging information and guidance
 * for resolving issues.
 */
fun LinkConfiguration.isConfigurationSane(args: CliArgs): List<ConfigReportElement> {
    val report = mutableListOf<ConfigReportElement>()
    if (tokens.jwtSecret == "I am a secret ! Please change me :(") {
        if (args.allowUnsecureJwtSecret) {
            report += ConfigWarning("Default JWT secret found in configuration but allowed through -u flag.")
            report += ConfigWarning("DO NOT USE -u IF YOU ARE IN A PRODUCTION ENVIRONMENT!")
        } else {
            report += ConfigError(true, "Please change the default JWT secret in the configuration file.")
            report += ConfigInfo("If you cannot change the secret (e.g. in a developer environment), run EpiLink with the -u flag.")
        }
    }

    if (server.sessionDuration < 0) {
        report += ConfigError(true, "Session duration can't be negative")
    }

    return report
}