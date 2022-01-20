/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.commands

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlin.test.Test
import org.epilink.backend.KoinBaseTest
import org.epilink.backend.config.DiscordEmbed
import org.epilink.backend.db.DatabaseFacade
import org.epilink.backend.discord.DiscordClientFacade
import org.epilink.backend.mockHere
import org.epilink.backend.services.Command
import org.epilink.backend.services.DiscordMessages
import org.epilink.backend.services.DiscordMessagesI18n
import org.epilink.backend.http.declareNoOpI18n
import org.koin.dsl.module

class LangCommandTest : KoinBaseTest<Command>(
    Command::class,
    module {
        single<Command> { LangCommand() }
    }
) {
    @Test
    fun `Test lang command help`() {
        val embed = mockk<DiscordEmbed>()
        declareNoOpI18n()
        mockHere<DiscordMessages> { every { getLangHelpEmbed(any()) } returns embed }
        val dcf = mockHere<DiscordClientFacade> { coEvery { sendChannelMessage("1234", embed) } returns "" }
        test {
            run("e!lang", "", null, "", "1234", "")
            dcf.sendChannelMessage("1234", embed)
        }
    }

    @Test
    fun `Test lang command clear`() {
        val embed = mockk<DiscordEmbed>()
        declareNoOpI18n()
        val df = mockHere<DatabaseFacade> { coEvery { clearLanguagePreference("iid") } just runs }
        mockHere<DiscordMessages> { every { getSuccessCommandReply(any(), "lang.clearSuccess") } returns embed }
        val dcf = mockHere<DiscordClientFacade> { coEvery { sendChannelMessage("1234", embed) } returns "" }
        test {
            run("e!lang clear", "clear", null, "iid", "1234", "")
        }
        coVerify {
            df.clearLanguagePreference("iid")
            dcf.sendChannelMessage("1234", embed)
        }
    }

    @Test
    fun `Test lang command set success`() {
        val embed = mockk<DiscordEmbed>()
        val i18n = mockHere<DiscordMessagesI18n> {
            coEvery { getLanguage(any()) } returns ""
            coEvery { setLanguage("iid", "lll") } returns true
        }
        mockHere<DiscordMessages> { every { getSuccessCommandReply(any(), "lang.success") } returns embed }
        val dcf = mockHere<DiscordClientFacade> { coEvery { sendChannelMessage("1234", embed) } returns "" }
        test {
            run("e!lang lll", "lll", null, "iid", "1234", "")
        }
        coVerify {
            i18n.setLanguage("iid", "lll")
            dcf.sendChannelMessage("1234", embed)
        }
    }

    @Test
    fun `Test lang command set fail`() {
        val embed = mockk<DiscordEmbed>()
        val i18n = mockHere<DiscordMessagesI18n> {
            coEvery { getLanguage(any()) } returns ""
            coEvery { setLanguage("iid", "lll") } returns false
        }
        mockHere<DiscordMessages> { every { getErrorCommandReply(any(), "lang.invalidLanguage", listOf("lll")) } returns embed }
        val dcf = mockHere<DiscordClientFacade> { coEvery { sendChannelMessage("1234", embed) } returns "" }
        test {
            run("e!lang lll", "lll", null, "iid", "1234", "")
        }
        coVerify {
            i18n.setLanguage("iid", "lll")
            dcf.sendChannelMessage("1234", embed)
        }
    }
}
