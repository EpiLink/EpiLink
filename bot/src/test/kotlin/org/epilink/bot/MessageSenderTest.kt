package org.epilink.bot

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.epilink.bot.discord.DiscordEmbed
import org.epilink.bot.discord.LinkDiscordClientFacade
import org.epilink.bot.discord.LinkDiscordMessageSender
import org.epilink.bot.discord.LinkDiscordMessageSenderImpl
import org.koin.core.get
import org.koin.dsl.module
import kotlin.test.*

class MessageSenderTest : KoinBaseTest(
    module {
        single<LinkDiscordMessageSender> { LinkDiscordMessageSenderImpl() }
    }
) {
    @Test
    fun `Test sending message later`() {
        val embed = mockk<DiscordEmbed>()
        val dcf = mockHere<LinkDiscordClientFacade> {
            coEvery { sendDirectMessage("userid", embed) } just runs
        }
        runBlocking {
            get<LinkDiscordMessageSender>().sendDirectMessageLater("userid", embed)
                .join()
            coVerify {
                dcf.sendDirectMessage("userid", embed)
            }
        }
    }
}