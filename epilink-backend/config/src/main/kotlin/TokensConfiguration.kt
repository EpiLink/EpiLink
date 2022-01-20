/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.config

/**
 * Tokens and secrets options
 *
 * @see Configuration.tokens
 */
data class TokensConfiguration(
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
fun TokensConfiguration.check(): List<ConfigReportElement> {
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
