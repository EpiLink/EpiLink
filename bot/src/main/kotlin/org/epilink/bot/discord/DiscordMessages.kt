/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import org.epilink.bot.EpiLinkException
import org.epilink.bot.config.DiscordConfiguration
import org.epilink.bot.config.DiscordServerSpec
import org.epilink.bot.config.PrivacyConfiguration
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The Discord bot interface that generates embeds for various instances
 */
interface DiscordMessages {
    /**
     * Get the embed to send to a user about why connecting failed
     */
    fun getCouldNotJoinEmbed(language: String, guildName: String, reason: String): DiscordEmbed

    /**
     * Get the initial server message sent upon connection with the server not knowing who the person is, or null if no
     * message should be sent.
     */
    fun getGreetingsEmbed(language: String, guildId: String, guildName: String): DiscordEmbed?

    /**
     * Send an identity access notification to the given Discord ID with the given information, or null if no message
     * should be sent. Whether a message should be sent or not is determined through the privacy configuration of
     * EpiLink.
     *
     * @param automated Whether the access was done automatically or not
     * @param author The author of the request (bot name or human name)
     * @param reason The reason behind this identity access
     */
    fun getIdentityAccessEmbed(language: String, automated: Boolean, author: String, reason: String): DiscordEmbed?

    /**
     * Get the ban notification embed, or null if privacy settings have ban notifications disabled.
     */
    fun getBanNotification(language: String, banReason: String, banExpiry: Instant?): DiscordEmbed?

    /**
     * Get an error command reply for a generic command in a specific language. Two sub-keys are automatically used:
     * `key.title` and `key.description`, where key is the [key] argument. The [objects] are used for formatting the
     * description only.
     */
    fun getErrorCommandReply(language: String, key: String, vararg objects: Any, titleObjects: List<Any> = listOf()): DiscordEmbed

    /**
     * Embed for an invalid target given in a command body.
     */
    fun getWrongTargetCommandReply(language: String, target: String): DiscordEmbed

    /**
     * Generic embed for a successful command result.
     */
    fun getSuccessCommandReply(language: String, messageKey: String, vararg messageArgs: Any): DiscordEmbed

    /**
     * Embed for the help message. Simply shows the URL to the documentation
     */
    fun getHelpMessage(language: String, withAdminHelp: Boolean): DiscordEmbed

    /**
     * Embed for the e!lang command's help message.
     */
    fun getLangHelpEmbed(language: String): DiscordEmbed

    /**
     * Get the "welcome please choose a language" embed in the default language of the instance.
     */
    fun getWelcomeChooseLanguageEmbed(): DiscordEmbed
}

private const val LOGO_URL = "https://raw.githubusercontent.com/EpiLink/EpiLink/master/assets/epilink256.png"
private const val UNKNOWN_USER_LOGO_URL =
    "https://raw.githubusercontent.com/EpiLink/EpiLink/master/assets/unknownuser256.png"
private const val ID_NOTIFY_LOGO_URL = "https://raw.githubusercontent.com/EpiLink/EpiLink/master/assets/idnotify256.png"
private const val BAN_LOGO_URL = "https://raw.githubusercontent.com/EpiLink/EpiLink/master/assets/ban256.png"
private const val TARGETS_DOCS_URL = "https://epilink.zoroark.guru/#/DiscordCommands?id=user-target"
private const val COMMANDS_DOCS_URL = "https://epilink.zoroark.guru/#/DiscordCommands"
private const val ERROR_RED = "#8A0303"
private const val HELP_GREY = "#CCD6DD"
private const val HELLO_BLUE = "#3771C8"
private const val NOTIFICATION_ORANGE = "#FF6600"
private const val OK_GREEN = "#2B9B2B"

@OptIn(KoinApiExtension::class)
internal class DiscordMessagesImpl : DiscordMessages, KoinComponent {
    private val config: DiscordConfiguration by inject()
    private val i18n: DiscordMessagesI18n by inject()
    private val privacyConfig: PrivacyConfiguration by inject()

