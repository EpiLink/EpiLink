package org.epilink.bot.config

/**
 * Tokens and secrets options
 *
 * @see LinkConfiguration.tokens
 */
data class LinkTokens(
    /**
     * Discord bot token
     */
    val discordToken: String,
    /**
     * Discord OAuth Client ID
     */
    val discordOAuthClientId: String,
    /**
     * Discord OAuth Client Secret
     */
    val discordOAuthSecret: String,
    /**
     * Identity provider OpenID Connect / OAuth 2 Client Id
     */
    val idpOAuthClientId: String,
    /**
     * Identity provider OpenID Connect / OAuth 2 Client Secret
     */
    val idpOAuthSecret: String
)

/**
 * Check the tokens' configuration
 */
@Suppress("unused")
fun LinkTokens.check(): List<ConfigReportElement> {
    // Emit an error if using the default values from the sample config file
    val report = mutableListOf<ConfigReportElement>()
    if (discordOAuthClientId == "...") {
        report += ConfigError(true, "discordOAuthClientId was left with its default value: please provide a client ID!")
    }
    if (discordOAuthSecret == "...") {
        report += ConfigError(true, "discordOAuthSecret was left with its default value: please provide a secret!")
    }
    if (discordToken == "...") {
        report += ConfigError(true, "discordToken was left with its default value: please provide a bot token!")
    }
    if (idpOAuthClientId == "...") {
        report += ConfigError(true, "idpOAuthClientId was left with its default value: please provide a client ID!")
    }
    if (idpOAuthSecret == "...") {
        report += ConfigError(true, "idpOAuthSecret was left with its default value: please provide a secret!")
    }
    return report
}