/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.identity

import guru.zoroark.tegral.di.dsl.put
import guru.zoroark.tegral.di.test.TegralSubjectTest
import guru.zoroark.tegral.di.test.mockk.putMock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.epilink.bot.EpiLinkException
import org.epilink.bot.UserEndpointException
import org.epilink.bot.config.IdentityProviderConfiguration
import org.epilink.bot.config.PrivacyConfiguration
import org.epilink.bot.db.DatabaseFacade
import org.epilink.bot.db.IdentityManager
import org.epilink.bot.db.IdentityManagerImpl
import org.epilink.bot.db.UnlinkCooldown
import org.epilink.bot.db.User
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.discord.DiscordEmbed
import org.epilink.bot.discord.DiscordMessageSender
import org.epilink.bot.discord.DiscordMessages
import org.epilink.bot.http.data.IdAccess
import org.epilink.bot.sha256
import org.epilink.bot.web.declareNoOpI18n
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdAccessorTest : TegralSubjectTest<IdentityManager>(
    IdentityManager::class,
    { put<IdentityManager>(::IdentityManagerImpl) }
) {
    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test automated id access success`() = test {
        val u = mockk<User> { every { discordId } returns "targetid" }
        val dbf = putMock<DatabaseFacade> {
            coEvery { isUserIdentifiable(u) } returns true
            coEvery { getUserEmailWithAccessLog(u, false, "authorrr", "reasonnn") } returns "identity"
        }
        val embed = mockk<DiscordEmbed>()
        val dms = putMock<DiscordMessageSender> {
            every { sendDirectMessageLater("targetid", embed) } returns mockk()
        }
        declareNoOpI18n()
        val dm = putMock<DiscordMessages> {
            every { getIdentityAccessEmbed(any(), false, "authorrr", "reasonnn") } returns embed
        }
        val cd = putMock<UnlinkCooldown> { coEvery { refreshCooldown("targetid") } just runs }

        val id = subject.accessIdentity(u, false, "authorrr", "reasonnn")
        assertEquals("identity", id)
        coVerify {
            dm.getIdentityAccessEmbed(any(), false, "authorrr", "reasonnn")
            dms.sendDirectMessageLater("targetid", embed)
            dbf.getUserEmailWithAccessLog(u, false, "authorrr", "reasonnn")
            cd.refreshCooldown("targetid")
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test automated id access user is not identifiable`() = test {
        val u = mockk<User>()
        putMock<DatabaseFacade> {
            coEvery { isUserIdentifiable(u) } returns false
        }

        val exc = assertFailsWith<EpiLinkException> {
            subject.accessIdentity(u, true, "authorrr", "reasonnn")
        }
        assertEquals("User is not identifiable", exc.message)
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test relink fails on user already identifiable`() = test {
        val user = mockk<User>()
        putMock<DatabaseFacade> {
            coEvery { isUserIdentifiable(user) } returns true
        }

        val exc = assertFailsWith<UserEndpointException> {
            subject.relinkIdentity(user, "this doesn't matter", "this doesn't matter either")
        }
        assertEquals(110, exc.errorCode.code)
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test relink fails on ID mismatch`() = test {
        val originalHash = "That doesn't look right".sha256()
        val u = mockk<User> {
            every { idpIdHash } returns originalHash
        }
        putMock<DatabaseFacade> {
            coEvery { isUserIdentifiable(u) } returns false
        }
        putMock<IdentityProviderConfiguration> {
            coEvery { microsoftBackwardsCompatibility } returns false
        }

        val exc = assertFailsWith<UserEndpointException> {
            subject.relinkIdentity(u, "this doesn't matter", "That is definitely not okay")
        }
        assertEquals(112, exc.errorCode.code)
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test relink success`() = test {
        val id = "This looks quite alright"
        val hash = id.sha256()
        val u = mockk<User> {
            every { discordId } returns "targetId"
            every { idpIdHash } returns hash
        }
        val df = putMock<DatabaseFacade> {
            coEvery { isUserIdentifiable(u) } returns false
            coEvery { recordNewIdentity(u, "mynewemail@email.com") } just runs
        }
        val rcd = putMock<UnlinkCooldown> {
            coEvery { refreshCooldown("targetId") } just runs
        }

        subject.relinkIdentity(u, "mynewemail@email.com", id)
        coVerify {
            df.recordNewIdentity(u, "mynewemail@email.com")
            rcd.refreshCooldown("targetId")
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test identity removal with no identity in the first place`() = test {
        val u = mockk<User>()
        putMock<DatabaseFacade> {
            coEvery { isUserIdentifiable(u) } returns false
        }

        val exc = assertFailsWith<UserEndpointException> {
            subject.deleteUserIdentity(u)
        }
        assertEquals(111, exc.errorCode.code)
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test identity removal on cooldown`() = test {
        val u = mockk<User> { every { discordId } returns "targetId" }
        putMock<DatabaseFacade> {
            coEvery { isUserIdentifiable(u) } returns true
        }
        putMock<UnlinkCooldown> {
            coEvery { canUnlink("targetId") } returns false
        }

        val exc = assertFailsWith<UserEndpointException> {
            subject.deleteUserIdentity(u)
        }
        assertEquals(113, exc.errorCode.code)
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test identity removal success`() = test {
        val u = mockk<User> {
            coEvery { discordId } returns "targetId"
        }
        val df = putMock<DatabaseFacade> {
            coEvery { isUserIdentifiable(u) } returns true
            coEvery { eraseIdentity(u) } just runs
        }
        putMock<UnlinkCooldown> {
            coEvery { canUnlink("targetId") } returns true
        }

        subject.deleteUserIdentity(u)
        coVerify { df.eraseIdentity(u) }
    }

    @Test
    fun `Test export access logs`() = test {
        val inst = Instant.now() - Duration.ofHours(5)
        val inst2 = Instant.now() - Duration.ofDays(10)
        val u = mockk<User> { every { discordId } returns "userid" }
        putMock<DatabaseFacade> {
            coEvery { getIdentityAccessesFor(u) } returns listOf(
                mockk {
                    every { authorName } returns "The Admin Of Things"
                    every { automated } returns false
                    every { reason } returns "The reason"
                    every { timestamp } returns inst
                },
                mockk {
                    every { authorName } returns "EpiLink Bot"
                    every { automated } returns true
                    every { reason } returns "Another reason"
                    every { timestamp } returns inst2
                }
            )
        }

        // Test with human identity disclosed
        putMock<PrivacyConfiguration> {
            every { shouldDiscloseIdentity(any()) } returns true
        }

        val al = subject.getIdAccessLogs(u)
        assertTrue(al.manualAuthorsDisclosed)
        assertEquals(2, al.accesses.size)
        assertTrue(IdAccess(false, "The Admin Of Things", "The reason", inst.toString()) in al.accesses)
        assertTrue(IdAccess(true, "EpiLink Bot", "Another reason", inst2.toString()) in al.accesses)
    }

    @Test
    fun `Test export access logs with concealed human identity requester`() = test {
        val inst = Instant.now() - Duration.ofHours(5)
        val inst2 = Instant.now() - Duration.ofDays(10)
        val u = mockk<User> { every { discordId } returns "discordId" }
        putMock<DatabaseFacade> {
            coEvery { getIdentityAccessesFor(u) } returns listOf(
                mockk {
                    every { authorName } returns "The Admin Of Things"
                    every { automated } returns false
                    every { reason } returns "The reason"
                    every { timestamp } returns inst
                },
                mockk {
                    every { authorName } returns "EpiLink Bot"
                    every { automated } returns true
                    every { reason } returns "Another reason"
                    every { timestamp } returns inst2
                }
            )
        }

        // Test without human identity disclosed
        putMock<PrivacyConfiguration> {
            every { shouldDiscloseIdentity(false) } returns false
            every { shouldDiscloseIdentity(true) } returns true
        }

        val al = subject.getIdAccessLogs(u)
        assertFalse(al.manualAuthorsDisclosed)
        assertEquals(2, al.accesses.size)
        assertTrue(IdAccess(false, null, "The reason", inst.toString()) in al.accesses)
        assertTrue(IdAccess(true, "EpiLink Bot", "Another reason", inst2.toString()) in al.accesses)
    }
}
