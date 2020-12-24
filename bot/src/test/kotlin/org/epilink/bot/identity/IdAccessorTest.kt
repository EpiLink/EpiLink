/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.identity

import io.mockk.*
import org.epilink.bot.*
import org.epilink.bot.DatabaseFeatures.eraseIdentity
import org.epilink.bot.DatabaseFeatures.getIdentityAccesses
import org.epilink.bot.DatabaseFeatures.getUserEmailWithAccessLog
import org.epilink.bot.DatabaseFeatures.isUserIdentifiable
import org.epilink.bot.DatabaseFeatures.recordNewIdentity
import org.epilink.bot.web.declareNoOpI18n
import org.epilink.bot.config.LinkIdProviderConfiguration
import org.epilink.bot.config.LinkPrivacy
import org.epilink.bot.db.*
import org.epilink.bot.discord.DiscordEmbed
import org.epilink.bot.discord.LinkDiscordMessageSender
import org.epilink.bot.discord.LinkDiscordMessages
import org.epilink.bot.http.data.IdAccess
import org.koin.dsl.module
import java.time.Duration
import java.time.Instant
import kotlin.test.*

class IdAccessorTest : KoinBaseTest<LinkIdManager>(
    LinkIdManager::class,
    module {
        single<LinkIdManager> { LinkIdManagerImpl() }
    }
) {
    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test automated id access success`() {
        val u = mockUser(id = "targetid")
        val dbf = mockDatabase(
            isUserIdentifiable(u, true),
            getUserEmailWithAccessLog(u, false, "authorrr", "reasonnn", "identity")
        )
        val embed = mockk<DiscordEmbed>()
        val dms = mockHere<LinkDiscordMessageSender> {
            every { sendDirectMessageLater("targetid", embed) } returns mockk()
        }
        declareNoOpI18n()
        val dm = mockHere<LinkDiscordMessages> {
            every { getIdentityAccessEmbed(any(), false, "authorrr", "reasonnn") } returns embed
        }
        val cd = mockHere<LinkUnlinkCooldown> { coEvery { refreshCooldown("targetid") } just runs }
        test {
            val id = accessIdentity(u, false, "authorrr", "reasonnn")
            assertEquals("identity", id)
        }
        coVerify {
            dm.getIdentityAccessEmbed(any(), false, "authorrr", "reasonnn")
            dms.sendDirectMessageLater("targetid", embed)
            dbf.getUserEmailWithAccessLog(u, false, "authorrr", "reasonnn")
            cd.refreshCooldown("targetid")
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test automated id access user is not identifiable`() {
        val u = mockUser()
        mockDatabase(isUserIdentifiable(u, false))
        test {
            val exc = assertFailsWith<LinkException> {
                accessIdentity(u, true, "authorrr", "reasonnn")
            }
            assertEquals("User is not identifiable", exc.message)
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test relink fails on user already identifiable`() {
        val user = mockUser()
        mockDatabase(isUserIdentifiable(user, true))
        test {
            val exc = assertFailsWith<LinkEndpointUserException> {
                relinkIdentity(user, "this doesn't matter", "this doesn't matter either")
            }
            assertEquals(110, exc.errorCode.code)
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test relink fails on ID mismatch`() {
        val originalHash = "That doesn't look right".sha256()
        val u = mockUser(idpIdHash = originalHash)
        mockDatabase(isUserIdentifiable(u, false))
        mockHere<LinkIdProviderConfiguration> {
            coEvery { microsoftBackwardsCompatibility } returns false
        }
        test {
            val exc = assertFailsWith<LinkEndpointUserException> {
                relinkIdentity(u, "this doesn't matter", "That is definitely not okay")
            }
            assertEquals(112, exc.errorCode.code)
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test relink success`() {
        val id = "This looks quite alright"
        val hash = id.sha256()
        val u = mockUser("targetId", hash)
        val df = mockDatabase(isUserIdentifiable(u, false), recordNewIdentity(u, "mynewemail@email.com"))
        val rcd = mockHere<LinkUnlinkCooldown> {
            coEvery { refreshCooldown("targetId") } just runs
        }
        test {
            relinkIdentity(u, "mynewemail@email.com", id)
        }
        coVerify {
            df.recordNewIdentity(u, "mynewemail@email.com")
            rcd.refreshCooldown("targetId")
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test identity removal with no identity in the first place`() {
        val u = mockUser()
        mockDatabase(isUserIdentifiable(u, false))
        test {
            val exc = assertFailsWith<LinkEndpointUserException> {
                deleteUserIdentity(u)
            }
            assertEquals(111, exc.errorCode.code)
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test identity removal on cooldown`() {
        val u = mockUser(id = "targetId")
        mockDatabase(isUserIdentifiable(u, true))
        mockHere<LinkUnlinkCooldown> {
            coEvery { canUnlink("targetId") } returns false
        }
        test {
            val exc = assertFailsWith<LinkEndpointUserException> {
                deleteUserIdentity(u)
            }
            assertEquals(113, exc.errorCode.code)
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test identity removal success`() {
        val u = mockUser("targetId")
        val df = mockDatabase(isUserIdentifiable(u, true), eraseIdentity(u))
        mockHere<LinkUnlinkCooldown> {
            coEvery { canUnlink("targetId") } returns true
        }
        test {
            deleteUserIdentity(u)
        }
        coVerify { df.eraseIdentity(u) }
    }

    @Test
    fun `Test export access logs`() {
        val inst = Instant.now() - Duration.ofHours(5)
        val inst2 = Instant.now() - Duration.ofDays(10)
        val u = mockUser("userId")
        mockDatabase(
            getIdentityAccesses(
                u, listOf(
                    BasicIdentityAccess("The Admin Of Things", false, "The reason", inst),
                    BasicIdentityAccess("EpiLink Bot", true, "Another reason", inst2)
                )
            )
        )
        // Test with human identity disclosed
        mockHere<LinkPrivacy> {
            every { shouldDiscloseIdentity(any()) } returns true
        }
        test {
            val al = getIdAccessLogs(u)
            assertTrue(al.manualAuthorsDisclosed)
            assertEquals(2, al.accesses.size)
            assertTrue(IdAccess(false, "The Admin Of Things", "The reason", inst.toString()) in al.accesses)
            assertTrue(IdAccess(true, "EpiLink Bot", "Another reason", inst2.toString()) in al.accesses)
        }
    }

    @Test
    fun `Test export access logs with concealed human identity requester`() {
        val inst = Instant.now() - Duration.ofHours(5)
        val inst2 = Instant.now() - Duration.ofDays(10)
        val u = mockUser("discordId")
        mockDatabase(
            getIdentityAccesses(
                u, listOf(
                    BasicIdentityAccess("The Admin Of Things", false, "The reason", inst),
                    BasicIdentityAccess("EpiLink Bot", true, "Another reason", inst2)
                )
            )
        )

        // Test without human identity disclosed
        mockHere<LinkPrivacy> {
            every { shouldDiscloseIdentity(false) } returns false
            every { shouldDiscloseIdentity(true) } returns true
        }
        test {
            val al = getIdAccessLogs(u)
            assertFalse(al.manualAuthorsDisclosed)
            assertEquals(2, al.accesses.size)
            assertTrue(IdAccess(false, null, "The reason", inst.toString()) in al.accesses)
            assertTrue(IdAccess(true, "EpiLink Bot", "Another reason", inst2.toString()) in al.accesses)
        }
    }
}