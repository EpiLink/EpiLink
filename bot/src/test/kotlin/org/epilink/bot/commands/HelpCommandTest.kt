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
        mockHere<LinkDiscordMessages> { every { getHelpMessage() } returns embed }
        mockHere<LinkDiscordClientFacade> { coEvery { sendChannelMessage("1234", embed) } just runs }
        test {
            run("e!help", "", mockk(), "1234", "")
        }
    }

    private fun test(block: suspend Command.() -> Unit) {
        runBlocking { get<Command>().block() }
    }
}