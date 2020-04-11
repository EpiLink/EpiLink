package org.epilink.bot.discord

import org.epilink.bot.LinkException
import org.epilink.bot.config.LinkDiscordConfig
import org.epilink.bot.config.LinkDiscordServerSpec
import org.epilink.bot.config.LinkPrivacy
import org.koin.core.KoinComponent
import org.koin.core.inject

/**
 * The Discord bot interface that generates embeds for various instances
 */
interface LinkDiscordMessages {
    /**
     * Get the embed to send to a user about why connecting failed
     */
    fun getCouldNotJoinEmbed(guildName: String, reason: String): DiscordEmbed

    /**
     * Get the initial server message sent upon connection with the server not knowing who the person is, or null if no
     * message should be sent.
     */
    fun getGreetingsEmbed(guildId: String, guildName: String): DiscordEmbed?

    /**
     * Send an identity access notification to the given Discord ID with the given information, or null if no message
     * should be sent. Whether a message should be sent or not is determined through the privacy configuration of
     * EpiLink.
     *
     * @param automated Whether the access was done automatically or not
     * @param author The author of the request (bot name or human name)
     * @param reason The reason behind this identity access
     */
    fun getIdentityAccessEmbed(automated: Boolean, author: String, reason: String): DiscordEmbed?
}

internal class LinkDiscordMessagesImpl : LinkDiscordMessages, KoinComponent {
    private val config: LinkDiscordConfig by inject()

    private val privacyConfig: LinkPrivacy by inject()

    override fun getCouldNotJoinEmbed(guildName: String, reason: String) =
        DiscordEmbed(
            title = ":x: Could not authenticate on $guildName",
            description = "Failed to authenticate you on $guildName. Please contact an administrator if you think that should not be happening.",
            fields = listOf(DiscordEmbedField("Reason", reason, true)),
            footer = DiscordEmbedFooter(
                "Powered by EpiLink",
                "https://cdn.discordapp.com/attachments/680809657740427300/680811402172301348/epilink3g.png"
            ),
            color = "red"
        )

    override fun getGreetingsEmbed(guildId: String, guildName: String): DiscordEmbed? {
        val guildConfig = config.getConfigForGuild(guildId)
        if (!guildConfig.enableWelcomeMessage)
            return null
        return guildConfig.welcomeEmbed ?: DiscordEmbed(
            title = ":closed_lock_with_key: Authentication required for $guildName",
            description =
            """
                    **Welcome to $guildName**. Access to this server is restricted. Please log in using the link
                    below to get full access to the server's channels.
                    """.trimIndent(),
            fields = run {
                val ml = mutableListOf<DiscordEmbedField>()
                val welcomeUrl = config.welcomeUrl
                if (welcomeUrl != null)
                    ml += DiscordEmbedField("Log in", welcomeUrl)
                ml += DiscordEmbedField(
                    "Need help?",
                    "Contact the administrators of $guildName if you need help with the procedure."
                )
                ml
            },
            thumbnail = "https://cdn.discordapp.com/attachments/680809657740427300/696412896472727562/whoareyou.png",
            footer = DiscordEmbedFooter(
                "Powered by EpiLink",
                "https://cdn.discordapp.com/attachments/680809657740427300/680811402172301348/epilink3g.png"
            ),
            color = "#3771c8"
        )
    }

    override fun getIdentityAccessEmbed(
        automated: Boolean,
        author: String,
        reason: String
    ): DiscordEmbed? {
        if (privacyConfig.shouldNotify(automated)) {
            val str = buildString {
                append("Your identity was accessed")
                if (privacyConfig.shouldDiscloseIdentity(automated)) {
                    append(" by *$author*")
                }
                if (automated) {
                    append(" automatically")
                }
                appendln(".")
            }
            return DiscordEmbed(
                title = "Identity access notification",
                description = str,
                fields = listOf(
                    DiscordEmbedField("Reason", reason, false),
                    if (automated) {
                        DiscordEmbedField(
                            "Automated access",
                            "This access was conducted automatically by a bot. No administrator has accessed your identity.",
                            false
                        )
                    } else {
                        DiscordEmbedField(
                            "I need help!",
                            "Contact an administrator if you believe that this action was conducted against the Terms of Services.",
                            false
                        )
                    }
                ),
                color = "#ff6600",
                thumbnail = "https://media.discordapp.net/attachments/680809657740427300/696411621320425572/idnotify.png",
                footer = DiscordEmbedFooter(
                    "Powered by EpiLink",
                    "https://cdn.discordapp.com/attachments/680809657740427300/680811402172301348/epilink3g.png"
                )
            )
        } else return null
    }
}

/**
 * Retrieve the configuration for a given guild, or throw an error if such a configuration could not be found.
 *
 * Expects the guild to be monitored (i.e. expects a configuration to be present).
 *
 * @throws LinkException If the configuration could not be found.
 */
fun LinkDiscordConfig.getConfigForGuild(guildId: String): LinkDiscordServerSpec =
    this.servers.firstOrNull { it.id == guildId }
        ?: throw LinkException("Configuration not found, but guild was expected to be monitored")