    private val DiscordI18nContext.poweredByEpiLink
        get() = DiscordEmbedFooter(i18n["poweredBy"], LOGO_URL)

    private fun <R> String.ctx(block: DiscordI18nContext.() -> R): R =
        DiscordI18nContext(this).run(block)

    override fun getCouldNotJoinEmbed(language: String, guildName: String, reason: String) = language.ctx {
        DiscordEmbed(
            title = i18n["cnj.title"].f(guildName),
            description = i18n["cnj.description"].f(guildName),
            fields = listOf(DiscordEmbedField(i18n["cnj.reason"], reason, true)),
            footer = poweredByEpiLink,
            color = ERROR_RED
        )
    }

    override fun getGreetingsEmbed(language: String, guildId: String, guildName: String): DiscordEmbed? = language.ctx {
        val guildConfig = config.getConfigForGuild(guildId)
        if (!guildConfig.enableWelcomeMessage)
            null
        else guildConfig.welcomeEmbed ?: DiscordEmbed(
            title = i18n["greet.title"].f(guildName),
            description = i18n["greet.welcome"].f(guildName),
            fields = run {
                val ml = mutableListOf<DiscordEmbedField>()
                val welcomeUrl = config.welcomeUrl
                if (welcomeUrl != null)
                    ml += DiscordEmbedField(i18n["greet.logIn"], welcomeUrl)
                ml += DiscordEmbedField(
                    i18n["greet.needHelp"],
                    i18n["greet.contact"].f(guildName)
                )
                ml
            },
            thumbnail = UNKNOWN_USER_LOGO_URL,
            footer = poweredByEpiLink,
            color = HELLO_BLUE
        )
    }

    override fun getIdentityAccessEmbed(
        language: String,
        automated: Boolean,
        author: String,
        reason: String
    ): DiscordEmbed? = language.ctx {
        if (privacyConfig.shouldNotify(automated)) {
            val discloseId = privacyConfig.shouldDiscloseIdentity(automated)
            val authorKey = if (discloseId) "Author" else ""
            val autoKey = if (automated) "Auto" else ""
            val str = i18n["ida.access$authorKey$autoKey"].let { if (discloseId) it.f(author) else it }
            DiscordEmbed(
                title = i18n["ida.title"],
                description = str,
                fields = listOf(
                    DiscordEmbedField(i18n["ida.reason"], reason, false),
                    if (automated) {
                        DiscordEmbedField(i18n["ida.auto.title"], i18n["ida.auto.description"], false)
                    } else {
                        DiscordEmbedField(i18n["ida.needHelp.title"], i18n["ida.needHelp.description"], false)
                    }
                ),
                color = NOTIFICATION_ORANGE,
                thumbnail = ID_NOTIFY_LOGO_URL,
                footer = poweredByEpiLink
            )
        } else null
    }

    override fun getBanNotification(language: String, banReason: String, banExpiry: Instant?): DiscordEmbed? =
        language.ctx {
            if (privacyConfig.notifyBans) {
                DiscordEmbed(
                    title = i18n["bn.title"],
                    description = i18n["bn.description"],
                    fields = listOf(
                        DiscordEmbedField(i18n["bn.reason"], banReason),
                        DiscordEmbedField(
                            i18n["bn.expiry.title"],
                            banExpiry?.let { i18n["bn.expiry.date"].f(it.getDate(), it.getTime()) }
                                ?: i18n["bn.expiry.none"]
                        )
                    ),
                    color = ERROR_RED,
                    thumbnail = BAN_LOGO_URL,
                    footer = poweredByEpiLink
                )
            } else {
                null
            }
        }

