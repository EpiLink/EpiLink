/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.user

import guru.zoroark.shedinja.dsl.put
import guru.zoroark.shedinja.test.ShedinjaBaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.epilink.bot.StandardErrorCodes
import org.epilink.bot.UserEndpointException
import org.epilink.bot.db.Ban
import org.epilink.bot.db.BanLogic
import org.epilink.bot.db.DatabaseFacade
import org.epilink.bot.db.UnlinkCooldown
import org.epilink.bot.db.User
import org.epilink.bot.discord.BanManager
import org.epilink.bot.discord.BanManagerImpl
import org.epilink.bot.discord.DiscordEmbed
import org.epilink.bot.discord.DiscordMessageSender
import org.epilink.bot.discord.DiscordMessages
import org.epilink.bot.discord.RoleManager
import org.epilink.bot.putMock
import org.epilink.bot.stest
import org.epilink.bot.web.declareNoOpI18n
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BanManagerTest : ShedinjaBaseTest<BanManager>(
    BanManager::class, {
        put<BanManager>(::BanManagerImpl)
    }
) {
    @Test
    fun `Test ban on existing user`() = stest {
        val idHash = byteArrayOf(1, 2, 3, 4)
        val idHashStr = Base64.getEncoder().encodeToString(idHash)
        val u = mockk<User> {
            every { discordId } returns "targetid"
        }
        val ban = mockk<Ban>()
        val embed = mockk<DiscordEmbed>()
        val df = putMock<DatabaseFacade> {
            coEvery { recordBan(idHash, null, "the_author", "the description") } returns ban
            coEvery { getUserFromIdpIdHash(any()) } returns u
        }
        val rm = putMock<RoleManager> {
            coEvery { invalidateAllRolesLater("targetid") } returns mockk()
        }
        val cd = putMock<UnlinkCooldown> {
            coEvery { refreshCooldown("targetid") } just runs
        }
        declareNoOpI18n()
        putMock<DiscordMessages> {
            every { getBanNotification(any(), "the description", null) } returns embed
        }
        val dms = putMock<DiscordMessageSender> {
            every { sendDirectMessageLater("targetid", embed) } returns mockk()
        }
        val realBan = subject.ban(idHashStr, null, "the_author", "the description")
        assertSame(ban, realBan)
        coVerify {
            df.recordBan(idHash, null, "the_author", "the description")
            rm.invalidateAllRolesLater("targetid")
            dms.sendDirectMessageLater("targetid", embed)
            cd.refreshCooldown("targetid")
        }
    }

    @Test
    fun `Test revoke ban that does not exist`() = stest {
        putMock<DatabaseFacade> {
            coEvery { getBan(any()) } returns null
        }
        val exc = assertFailsWith<UserEndpointException> {
            subject.revokeBan("blabla", 12345)
        }
        assertEquals(StandardErrorCodes.InvalidId, exc.errorCode)
    }

    @Test
    fun `Test revoke ban that does not correspond to msft id hash`() = stest {
        putMock<DatabaseFacade> {
            coEvery { getBan(12) } returns mockk {
                every { idpIdHash } returns byteArrayOf(1, 2, 3)
            }
        }
        putMock<BanLogic> {
            every { isBanActive(any()) } returns true
        }
        val exc = assertFailsWith<UserEndpointException> {
            subject.revokeBan("nope", 12)
        }
        assertEquals(StandardErrorCodes.InvalidId, exc.errorCode)
        assertTrue(exc.message!!.contains("hash"))
    }

    @Test
    fun `Test revoke ban that does correspond to msft id hash`() = stest {
        val idHash = byteArrayOf(1, 2, 3, 4)
        val idHashStr = Base64.getUrlEncoder().encodeToString(idHash)
        val initialBan = mockk<Ban> {
            every { idpIdHash } returns idHash
        }
        putMock<BanLogic> {
            every { isBanActive(initialBan) } returns true
        }
        val dbf = putMock<DatabaseFacade> {
            coEvery { getBan(12) } returns initialBan
            coEvery { revokeBan(12) } just runs
            coEvery { getUserFromIdpIdHash(any()) } returns mockk {
                every { discordId } returns "discordid"
            }
        }
        val rm = putMock<RoleManager> {
            coEvery { invalidateAllRolesLater("discordid") } returns mockk()
        }
        subject.revokeBan(idHashStr, 12)
        coVerify {
            dbf.revokeBan(12)
            rm.invalidateAllRolesLater("discordid")
        }
    }
}
