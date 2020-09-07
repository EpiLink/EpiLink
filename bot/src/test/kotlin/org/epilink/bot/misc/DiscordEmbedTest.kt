/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.misc

import discord4j.core.spec.EmbedCreateSpec
import discord4j.discordjson.json.EmbedData
import discord4j.rest.util.Color
import org.epilink.bot.LinkException
import org.epilink.bot.discord.DiscordEmbed
import org.epilink.bot.discord.DiscordEmbedAuthor
import org.epilink.bot.discord.DiscordEmbedFooter
import org.epilink.bot.discord.from
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

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
        assertFailsWith<LinkException> { DiscordEmbed(color = "#qsdfmljkdfqs").d4jColor }
    }

    @Test
    fun `Test embed named color`() {
        assertEquals(Color.BISMARK, DiscordEmbed(color = "bismark").d4jColor)
    }

    @Test
    fun `Test embed invalid named color`() {
        assertFailsWith<LinkException> { DiscordEmbed(color = "nope").d4jColor }
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

    // TODO test embeds

    private fun <T> embedFromAndTest(embed: DiscordEmbed, expected: T, actual: EmbedData.() -> T) =
        EmbedCreateSpec().apply { from(embed) }.asRequest().let {
            assertEquals(expected, actual(it))
        }

}