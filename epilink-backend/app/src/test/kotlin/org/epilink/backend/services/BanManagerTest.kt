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
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.epilink.backend.KoinBaseTest
import org.epilink.backend.common.UserEndpointException
import org.epilink.backend.config.DiscordEmbed
import org.epilink.backend.db.Ban
import org.epilink.backend.db.DatabaseFacade
import org.epilink.backend.db.User
import org.epilink.backend.mockHere
import org.epilink.backend.models.StandardErrorCodes
import org.epilink.backend.http.declareNoOpI18n
import org.koin.dsl.module

class BanManagerTest : KoinBaseTest<BanManager>(
    BanManager::class,
    module {
        single<BanManager> { BanManagerImpl() }
    }
) {
    @Test
    fun `Test ban on existing user`() {
        val idHash = byteArrayOf(1, 2, 3, 4)
        val idHashStr = Base64.getEncoder().encodeToString(idHash)
        val u = mockk<User> {
            every { discordId } returns "targetid"
        }
        val ban = mockk<Ban>()
        val embed = mockk<DiscordEmbed>()
        val df = mockHere<DatabaseFacade> {
            coEvery { recordBan(idHash, null, "the_author", "the description") } returns ban
            coEvery { getUserFromIdpIdHash(any()) } returns u
        }
        val rm = mockHere<RoleManager> {
            coEvery { invalidateAllRolesLater("targetid") } returns mockk()
        }
        val cd = mockHere<UnlinkCooldown> {
            coEvery { refreshCooldown("targetid") } just runs
        }
        declareNoOpI18n()
        mockHere<DiscordMessages> {
            every { getBanNotification(any(), "the description", null) } returns embed
        }
        val dms = mockHere<DiscordMessageSender> {
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
        mockHere<DatabaseFacade> {
            coEvery { getBan(any()) } returns null
        }
        test {
            val exc = assertFailsWith<UserEndpointException> {
                revokeBan("blabla", 12345)
            }
            assertEquals(StandardErrorCodes.InvalidId, exc.errorCode)
        }
    }

    @Test
    fun `Test revoke ban that does not correspond to msft id hash`() {
        mockHere<DatabaseFacade> {
            coEvery { getBan(12) } returns mockk {
                every { idpIdHash } returns byteArrayOf(1, 2, 3)
            }
        }
        mockHere<BanLogic> {
            every { isBanActive(any()) } returns true
        }
        test {
            val exc = assertFailsWith<UserEndpointException> {
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
        val initialBan = mockk<Ban> {
            every { idpIdHash } returns idHash
        }
        mockHere<BanLogic> {
            every { isBanActive(initialBan) } returns true
        }
        val dbf = mockHere<DatabaseFacade> {
            coEvery { getBan(12) } returns initialBan
            coEvery { revokeBan(12) } just runs
            coEvery { getUserFromIdpIdHash(any()) } returns mockk {
                every { discordId } returns "discordid"
            }
        }
        val rm = mockHere<RoleManager> {
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
