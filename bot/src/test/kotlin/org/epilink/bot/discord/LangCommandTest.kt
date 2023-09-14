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
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.epilink.bot.db.DatabaseFacade
import org.epilink.bot.discord.cmd.LangCommand
import org.epilink.bot.web.declareNoOpI18n
import kotlin.test.Test

class LangCommandTest : TegralSubjectTest<Command>(
    Command::class,
    { put<Command>(::LangCommand) }
) {
    @Test
    fun `Test lang command help`() = test {
        val embed = mockk<DiscordEmbed>()
        declareNoOpI18n()
        putMock<DiscordMessages> { every { getLangHelpEmbed(any()) } returns embed }
        val dcf = putMock<DiscordClientFacade> { coEvery { sendChannelMessage("1234", embed) } returns "" }
        subject.run("e!lang", "", null, "", "1234", "")
        dcf.sendChannelMessage("1234", embed)
        Unit
    }

    @Test
    fun `Test lang command clear`() = test {
        val embed = mockk<DiscordEmbed>()
        declareNoOpI18n()
        val df = putMock<DatabaseFacade> { coEvery { clearLanguagePreference("iid") } just runs }
        putMock<DiscordMessages> { every { getSuccessCommandReply(any(), "lang.clearSuccess") } returns embed }
        val dcf = putMock<DiscordClientFacade> { coEvery { sendChannelMessage("1234", embed) } returns "" }
        subject.run("e!lang clear", "clear", null, "iid", "1234", "")
        coVerify {
            df.clearLanguagePreference("iid")
            dcf.sendChannelMessage("1234", embed)
        }
    }

    @Test
    fun `Test lang command set success`() = test {
        val embed = mockk<DiscordEmbed>()
        val i18n = putMock<DiscordMessagesI18n> {
            coEvery { getLanguage(any()) } returns ""
            coEvery { setLanguage("iid", "lll") } returns true
        }
        putMock<DiscordMessages> { every { getSuccessCommandReply(any(), "lang.success") } returns embed }
        val dcf = putMock<DiscordClientFacade> { coEvery { sendChannelMessage("1234", embed) } returns "" }
        subject.run("e!lang lll", "lll", null, "iid", "1234", "")
        coVerify {
            i18n.setLanguage("iid", "lll")
            dcf.sendChannelMessage("1234", embed)
        }
    }

    @Test
    fun `Test lang command set fail`() = test {
        val embed = mockk<DiscordEmbed>()
        val i18n = putMock<DiscordMessagesI18n> {
            coEvery { getLanguage(any()) } returns ""
            coEvery { setLanguage("iid", "lll") } returns false
        }
        putMock<DiscordMessages> {
            every { getErrorCommandReply(any(), "lang.invalidLanguage", listOf("lll")) } returns embed
        }
        val dcf = putMock<DiscordClientFacade> { coEvery { sendChannelMessage("1234", embed) } returns "" }
        subject.run("e!lang lll", "lll", null, "iid", "1234", "")

        coVerify {
            i18n.setLanguage("iid", "lll")
            dcf.sendChannelMessage("1234", embed)
        }
    }
}
