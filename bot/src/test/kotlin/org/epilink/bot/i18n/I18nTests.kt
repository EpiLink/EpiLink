/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.i18n

import org.epilink.bot.CliArgs
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.*

/**
 * These tests are specifically intended for testing the language files themselves. For tests on the actual class that
 * handles all of this, see [org.epilink.bot.DiscordMessagesI18nTest].
 */
class I18nTests {

    private val languages: MutableMap<String, Map<String, String>> = mutableMapOf()

    @BeforeEach
    fun setUp() {
        val languagesList =
            CliArgs::class.java.getResourceAsStream("/discord_i18n/languages")!!.bufferedReader().use { it.readLines() }
        languagesList.forEach {
            CliArgs::class.java.getResourceAsStream("/discord_i18n/strings_$it.properties").also { stream ->
                assumeTrue(stream != null, "Failed to load language $it (not found or not available)")
            }!!.bufferedReader().use { reader ->
                val p = Properties()
                p.load(reader)
                val map = mutableMapOf<String, String>()
                p.forEach { (k, v) -> map[k.toString()] = v.toString() }
                languages[it] = map
            }
        }
        val notLoaded = languagesList - languages.keys
        assumeTrue(notLoaded.isEmpty(), "Some languages were not loaded ($notLoaded)")
    }

    @Test
    fun `Test language list validity`() {
        val missingLanguageLine = languages.mapNotNull { (k, v) ->
            if (!v.containsKey("languageLine") || !v.containsKey("welcomeLang.current") || !v.containsKey("welcomeLang.description")) {
                k
            } else null
        }
        if (missingLanguageLine.isNotEmpty()) {
            fail(
                "The following language(s) are missing a languageLine, welcomeLang.current and/or welcomeLang.description key: " +
                    missingLanguageLine.joinToString(", ")
            )
        }
    }
}