    override fun getErrorCommandReply(
        language: String,
        key: String,
        vararg objects: Any,
        titleObjects: List<Any>
    ): DiscordEmbed = language.ctx {
        DiscordEmbed(
            title = i18n["$key.title"].f(*titleObjects.toTypedArray()),
            description = i18n["$key.description"].f(*objects),
            color = ERROR_RED,
            footer = poweredByEpiLink
        )
    }

    override fun getWrongTargetCommandReply(language: String, target: String): DiscordEmbed = language.ctx {
        DiscordEmbed(
            title = i18n["cr.wt.title"],
            description = i18n["cr.wt.description"].f(target),
            fields = listOf(
                DiscordEmbedField(
                    i18n["cr.wt.help.title"],
                    i18n["cr.wt.help.description"].f(TARGETS_DOCS_URL)
                )
            ),
            color = ERROR_RED,
            footer = poweredByEpiLink
        )
    }

    override fun getSuccessCommandReply(language: String, messageKey: String, vararg messageArgs: Any): DiscordEmbed =
        language.ctx {
            DiscordEmbed(
                title = i18n["cr.ok.title"],
                description = i18n[messageKey].f(*messageArgs),
                color = OK_GREEN,
                footer = poweredByEpiLink
            )
        }

    override fun getHelpMessage(language: String, withAdminHelp: Boolean): DiscordEmbed = language.ctx {
        DiscordEmbed(
            title = i18n["help.title"],
            description = i18n["help.description"],
            fields = if (withAdminHelp) listOf(
                DiscordEmbedField(
                    i18n["help.admin.title"],
                    i18n["help.admin.description"].f(COMMANDS_DOCS_URL),
                    false
                )
            ) else listOf(),
            color = HELP_GREY,
            footer = poweredByEpiLink
        )
    }

    override fun getLangHelpEmbed(language: String): DiscordEmbed = language.ctx {
        // Allows for the preferred languages to show up before all of the available languages
        val languages = i18n.preferredLanguages + (i18n.availableLanguages - i18n.preferredLanguages)
        val languageLines = languages.joinToString("\n") { i18n.get(it, "languageLine") }
        DiscordEmbed(
            title = i18n["lang.help.title"],
            description = i18n["lang.help.description"],
            fields = listOf(
                DiscordEmbedField(i18n["lang.help.change.title"], i18n["lang.help.change.description"], false),
                DiscordEmbedField(i18n["lang.help.clear.title"], i18n["lang.help.clear.description"], false),
                DiscordEmbedField(i18n["lang.help.availableLanguages"], languageLines, false)
            ),
            color = HELP_GREY,
            footer = poweredByEpiLink
        )
    }

    override fun getWelcomeChooseLanguageEmbed(): DiscordEmbed = i18n.defaultLanguage.ctx {
        DiscordEmbed(
            title = i18n["welcomeLang.current"],
            description = i18n["welcomeLang.description"] + "\n\n" +
                    (i18n.preferredLanguages - i18n.defaultLanguage).joinToString("\n\n") {
                        i18n.get(it, "welcomeLang.change")
                    },
            color = HELLO_BLUE
            // No powered by epilink footer because this embed is sent right before another one. Having a "powered
            // by epilink" footer on both is obnoxious.
        )
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun Instant.getDate(): String =
    DateTimeFormatter.ISO_LOCAL_DATE.format(this.atOffset(ZoneOffset.UTC))

private fun Instant.getTime(): String =
    timeFormatter.format(this.atOffset(ZoneOffset.UTC))

/**
 * Retrieve the configuration for a given guild, or throw an error if such a configuration could not be found.
 *
 * Expects the guild to be monitored (i.e. expects a configuration to be present).
 *
 * @throws EpiLinkException If the configuration could not be found.
 */
fun DiscordConfiguration.getConfigForGuild(guildId: String): DiscordServerSpec =
    this.servers.firstOrNull { it.id == guildId }
        ?: throw EpiLinkException("Configuration not found, but guild was expected to be monitored")
