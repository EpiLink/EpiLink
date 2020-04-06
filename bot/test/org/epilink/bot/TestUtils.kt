package org.epilink.bot

import org.epilink.bot.config.LinkConfiguration
import org.epilink.bot.config.LinkDiscordConfig
import org.epilink.bot.config.LinkTokens
import org.epilink.bot.config.LinkWebServerConfiguration

val minimalConfig = LinkConfiguration(
    "Test",
    server = LinkWebServerConfiguration(0, null),
    db = "",
    tokens = LinkTokens(
        discordToken = "",
        discordOAuthClientId = "",
        discordOAuthSecret = "",
        msftOAuthClientId = "",
        msftOAuthSecret = "",
        msftTenant = ""
    ),
    discord = LinkDiscordConfig(null),
    redis = null
)