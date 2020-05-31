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
import org.epilink.bot.db.*
import org.epilink.bot.discord.LinkBanManager
import org.epilink.bot.discord.LinkBanManagerImpl
import org.epilink.bot.discord.LinkRoleManager
import org.koin.core.get
import org.koin.dsl.module
import java.util.*
import kotlin.test.*

class BanManagerTest : KoinBaseTest(
    module {
        single<LinkBanManager> { LinkBanManagerImpl() }
    }
) {
    @Test
    fun `Test ban on non-existing user`() {
        mockHere<LinkServerDatabase> {
            coEvery { getUser("userid") } returns null
        }
        test {
            val exc = assertFailsWith<LinkException> {
                ban("userid", null, "big boy", "the big desc")
            }
            assertEquals("User 'userid' does not exist", exc.message)
        }
    }

    @Test
    fun `Test ban on existing user`() {
        val idHash = byteArrayOf(1, 2, 3, 4)
        val u = mockk<LinkUser> {
            every { msftIdHash } returns idHash
        }
        val ban = mockk<LinkBan>()
        mockHere<LinkServerDatabase> {
            coEvery { getUser("userid") } returns u
        }
        val df = mockHere<LinkDatabaseFacade> {
            coEvery { recordBan(idHash, null, "the_author", "the description") } returns ban
        }
        val rm = mockHere<LinkRoleManager> {
            coEvery { invalidateAllRoles("userid") } returns mockk()
        }
        test {
            val realBan = ban("userid", null, "the_author", "the description")
            assertSame(ban, realBan)
        }
        coVerify {
            df.recordBan(idHash, null, "the_author", "the description")
            rm.invalidateAllRoles("userid")
        }
    }

    @Test
    fun `Test revoke ban that does not exist`() {
        mockHere<LinkDatabaseFacade> {
            coEvery { getBan(any()) } returns null
        }
        test {
            val exc = assertFailsWith<LinkEndpointException> {
                revokeBan("blabla", 12345)
            }
            assertEquals(StandardErrorCodes.InvalidId, exc.errorCode)
            assertTrue(exc.isEndUserAtFault)
        }
    }

    @Test
    fun `Test revoke ban that does not correspond to msft id hash`() {
        mockHere<LinkDatabaseFacade> {
            coEvery { getBan(12) } returns mockk {
                every { msftIdHash } returns byteArrayOf(1, 2, 3)
            }
        }
        mockHere<LinkBanLogic> {
            every { isBanActive(any()) } returns true
        }
        test {
            val exc = assertFailsWith<LinkEndpointException> {
                revokeBan("nope", 12)
            }
            assertEquals(StandardErrorCodes.InvalidId, exc.errorCode)
            assertTrue(exc.isEndUserAtFault)
            assertTrue(exc.message!!.contains("hash"))
        }
    }

    @Test
    fun `Test revoke ban that does correspond to msft id hash`() {
        val idHash = byteArrayOf(1, 2, 3, 4)
        val idHashStr = Base64.getUrlEncoder().encodeToString(idHash)
        val initialBan = mockk<LinkBan> {
            every { msftIdHash } returns idHash
        }
        mockHere<LinkBanLogic> {
            every { isBanActive(initialBan) } returns true
        }
        val dbf = mockHere<LinkDatabaseFacade> {
            coEvery { getBan(12) } returns initialBan
            coEvery { revokeBan(12) } just runs
            coEvery { getUserFromMsftIdHash(any()) } returns mockk {
                every { discordId } returns "discordid"
            }
        }
        val rm = mockHere<LinkRoleManager> {
            coEvery { invalidateAllRoles("discordid") } returns mockk()
        }
        test {
            revokeBan(idHashStr, 12)
        }
        coVerify {
            dbf.revokeBan(12)
            rm.invalidateAllRoles("discordid")
        }
    }

    private fun test(body: suspend LinkBanManager.() -> Unit) {
        runBlocking { body(get()) }
    }
}