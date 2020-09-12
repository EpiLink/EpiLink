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
import org.epilink.bot.db.*
import org.epilink.bot.discord.*
import org.koin.dsl.module
import java.util.*
import kotlin.test.*

class BanManagerTest : KoinBaseTest<LinkBanManager>(
    LinkBanManager::class,
    module {
        single<LinkBanManager> { LinkBanManagerImpl() }
    }
) {
    @Test
    fun `Test ban on existing user`() {
        val idHash = byteArrayOf(1, 2, 3, 4)
        val idHashStr = Base64.getEncoder().encodeToString(idHash)
        val u = mockk<LinkUser> {
            every { discordId } returns "targetid"
        }
        val ban = mockk<LinkBan>()
        val embed = mockk<DiscordEmbed>()
        val df = mockHere<LinkDatabaseFacade> {
            coEvery { recordBan(idHash, null, "the_author", "the description") } returns ban
            coEvery { getUserFromIdpIdHash(any()) } returns u
        }
        val rm = mockHere<LinkRoleManager> {
            coEvery { invalidateAllRolesLater("targetid") } returns mockk()
        }
        val cd = mockHere<LinkUnlinkCooldown> {
            coEvery { refreshCooldown("targetid") } just runs
        }
        declareNoOpI18n()
        mockHere<LinkDiscordMessages> {
            every { getBanNotification(any(), "the description", null) } returns embed
        }
        val dms = mockHere<LinkDiscordMessageSender> {
            every { sendDirectMessageLater("targetid", embed) } returns mockk()
        }
        test {
            val realBan = ban(idHashStr, null, "the_author", "the description")
            assertSame(ban, realBan)
        }
        coVerify {
            df.recordBan(idHash, null, "the_author", "the description")
            rm.invalidateAllRolesLater("targetid")
            dms.sendDirectMessageLater("targetid", embed)
            cd.refreshCooldown("targetid")
        }
    }

    @Test
    fun `Test revoke ban that does not exist`() {
        mockHere<LinkDatabaseFacade> {
            coEvery { getBan(any()) } returns null
        }
        test {
            val exc = assertFailsWith<LinkEndpointUserException> {
                revokeBan("blabla", 12345)
            }
            assertEquals(StandardErrorCodes.InvalidId, exc.errorCode)
        }
    }

    @Test
    fun `Test revoke ban that does not correspond to msft id hash`() {
        mockHere<LinkDatabaseFacade> {
            coEvery { getBan(12) } returns mockk {
                every { idpIdHash } returns byteArrayOf(1, 2, 3)
            }
        }
        mockHere<LinkBanLogic> {
            every { isBanActive(any()) } returns true
        }
        test {
            val exc = assertFailsWith<LinkEndpointUserException> {
                revokeBan("nope", 12)
            }
            assertEquals(StandardErrorCodes.InvalidId, exc.errorCode)
            assertTrue(exc.message!!.contains("hash"))
        }
    }

    @Test
    fun `Test revoke ban that does correspond to msft id hash`() {
        val idHash = byteArrayOf(1, 2, 3, 4)
        val idHashStr = Base64.getUrlEncoder().encodeToString(idHash)
        val initialBan = mockk<LinkBan> {
            every { idpIdHash } returns idHash
        }
        mockHere<LinkBanLogic> {
            every { isBanActive(initialBan) } returns true
        }
        val dbf = mockHere<LinkDatabaseFacade> {
            coEvery { getBan(12) } returns initialBan
            coEvery { revokeBan(12) } just runs
            coEvery { getUserFromIdpIdHash(any()) } returns mockk {
                every { discordId } returns "discordid"
            }
        }
        val rm = mockHere<LinkRoleManager> {
            coEvery { invalidateAllRolesLater("discordid") } returns mockk()
        }
        test {
            revokeBan(idHashStr, 12)
        }
        coVerify {
            dbf.revokeBan(12)
            rm.invalidateAllRolesLater("discordid")
        }
    }
}