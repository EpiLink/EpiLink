/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.misc

import discord4j.rest.util.Color
import org.epilink.bot.LinkException
import org.epilink.bot.discord.DiscordEmbed
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
}