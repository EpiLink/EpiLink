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
import org.epilink.bot.mockHere
import org.koin.dsl.module
import kotlin.test.*

class MessageSenderTest : EpiLinkBaseTest<DiscordMessageSender>(
    DiscordMessageSender::class,
    module {
        single<DiscordMessageSender> { DiscordMessageSenderImpl() }
    }
) {
    @Test
    fun `Test sending message later`() {
        val embed = mockk<DiscordEmbed>()
        val dcf = mockHere<DiscordClientFacade> {
            coEvery { sendDirectMessage("userid", embed) } just runs
        }
        test {
            sendDirectMessageLater("userid", embed).join()
            coVerify {
                dcf.sendDirectMessage("userid", embed)
            }
        }
    }
}
