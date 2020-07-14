/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.commands

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.epilink.bot.KoinBaseTest
import org.epilink.bot.discord.Command
import org.epilink.bot.discord.DiscordEmbed
import org.epilink.bot.discord.LinkDiscordClientFacade
import org.epilink.bot.discord.LinkDiscordMessages
import org.epilink.bot.discord.cmd.HelpCommand
import org.epilink.bot.mockHere
import org.koin.core.get
import org.koin.dsl.module
import kotlin.test.Test

class HelpCommandTest : KoinBaseTest(
    module {
        single<Command> { HelpCommand() }
    }
) {
    @Test
    fun `Test help command`() {
        val embed = mockk<DiscordEmbed>()
        mockHere<LinkDiscordMessages> { every { getHelpMessage(false) } returns embed }
        mockHere<LinkDiscordClientFacade> { coEvery { sendChannelMessage("1234", embed) } just runs }
        test {
            run("e!help", "", mockk(), "1234", "")
        }
    }

    private fun test(block: suspend Command.() -> Unit) {
        runBlocking { get<Command>().block() }
    }
}