/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.discord

import discord4j.discordjson.json.EmbedData
import discord4j.rest.util.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.epilink.backend.common.EpiLinkException
import org.epilink.backend.config.DiscordEmbed
import org.epilink.backend.config.DiscordEmbedAuthor
import org.epilink.backend.config.DiscordEmbedField
import org.epilink.backend.config.DiscordEmbedFooter

class DiscordEmbedTest {
    @Test
    fun `Test embed null color`() {
        assertNull(DiscordEmbed(color = null).d4jColor)
    }

    @Test
    fun `Test embed hex color`() {
        assertEquals(Color.of(0xabcdef), DiscordEmbed(color = "#abcdef").d4jColor)
    }

    @Test
    fun `Test embed invalid hex color`() {
        assertFailsWith<EpiLinkException> { DiscordEmbed(color = "#qsdfmljkdfqs").d4jColor }
    }

    @Test
    fun `Test embed named color`() {
        assertEquals(Color.BISMARK, DiscordEmbed(color = "bismark").d4jColor)
    }

    @Test
    fun `Test embed invalid named color`() {
        assertFailsWith<EpiLinkException> { DiscordEmbed(color = "nope").d4jColor }
    }

    @Test
    fun `Test embed creation - title`() {
        embedFromAndTest(DiscordEmbed(title = "a"), "a") { title().get() }
    }

    @Test
    fun `Test embed creation - description`() {
        embedFromAndTest(DiscordEmbed(description = "a"), "a") { description().get() }
    }

    @Test
    fun `Test embed creation - url`() {
        embedFromAndTest(DiscordEmbed(url = "a"), "a") { url().get() }
    }

    @Test
    fun `Test embed creation - color`() {
        embedFromAndTest(DiscordEmbed(color = "#FFFFFF"), 0xFFFFFF) { color().get() }
    }

    @Test
    fun `Test embed creation - footer`() {
        embedFromAndTest(DiscordEmbed(footer = DiscordEmbedFooter("a", "b")), "a" to "b") {
            footer().get().text() to footer().get().iconUrl().get()
        }
    }

    @Test
    fun `Test embed creation - image`() {
        embedFromAndTest(DiscordEmbed(image = "a"), "a") { image().get().url().get() }
    }

    @Test
    fun `Test embed creation - thumbnail`() {
        embedFromAndTest(DiscordEmbed(thumbnail = "a"), "a") { thumbnail().get().url().get() }
    }

    @Test
    fun `Test embed creation - author`() {
        embedFromAndTest(DiscordEmbed(author = DiscordEmbedAuthor("a", "b", "c")), "a" to "b" to "c") {
            author().get().name().get() to author().get().url().get() to author().get().iconUrl().get()
        }
    }

    @Test
    fun `Test embed creation - fields`() {
        val embed = DiscordEmbed(
            fields = listOf(
                DiscordEmbedField(name = "First field", value = "abcd", true),
                DiscordEmbedField(name = "Second field", value = "efgh", false)
            )
        )
        embed.toDiscord4J().asRequest().let {
            val fields = it.fields().get()
            assertEquals(2, fields.size)
            // Field 1
            val field1 = fields[0]
            assertTrue(field1.inline().get())
            assertEquals("First field", field1.name())
            assertEquals("abcd", field1.value())
            val field2 = fields[1]
            assertFalse(field2.inline().get())
            assertEquals("Second field", field2.name())
            assertEquals("efgh", field2.value())
        }
    }

    private fun <T> embedFromAndTest(embed: DiscordEmbed, expected: T, actual: EmbedData.() -> T) =
        embed.toDiscord4J().asRequest().let {
            assertEquals(expected, actual(it))
        }
}
