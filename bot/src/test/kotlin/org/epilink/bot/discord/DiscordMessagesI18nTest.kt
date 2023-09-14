/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import guru.zoroark.tegral.di.dsl.put
import guru.zoroark.tegral.di.test.TegralSubjectTest
import guru.zoroark.tegral.di.test.mockk.putMock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.runs
import org.epilink.bot.db.DatabaseFacade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscordMessagesI18nTest : TegralSubjectTest<DiscordMessagesI18n>(
    DiscordMessagesI18n::class,
    {
        put<DiscordMessagesI18n> {
            DiscordMessagesI18nImpl(
                scope,
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
    fun `Test available languages`() = test {
        assertEquals(setOf("one", "two"), subject.availableLanguages)
    }

    @Test
    fun `Test key retrieval present in chosen`() = test {
        assertEquals("Buongiorno", subject.get("two", "hello"))
    }

    @Test
    fun `Test key fallback`() = test {
        assertEquals("Au revoir", subject.get("two", "goodbye"))
    }

    @Test
    fun `Test key second fallback`() =
        // Key not found in the current language nor in the default language
        test {
            assertEquals("seeyoulater", subject.get("two", "seeyoulater"))
        }

    @Test
    fun `Test set language with valid language`() = test {
        val db = putMock<DatabaseFacade> {
            coEvery { recordLanguagePreference("did", "two") } just runs
        }
        assertTrue(subject.setLanguage("did", "two"))
        coVerify { db.recordLanguagePreference("did", "two") }
    }

    @Test
    fun `Test set language with invalid language`() = test {
        assertFalse(subject.setLanguage("did", "three"))
    }

    @Test
    fun `Test get language no id`() = test {
        assertEquals("one", subject.getLanguage(null))
    }

    @Test
    fun `Test get language no preference`() = test {
        putMock<DatabaseFacade> {
            coEvery { getLanguagePreference("did") } returns null
        }
        assertEquals("one", subject.getLanguage("did"))
    }

    @Test
    fun `Test get language invalid preference`() = test {
        putMock<DatabaseFacade> {
            coEvery { getLanguagePreference("did") } returns "LQSDLKJQHSD"
        }
        assertEquals("one", subject.getLanguage("did"))
    }

    @Test
    fun `Test get language valid preference`() = test {
        putMock<DatabaseFacade> {
            coEvery { getLanguagePreference("did") } returns "two"
        }
        assertEquals("two", subject.getLanguage("did"))
    }
}
