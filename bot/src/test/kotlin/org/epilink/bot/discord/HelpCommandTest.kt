/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import guru.zoroark.shedinja.dsl.put
import guru.zoroark.shedinja.test.ShedinjaBaseTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.epilink.bot.discord.cmd.HelpCommand
import org.epilink.bot.putMock
import org.epilink.bot.stest
import org.epilink.bot.web.declareNoOpI18n
import kotlin.test.Test

class HelpCommandTest : ShedinjaBaseTest<Command>(
    Command::class, {
        put<Command> { HelpCommand() }
    }
) {
    @Test
    fun `Test help command`() = stest {
        val embed = mockk<DiscordEmbed>()
        declareNoOpI18n()
        putMock<DiscordMessages> { every { getHelpMessage(any(), false) } returns embed }
        putMock<DiscordClientFacade> { coEvery { sendChannelMessage("1234", embed) } returns "" }
        subject.run("e!help", "", null, "", "1234", "")
    }
}
