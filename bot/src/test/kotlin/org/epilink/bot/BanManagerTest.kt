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
import org.epilink.bot.db.LinkBan
import org.epilink.bot.db.LinkDatabaseFacade
import org.epilink.bot.db.LinkServerDatabase
import org.epilink.bot.db.LinkUser
import org.epilink.bot.discord.LinkBanManager
import org.epilink.bot.discord.LinkBanManagerImpl
import org.epilink.bot.discord.LinkRoleManager
import org.koin.core.get
import org.koin.dsl.module
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
        runBlocking {
            val exc = assertFailsWith<LinkException> {
                get<LinkBanManager>().ban("userid", null, "big boy", "the big desc")
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
        runBlocking {
            val realBan = get<LinkBanManager>().ban("userid", null, "the_author", "the description")
            assertSame(ban, realBan)
        }
        coVerify {
            df.recordBan(idHash, null, "the_author", "the description")
            rm.invalidateAllRoles("userid")
        }
    }
}