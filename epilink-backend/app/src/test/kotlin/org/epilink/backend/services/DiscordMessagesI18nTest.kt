/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.services

import io.mockk.*
import org.epilink.backend.KoinBaseTest
import org.epilink.backend.db.DatabaseFacade
import org.epilink.backend.mockHere
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscordMessagesI18nTest : KoinBaseTest<DiscordMessagesI18n>(
    DiscordMessagesI18n::class,
    module {
        single<DiscordMessagesI18n> {
            DiscordMessagesI18nImpl(
                mapOf(
                    "one" to mapOf("hello" to "Bonjour", "goodbye" to "Au revoir"),
                    "two" to mapOf("hello" to "Buongiorno")
                ),
                "one",
                listOf("one")
            )
        }
    }
) {

    @Test
    fun `Test available languages`() {
        test {
            assertEquals(setOf("one", "two"), availableLanguages)
        }
    }
    @Test
    fun `Test key retrieval present in chosen`() {
        test {
            assertEquals("Buongiorno", get("two", "hello"))
        }
    }

    @Test
    fun `Test key fallback`() {
        test {
            assertEquals("Au revoir", get("two", "goodbye"))
        }
    }

    @Test
    fun `Test key second fallback`() {
        // Key not found in the current language nor in the default language
        test {
            assertEquals("seeyoulater", get("two", "seeyoulater"))
        }
    }

    @Test
    fun `Test set language with valid language`() {
        val db = mockHere<DatabaseFacade> {
            coEvery { recordLanguagePreference("did", "two") } just runs
        }
        test { assertTrue(setLanguage("did", "two")) }
        coVerify { db.recordLanguagePreference("did", "two") }
    }

    @Test
    fun `Test set language with invalid language`() {
        test { assertFalse(setLanguage("did", "three")) }
    }

    @Test
    fun `Test get language no id`() {
        test { assertEquals("one", getLanguage(null)) }
    }

    @Test
    fun `Test get language no preference`() {
        mockHere<DatabaseFacade> {
            coEvery { getLanguagePreference("did") } returns null
        }
        test { assertEquals("one", getLanguage("did")) }
    }

    @Test
    fun `Test get language invalid preference`() {
        mockHere<DatabaseFacade> {
            coEvery { getLanguagePreference("did") } returns "LQSDLKJQHSD"
        }
        test { assertEquals("one", getLanguage("did")) }
    }

    @Test
    fun `Test get language valid preference`() {
        mockHere<DatabaseFacade> {
            coEvery { getLanguagePreference("did") } returns "two"
        }
        test { assertEquals("two", getLanguage("did")) }
    }
}
