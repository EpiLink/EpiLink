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
import org.epilink.bot.config.LinkPrivacy
import org.epilink.bot.db.*
import org.epilink.bot.discord.DiscordEmbed
import org.epilink.bot.discord.LinkDiscordMessageSender
import org.epilink.bot.discord.LinkDiscordMessages
import org.epilink.bot.http.data.IdAccess
import org.koin.core.get
import org.koin.dsl.module
import java.time.Duration
import java.time.Instant
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
        val dbf = mockHere<LinkDatabaseFacade> {
            coEvery { getUser("targetid") } returns u
            coEvery { isUserIdentifiable("targetid") } returns true
            coEvery { getUserEmailWithAccessLog("targetid", false, "authorrr", "reasonnn") } returns "identity"
        }
        val embed = mockk<DiscordEmbed>()
        val dms = mockHere<LinkDiscordMessageSender> {
            every { sendDirectMessageLater("targetid", embed) } returns mockk()
        }
        val dm = mockHere<LinkDiscordMessages> {
            every { getIdentityAccessEmbed(false, "authorrr", "reasonnn") } returns embed
        }
        test {
            val id = accessIdentity("targetid", false, "authorrr", "reasonnn")
            assertEquals("identity", id)
        }
        coVerify {
            dm.getIdentityAccessEmbed(false, "authorrr", "reasonnn")
            dms.sendDirectMessageLater("targetid", embed)
            dbf.getUserEmailWithAccessLog("targetid", false, "authorrr", "reasonnn")
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test automated id access user does not exist`() {
        mockHere<LinkDatabaseFacade> {
            coEvery { getUser("targetid") } returns null
        }
        test {
            val exc = assertFailsWith<LinkException> {
                accessIdentity("targetid", true, "authorrr", "reasonnn")
            }
            assertEquals("User does not exist", exc.message)
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test automated id access user is not identifiable`() {
        mockHere<LinkDatabaseFacade> {
            coEvery { getUser("targetid") } returns mockk()
            coEvery { isUserIdentifiable("targetid") } returns false
        }
        test {
            val exc = assertFailsWith<LinkException> {
                accessIdentity("targetid", true, "authorrr", "reasonnn")
            }
            assertEquals("User is not identifiable", exc.message)
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test relink fails on user already identifiable`() {
        mockHere<LinkDatabaseFacade> {
            coEvery { isUserIdentifiable("userid") } returns true
        }
        test {
            val exc = assertFailsWith<LinkEndpointException> {
                relinkMicrosoftIdentity("userid", "this doesn't matter", "this doesn't matter either")
            }
            assertEquals(110, exc.errorCode.code)
            assertTrue(exc.isEndUserAtFault)
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test relink fails on ID mismatch`() {
        val originalHash = "That doesn't look right".sha256()
        mockHere<LinkDatabaseFacade> {
            coEvery { isUserIdentifiable("userid") } returns false
            coEvery { getUser("userid") } returns mockk {
                every { msftIdHash } returns originalHash
            }
        }
        test {
            val exc = assertFailsWith<LinkEndpointException> {
                relinkMicrosoftIdentity("userid", "this doesn't matter", "That is definitely not okay")
            }
            assertEquals(112, exc.errorCode.code)
            assertTrue(exc.isEndUserAtFault)
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test relink success`() {
        val id = "This looks quite alright"
        val hash = id.sha256()
        val df = mockHere<LinkDatabaseFacade> {
            coEvery { isUserIdentifiable("userid") } returns false
            coEvery { getUser("userid") } returns mockk {
                every { msftIdHash } returns hash
            }
            coEvery { recordNewIdentity("userid", "mynewemail@email.com") } just runs
        }
        test {
            relinkMicrosoftIdentity("userid", "mynewemail@email.com", id)
        }
        coVerify { df.recordNewIdentity("userid", "mynewemail@email.com") }
    }


    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test identity removal with no identity in the first place`() {
        mockHere<LinkDatabaseFacade> {
            coEvery { isUserIdentifiable("userid") } returns false
        }
        test {
            val exc = assertFailsWith<LinkEndpointException> {
                deleteUserIdentity("userid")
            }
            assertEquals(111, exc.errorCode.code)
            assertTrue(exc.isEndUserAtFault)
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test identity removal success`() {
        val df = mockHere<LinkDatabaseFacade> {
            coEvery { isUserIdentifiable("userid") } returns true
            coEvery { eraseIdentity("userid") } just runs
        }
        test {
            deleteUserIdentity("userid")
        }
        coVerify { df.eraseIdentity("userid") }
    }

    @Test
    fun `Test export access logs`() {
        val inst = Instant.now() - Duration.ofHours(5)
        val inst2 = Instant.now() - Duration.ofDays(10)
        mockHere<LinkDatabaseFacade> {
            coEvery { getIdentityAccessesFor("userid") } returns listOf(
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
        mockHere<LinkPrivacy> {
            every { shouldDiscloseIdentity(any()) } returns true
        }
        test {
            val al = getIdAccessLogs("userid")
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
        mockHere<LinkDatabaseFacade> {
            coEvery { getIdentityAccessesFor("userid") } returns listOf(
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
        mockHere<LinkPrivacy> {
            every { shouldDiscloseIdentity(false) } returns false
            every { shouldDiscloseIdentity(true) } returns true
        }
        test {
            val al = getIdAccessLogs("userid")
            assertFalse(al.manualAuthorsDisclosed)
            assertEquals(2, al.accesses.size)
            assertTrue(IdAccess(false, null, "The reason", inst.toString()) in al.accesses)
            assertTrue(IdAccess(true, "EpiLink Bot", "Another reason", inst2.toString()) in al.accesses)
        }
    }

    private fun <R> test(block: suspend LinkIdAccessor.() -> R) =
        runBlocking {
            block(get())
        }
}