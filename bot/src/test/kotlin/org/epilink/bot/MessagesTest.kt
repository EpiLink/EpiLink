/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.epilink.bot.config.LinkDiscordConfig
import org.epilink.bot.config.LinkPrivacy
import org.epilink.bot.discord.DiscordEmbed
import org.epilink.bot.discord.LinkDiscordMessages
import org.epilink.bot.discord.LinkDiscordMessagesI18n
import org.epilink.bot.discord.LinkDiscordMessagesImpl
import org.koin.dsl.module
import org.koin.test.get
import java.time.Duration
import java.time.Instant
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
        assertNull(dm.getGreetingsEmbed("", "guildid", "My Guild"))
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
        val keySlot = slot<String>()
        mockHere<LinkDiscordMessagesI18n> {
            coEvery { getLanguage(any()) } returns ""
            every { get(any(), capture(keySlot)) } answers {
                keySlot.captured.let {
                    if(it == "greet.title" || it == "greet.welcome")
                        "$it::%s"
                    else it
                }
            }
        }
        val dm = get<LinkDiscordMessages>()
        val embed = dm.getGreetingsEmbed("", "guildid", "My Guild")
        assertNotNull(embed)
        assertEquals("greet.title::My Guild", embed.title)
        assertEquals("greet.welcome::My Guild", embed.description)
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
        val embedReturned = dm.getGreetingsEmbed("", "guildid", "My Guild")
        assertSame(embed, embedReturned)
    }

    @Test
    fun `Test could not join`() {
        mockHere<LinkDiscordMessagesI18n> {
            every { get(any(), "cnj.title") } returns "title::%s"
            every { get(any(), "cnj.description") } returns "description::%s"
            every { get(any(), "cnj.reason") } returns "reason"
            every { get(any(), "poweredBy") } returns "poweredBy"
        }
        val dm = get<LinkDiscordMessages>()
        val embed = dm.getCouldNotJoinEmbed("", "My Guild", "My Reason")
        assertEquals("description::My Guild", embed.description)
        assertEquals("title::My Guild", embed.title)
    }

    @Test
    fun `Test identity access embed not sent on specific cases`() {
        mockHere<LinkPrivacy> {
            every { shouldNotify(any()) } returns false
        }
        val dm = get<LinkDiscordMessages>()
        val embed = dm.getIdentityAccessEmbed("", true, "TestAuthor", "TestReason")
        assertNull(embed, "Should not give an embed")
        val embed2 = dm.getIdentityAccessEmbed("", false, "TrueAuthor", "TestReason")
        assertNull(embed2, "Should not give an embed")
    }

    @Test
    fun `Test identity access embed`() {
        mockHere<LinkPrivacy> {
            every { shouldNotify(any()) } returns true
            every { shouldDiscloseIdentity(true) } returns true
        }
        val keySlot = slot<String>()
        mockHere<LinkDiscordMessagesI18n> {
            every { get(any(), capture(keySlot)) } answers { keySlot.captured }
            every { get(any(), "ida.accessAuthorAuto") } returns "authorauto::%s"
        }
        val dm = get<LinkDiscordMessages>()
        val embed = dm.getIdentityAccessEmbed("", true, "TestAuthor", "TestReason")
        assertNotNull(embed)
        assertEquals("authorauto::TestAuthor", embed.description)
        assertTrue(embed.fields.any { it.value.contains("TestReason") })
    }

    @Test
    fun `Test identity access embed not automated`() {
        mockHere<LinkPrivacy> {
            every { shouldNotify(any()) } returns true
            every { shouldDiscloseIdentity(false) } returns true
        }
        val keySlot = slot<String>()
        mockHere<LinkDiscordMessagesI18n> {
            every { get(any(), capture(keySlot)) } answers { keySlot.captured }
            every { get(any(), "ida.accessAuthor") } returns "author::%s"
        }
        val dm = get<LinkDiscordMessages>()
        val embed = dm.getIdentityAccessEmbed("", false, "TestAuthor", "TestReason")
        assertNotNull(embed)
        assertEquals("author::TestAuthor", embed.description)
        assertTrue(embed.fields.any { it.value.contains("TestReason") })
    }

    @Test
    fun `Test identity access embed not automated, no disclose identity`() {
        mockHere<LinkPrivacy> {
            every { shouldNotify(any()) } returns true
            every { shouldDiscloseIdentity(false) } returns false
        }
        declareNoOpI18n()
        val dm = get<LinkDiscordMessages>()
        val embed = dm.getIdentityAccessEmbed("", false, "TestAuthor", "TestReason")
        assertNotNull(embed)
        assertFalse(embed.description!!.contains("TestAuthor"))
        assertTrue(embed.fields.any { it.value == "TestReason" })
    }

    @Test
    fun `Test disabled ban notification`() {
        mockHere<LinkPrivacy> {
            every { notifyBans } returns false
        }
        val dm = get<LinkDiscordMessages>()
        val embed = dm.getBanNotification("", "Hello", Instant.now() + Duration.ofHours(230))
        assertNull(embed)
    }

    @Test
    fun `Test ban notification`() {
        mockHere<LinkPrivacy> {
            every { notifyBans } returns true
        }
        val keySlot = slot<String>()
        mockHere<LinkDiscordMessagesI18n> {
            every { get(any(), capture(keySlot)) } answers { keySlot.captured }
            every { get(any(), "bn.expiry.date") } returns "date::%s::%s"
        }
        val dm = get<LinkDiscordMessages>()
        val embed = dm.getBanNotification("", "You are now banned, RIP.", Instant.parse("2020-02-03T13:37:01.02Z"))
        assertNotNull(embed)
        assertEquals("You are now banned, RIP.", embed.fields[0].value)
        assertEquals("date::2020-02-03::13:37", embed.fields[1].value)
    }
}