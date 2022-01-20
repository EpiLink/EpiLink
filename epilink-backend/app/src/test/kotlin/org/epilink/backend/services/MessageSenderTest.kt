/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.services

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlin.test.Test
import org.epilink.backend.KoinBaseTest
import org.epilink.backend.config.DiscordEmbed
import org.epilink.backend.discord.DiscordClientFacade
import org.epilink.backend.mockHere
import org.koin.dsl.module

class MessageSenderTest : KoinBaseTest<DiscordMessageSender>(
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
