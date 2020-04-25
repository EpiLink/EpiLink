/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import io.mockk.every
import io.mockk.mockk
import org.epilink.bot.config.LinkDiscordConfig
import org.epilink.bot.config.LinkPrivacy
import org.epilink.bot.discord.DiscordEmbed
import org.epilink.bot.discord.LinkDiscordMessages
import org.epilink.bot.discord.LinkDiscordMessagesImpl
import org.koin.dsl.module
import org.koin.test.get
import kotlin.test.*

class MessagesTest : KoinBaseTest(
    module {
        single<LinkDiscordMessages> { LinkDiscordMessagesImpl() }
    }
) {

    @Test
    fun `Test welcome generation does not output when`() {
        mockHere<LinkDiscordConfig> {
            every { servers } returns listOf(
                mockk {
                    every { id } returns "guildid"
                    every { enableWelcomeMessage } returns false
                }
            )
        }
        val dm = get<LinkDiscordMessages>()
        assertNull(dm.getGreetingsEmbed("guildid", "My Guild"))
    }

    @Test
    fun `Test welcome generation outputs sane default message`() {
        mockHere<LinkDiscordConfig> {
            every { welcomeUrl } returns "MyVeryTrueUrl"
            every { servers } returns listOf(
                mockk {
                    every { id } returns "guildid"
                    every { enableWelcomeMessage } returns true
                    every { welcomeEmbed } returns null
                }
            )
        }
        val dm = get<LinkDiscordMessages>()
        val embed = dm.getGreetingsEmbed("guildid", "My Guild")
        assertNotNull(embed)
        assertNotNull(embed.description)
        assertTrue(embed.description!!.contains("My Guild"), "Embed description should contain guild name")
        assertNotNull(embed.color, "Has color")
        assertNotNull(embed.thumbnail, "Has a thumbnail")
        assertNotNull(embed.footer, "Has a footer")
        assertTrue(embed.fields.any { it.value.contains("MyVeryTrueUrl") }, "Has a URL in fields")
    }

    @Test
    fun `Test welcome generation outputs custom message`() {
        val embed = DiscordEmbed()
        mockHere<LinkDiscordConfig> {
            every { welcomeUrl } returns "YEET"
            every { servers } returns listOf(
                mockk {
                    every { id } returns "guildid"
                    every { enableWelcomeMessage } returns true
                    every { welcomeEmbed } returns embed
                }
            )
        }
        val dm = get<LinkDiscordMessages>()
        val embedReturned = dm.getGreetingsEmbed("guildid", "My Guild")
        assertSame(embed, embedReturned)
    }

    @Test
    fun `Test could not join`() {
        val dm = get<LinkDiscordMessages>()
        val embed = dm.getCouldNotJoinEmbed("My Guild", "My Reason")
        assertTrue(embed.description!!.contains("My Guild"), "Guild name is mentioned")
        assertTrue(embed.fields.any { it.value.contains("My Reason") }, "Reason is mentioned")
    }

    @Test
    fun `Test identity access embed not sent on specific cases`() {
        mockHere<LinkPrivacy> {
            every { shouldNotify(any()) } returns false
        }
        val dm = get<LinkDiscordMessages>()
        val embed = dm.getIdentityAccessEmbed(true, "TestAuthor", "TestReason")
        assertNull(embed, "Should not give an embed")
        val embed2 = dm.getIdentityAccessEmbed(false, "TrueAuthor", "TestReason")
        assertNull(embed2, "Should not give an embed")
    }

    @Test
    fun `Test identity access embed`() {
        mockHere<LinkPrivacy> {
            every { shouldNotify(any()) } returns true
            every { shouldDiscloseIdentity(true) } returns true
        }
        val dm = get<LinkDiscordMessages>()
        val embed = dm.getIdentityAccessEmbed(true, "TestAuthor", "TestReason")
        assertNotNull(embed)
        assertTrue(embed.description!!.contains("TestAuthor"))
        assertTrue(embed.fields.any { it.value.contains("TestReason") })
        assertTrue(embed.fields.any { it.value.contains("auto") })
    }

    @Test
    fun `Test identity access embed not automated`() {
        mockHere<LinkPrivacy> {
            every { shouldNotify(any()) } returns true
            every { shouldDiscloseIdentity(false) } returns true
        }
        val dm = get<LinkDiscordMessages>()
        val embed = dm.getIdentityAccessEmbed(false, "TestAuthor", "TestReason")
        assertNotNull(embed)
        assertTrue(embed.description!!.contains("TestAuthor"))
        assertTrue(embed.fields.any { it.value.contains("TestReason") })
        assertTrue(embed.fields.any { it.name.contains("need help") })
    }

    @Test
    fun `Test identity access embed not automated, no disclose identity`() {
        mockHere<LinkPrivacy> {
            every { shouldNotify(any()) } returns true
            every { shouldDiscloseIdentity(false) } returns false
        }
        val dm = get<LinkDiscordMessages>()
        val embed = dm.getIdentityAccessEmbed(false, "TestAuthor", "TestReason")
        assertNotNull(embed)
        assertFalse(embed.description!!.contains("TestAuthor"))
        assertTrue(embed.fields.any { it.value.contains("TestReason") })
        assertTrue(embed.fields.any { it.name.contains("need help") })
    }
}