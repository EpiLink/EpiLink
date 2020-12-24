/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import io.mockk.coEvery
import io.mockk.every
import org.epilink.bot.KoinBaseTest
import org.epilink.bot.LinkException
import org.epilink.bot.config.LinkDiscordServerSpec
import org.epilink.bot.config.LinkPrivacy
import org.epilink.bot.mockHere
import org.epilink.bot.web.declareNoOpI18n
import org.koin.dsl.module
import org.koin.test.get
import java.time.Duration
import java.time.Instant
import kotlin.test.*

class MessagesTest : KoinBaseTest<LinkDiscordMessages>(
    LinkDiscordMessages::class,
    module {
        single<LinkDiscordMessages> { LinkDiscordMessagesImpl() }
    }
) {

    @Test
    fun `Test welcome generation does not output when`() {
        mockDiscordConfigHere {
            server("guildid") {
                enableWelcomeMessage = false
            }
        }
        val dm = get<LinkDiscordMessages>()
        assertNull(dm.getGreetingsEmbed("", "guildid", "My Guild"))
    }

    @Test
    fun `Test welcome generation outputs sane default message`() {
        mockDiscordConfigHere {
            welcomeUrl = "MyVeryTrueUrl"
            server("guildid") {
                enableWelcomeMessage = true
                welcomeEmbed = null
            }
        }
        mockHere<LinkDiscordMessagesI18n> {
            coEvery { getLanguage(any()) } returns ""
            defaultMock()
            every { get(any(), "greet.title") } returns "greet.title::%s"
            every { get(any(), "greet.welcome") } returns "greet.welcome::%s"
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
        mockDiscordConfigHere {
            welcomeUrl = "YEET"
            server("guildid") {
                enableWelcomeMessage = true
                welcomeEmbed = embed
            }
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
        mockHere<LinkDiscordMessagesI18n> {
            defaultMock()
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
        mockHere<LinkDiscordMessagesI18n> {
            defaultMock()
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
        mockHere<LinkDiscordMessagesI18n> {
            defaultMock()
            every { get(any(), "bn.expiry.date") } returns "date::%s::%s"
        }
        val dm = get<LinkDiscordMessages>()
        val embed = dm.getBanNotification("", "You are now banned, RIP.", Instant.parse("2020-02-03T13:37:01.02Z"))
        assertNotNull(embed)
        assertEquals("You are now banned, RIP.", embed.fields[0].value)
        assertEquals("date::2020-02-03::13:37", embed.fields[1].value)
    }

    @Test
    fun `Test error command reply`() {
        mockHere<LinkDiscordMessagesI18n> {
            defaultMock()
            every { get(any(), "kkk.title") } returns "title::%s::%s"
            every { get(any(), "kkk.description") } returns "desc::%s::%s"
        }
        test {
            val embed =
                getErrorCommandReply("", "kkk", titleObjects = listOf("to1", "to2"), objects = arrayOf("obj1", "obj2"))
            assertEquals("#8A0303", embed.color)
            assertEquals("title::to1::to2", embed.title)
            assertEquals("desc::obj1::obj2", embed.description)
        }
    }

    @Test
    fun `Test wrong target command reply`() {
        mockHere<LinkDiscordMessagesI18n> {
            defaultMock()
            every { get(any(), "cr.wt.description") } returns "description::%s"
            every { get(any(), "cr.wt.help.description") } returns "helpDescription::%s"
        }
        test {
            val embed = getWrongTargetCommandReply("", "le target")
            assertEquals("#8A0303", embed.color)
            assertEquals("cr.wt.title", embed.title)
            assertEquals("description::le target", embed.description)
            assertEquals("cr.wt.help.title", embed.fields[0].name)
            assertTrue(embed.fields[0].value.startsWith("helpDescription::https://"), "help description has a url")
        }
    }

    @Test
    fun `Test config for guild found`() {
        lateinit var serverId2: LinkDiscordServerSpec
        val cfg = mockDiscordConfig {
            server("id1")
            serverId2 = server("id2")
        }
        assertSame(serverId2, cfg.getConfigForGuild("id2"))
    }

    @Test
    fun `Test config for guild not found`() {
        val cfg = mockDiscordConfig {
            server("id1")
            server("id2")
        }
        assertFailsWith<LinkException> { cfg.getConfigForGuild("id3") }
    }
}