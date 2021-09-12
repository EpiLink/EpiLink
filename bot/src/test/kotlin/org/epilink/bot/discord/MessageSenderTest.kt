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
import io.mockk.*
import org.epilink.bot.putMock
import org.epilink.bot.stest
import kotlin.test.*

class MessageSenderTest : ShedinjaBaseTest<DiscordMessageSender>(
    DiscordMessageSender::class, {
        put<DiscordMessageSender>(::DiscordMessageSenderImpl)
    }
) {
    @Test
    fun `Test sending message later`() = stest {
        val embed = mockk<DiscordEmbed>()
        val dcf = putMock<DiscordClientFacade> {
            coEvery { sendDirectMessage("userid", embed) } just runs
        }
        subject.sendDirectMessageLater("userid", embed).join()
        coVerify {
            dcf.sendDirectMessage("userid", embed)
        }
    }
}
