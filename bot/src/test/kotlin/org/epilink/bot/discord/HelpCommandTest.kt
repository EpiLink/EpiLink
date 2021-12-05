/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import io.mockk.*
import org.epilink.bot.KoinBaseTest
import org.epilink.bot.discord.cmd.HelpCommand
import org.epilink.bot.mockHere
import org.epilink.bot.web.declareNoOpI18n
import org.koin.dsl.module
import kotlin.test.Test

class HelpCommandTest : KoinBaseTest<Command>(
    Command::class,
    module {
        single<Command> { HelpCommand() }
    }
) {
    @Test
    fun `Test help command`() {
        val embed = mockk<DiscordEmbed>()
        declareNoOpI18n()
        mockHere<DiscordMessages> { every { getHelpMessage(any(), false) } returns embed }
        mockHere<DiscordClientFacade> { coEvery { sendChannelMessage("1234", embed) } returns "" }
        test {
            run("e!help", "", null, "", "1234", "")
        }
    }
}
