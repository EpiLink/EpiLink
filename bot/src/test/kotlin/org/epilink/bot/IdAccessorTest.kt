/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.epilink.bot.db.*
import org.epilink.bot.discord.DiscordEmbed
import org.epilink.bot.discord.LinkDiscordMessageSender
import org.epilink.bot.discord.LinkDiscordMessages
import org.koin.core.get
import org.koin.dsl.module
import kotlin.test.*

class IdAccessorTest : KoinBaseTest(
    module {
        single<LinkIdAccessor> { LinkIdAccessorImpl() }
    }
) {
    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test automated id access success`() {
        val u = mockk<LinkUser>()
        val sd = mockHere<LinkServerDatabase> {
            coEvery { getUser("targetid") } returns u
            coEvery { isUserIdentifiable("targetid") } returns true
            coEvery { accessIdentity(u, false, "authorrr", "reasonnn") } returns "identity"
        }
        val embed = mockk<DiscordEmbed>()
        val dms = mockHere<LinkDiscordMessageSender> {
            every { sendDirectMessageLater("targetid", embed) } returns mockk()
        }
        val dm = mockHere<LinkDiscordMessages> {
            every { getIdentityAccessEmbed(false, "authorrr", "reasonnn") } returns embed
        }
        runBlocking {
            val id = get<LinkIdAccessor>().accessIdentity("targetid", false, "authorrr", "reasonnn")
            assertEquals("identity", id)
        }
        coVerify {
            dm.getIdentityAccessEmbed(false, "authorrr", "reasonnn")
            dms.sendDirectMessageLater("targetid", embed)
            sd.accessIdentity(u, false, "authorrr", "reasonnn")
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test automated id access user does not exist`() {
        mockHere<LinkServerDatabase> {
            coEvery { getUser("targetid") } returns null
        }
        runBlocking {
            val exc = assertFailsWith<LinkException> {
                get<LinkIdAccessor>().accessIdentity("targetid", true, "authorrr", "reasonnn")
            }
            assertEquals("User does not exist", exc.message)
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test automated id access user is not identifiable`() {
        mockHere<LinkServerDatabase> {
            coEvery { getUser("targetid") } returns mockk()
            coEvery { isUserIdentifiable("targetid") } returns false
        }
        runBlocking {
            val exc = assertFailsWith<LinkException> {
                get<LinkIdAccessor>().accessIdentity("targetid", true, "authorrr", "reasonnn")
            }
            assertEquals("User is not identifiable", exc.message)
        }
    }
}