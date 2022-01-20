/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.services

import org.epilink.backend.db.DatabaseFacade
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.util.Locale

/**
 * A utility class for providing a nicer way of retrieving I18n strings
 */
class DiscordI18nContext(private val language: String) {
    /**
     * Get the key with the language inferred from this I18n context.
     */
    operator fun DiscordMessagesI18n.get(key: String) = get(language, key)

    /**
     * Format the given string with the given language, inferring the language from this i18n context.
     */
    fun String.f(objects: List<Any>): String {
        val locale = Locale.forLanguageTag(language)
        @Suppress("SpreadOperator") // No other choice here
        return String.format(locale, this, *objects.toTypedArray())
    }
}

/**
 * I18n support for the Discord messages
 */
interface DiscordMessagesI18n {
    /**
     * The default language. Guaranteed to be in [availableLanguages].
     */
    val defaultLanguage: String

    /**
     * A set of all of the supported languages (by language code)
     */
    val availableLanguages: Set<String>

    /**
     * A list of preferred languages
     */
    val preferredLanguages: List<String>

    /**
     * Get the preferred language of a user, or the default language if [discordId] is null.
     */
    suspend fun getLanguage(discordId: String?): String

    /**
     * Get the translated string for a given language. No particular formatting is performed.
     */
    fun get(language: String, key: String): String

    /**
     * Set the display language of EpiLink to the given language. Returns true on success, false if the language name is
     * invalid.
     */
    suspend fun setLanguage(discordId: String, language: String): Boolean
}

@OptIn(KoinApiExtension::class)
internal class DiscordMessagesI18nImpl(
    private val strings: Map<String, Map<String, String>>,
    override val defaultLanguage: String,
    preferred: List<String>
) : DiscordMessagesI18n, KoinComponent {

    private val db by inject<DatabaseFacade>()

    private val logger = LoggerFactory.getLogger("epilink.discord.i18n")

    override val availableLanguages = strings.keys

    override val preferredLanguages = preferred.distinct()

    override fun get(language: String, key: String) =
        strings[language]?.get(key)
            ?: strings[defaultLanguage]?.get(key).also {
                logger.warn("Key $key not found in language $language")
            } ?: key.also {
            logger.error("Key $key not found for in default language $defaultLanguage")
        }

    override suspend fun setLanguage(discordId: String, language: String): Boolean {
        return if (language in availableLanguages) {
            db.recordLanguagePreference(discordId, language)
            true
        } else {
            false
        }
    }

    override suspend fun getLanguage(discordId: String?): String {
        if (discordId == null) {
            return defaultLanguage
        }
        val x = db.getLanguagePreference(discordId)
        return if (x == null || x !in availableLanguages) defaultLanguage else x
    }
}
