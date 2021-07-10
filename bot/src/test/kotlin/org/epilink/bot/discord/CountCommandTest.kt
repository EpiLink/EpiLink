package org.epilink.bot.discord

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.epilink.bot.KoinBaseTest
import org.epilink.bot.discord.cmd.CountCommand
import org.epilink.bot.mockHere
import org.epilink.bot.web.declareNoOpI18n
import org.koin.dsl.module
import kotlin.test.Test

class CountCommandTest : KoinBaseTest<Command>(
    Command::class,
    module {
        single<Command> { CountCommand() }
    }
) {
    @Test
    fun `Test wrong target command`() {
        mockHere<LinkDiscordTargets> {
            every { parseDiscordTarget("OWO OWO OWO") } returns TargetParseResult.Error
        }
        val embed = mockk<DiscordEmbed>()
        declareNoOpI18n()
        mockHere<LinkDiscordMessages> {
            every { getWrongTargetCommandReply(any(), "OWO OWO OWO") } returns embed
        }
        val f = mockHere<LinkDiscordClientFacade> {
            coEvery { sendChannelMessage("channel", embed) } returns ""
        }
        test {
            run(
                fullCommand = "",
                commandBody = "OWO OWO OWO",
                sender = mockk(),
                senderId = "",
                channelId = "channel",
                guildId = ""
            )
        }
        coVerify { f.sendChannelMessage("channel", embed) }
    }

}
