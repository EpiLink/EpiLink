/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
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