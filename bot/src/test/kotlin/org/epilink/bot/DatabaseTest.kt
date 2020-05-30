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
import org.epilink.bot.rulebook.Rulebook
import org.epilink.bot.db.*
import org.epilink.bot.http.data.IdAccess
import org.koin.dsl.module
import org.koin.test.get
import java.time.Duration
import java.time.Instant
import kotlin.test.*

class DatabaseTest : KoinBaseTest(
    module {
        single<LinkServerDatabase> { spyk(LinkServerDatabaseImpl()) }
    }
) {
    @Test
    fun `Test Discord user who already exists cannot create account`() {
        mockHere<LinkDatabaseFacade> {
            coEvery { doesUserExist("discordid") } returns true
        }
        test {
            val adv = isDiscordUserAllowedToCreateAccount("discordid")
            assertTrue(adv is Disallowed, "Creation should be disallowed")
            assertTrue(
                adv.reason.contains("already exists"),
                "Disallowed message should contain the phrase already exists"
            )
        }
    }

    @Test
    fun `Test Discord user who does not exist should be able to create account`() {
        mockHere<LinkDatabaseFacade> {
            coEvery { doesUserExist("discordid") } returns false
        }
        test {
            val adv = isDiscordUserAllowedToCreateAccount("discordid")
            assertEquals(Allowed, adv)
        }
    }

    @Test
    fun `Test Microsoft user who already exists cannot create account`() {
        val hey = "hey".sha256()
        mockHere<LinkDatabaseFacade> {
            coEvery { isMicrosoftAccountAlreadyLinked(hey) } returns true
        }
        test {
            val adv = isMicrosoftUserAllowedToCreateAccount("hey", "emailemail")
            assertTrue(adv is Disallowed, "Creation should be disallowed")
            assertTrue(adv.reason.contains("already"), "Reason should contain word already")
        }
    }

    @Test
    fun `Test banned Microsoft user cannot create account`() {
        val hey = "hey".sha256()
        mockHere<LinkDatabaseFacade> {
            coEvery { isMicrosoftAccountAlreadyLinked(hey) } returns false
            coEvery { getBansFor(hey) } returns listOf(mockk())
        }
        mockHere<LinkBanLogic> {
            every { isBanActive(any()) } returns true
        }
        mockHere<Rulebook> {
            every { validator } returns { true }
        }
        test {
            val adv = isMicrosoftUserAllowedToCreateAccount("hey", "mailmail")
            assertTrue(adv is Disallowed, "Creation should be disallowed")
            assertTrue(adv.reason.contains("banned"), "Reason should contain word banned")
        }
    }

    @Test
    fun `Test Microsoft user with no validator can create account`() {
        val hey = "hey".sha256()
        mockHere<LinkDatabaseFacade> {
            coEvery { isMicrosoftAccountAlreadyLinked(hey) } returns false
            coEvery { getBansFor(hey) } returns listOf()
        }
        mockHere<Rulebook> {
            every { validator } returns null
        }
        test {
            val adv = isMicrosoftUserAllowedToCreateAccount("hey", "mailmail")
            assertTrue(adv is Allowed)
        }
    }

    @Test
    fun `Test Microsoft user with email rejected cannot create account`() {
        val hey = "hey".sha256()
        mockHere<LinkDatabaseFacade> {
            coEvery { isMicrosoftAccountAlreadyLinked(hey) } returns false
        }
        mockHere<Rulebook> {
            every { validator } returns { it != "mailmail" }
        }
        test {
            val adv = isMicrosoftUserAllowedToCreateAccount("hey", "mailmail")
            assertTrue(adv is Disallowed, "Creation should be disallowed")
            assertTrue(adv.reason.contains("e-mail", ignoreCase = true), "Reason should contain word e-mail")
        }
    }

    @Test
    fun `Successful account creation`() {
        val hash = "tested".sha256()
        val fac = mockHere<LinkDatabaseFacade> {
            coEvery { recordNewUser("discordid", hash, "eeemail", true, any()) } returns mockk()
        }
        test {
            coEvery { isMicrosoftUserAllowedToCreateAccount("tested", "eeemail") } returns Allowed
            coEvery { isDiscordUserAllowedToCreateAccount("discordid") } returns Allowed
            createUser("discordid", "tested", "eeemail", true)
            coVerify { isMicrosoftUserAllowedToCreateAccount("tested", "eeemail") }
            coVerify { isDiscordUserAllowedToCreateAccount("discordid") }
        }
        coVerify { fac.recordNewUser("discordid", hash, "eeemail", true, any()) }
    }

    @Test
    fun `Unsuccessful account creation on msft issue`() {
        test {
            coEvery { isMicrosoftUserAllowedToCreateAccount("tested", "eeemail") } returns Disallowed("Hello")
            coEvery { isDiscordUserAllowedToCreateAccount("discordid") } returns Allowed
            val exc = assertFailsWith<LinkEndpointException> {
                createUser("discordid", "tested", "eeemail", true)
            }
            coVerify { isMicrosoftUserAllowedToCreateAccount("tested", "eeemail") }
            assertEquals(StandardErrorCodes.AccountCreationNotAllowed, exc.errorCode)
            assertTrue(exc.isEndUserAtFault, "End user is expected to be at fault")
            assertTrue(exc.message!!.contains("Hello"))
        }
    }

    @Test
    fun `Unsuccessful account creation on Discord issue`() {
        test {
            coEvery { isMicrosoftUserAllowedToCreateAccount("tested", "eeemail") } returns Allowed
            coEvery { isDiscordUserAllowedToCreateAccount("discordid") } returns Disallowed("Hiii")
            val exc = assertFailsWith<LinkEndpointException> {
                createUser("discordid", "tested", "eeemail", true)
            }
            coVerify { isDiscordUserAllowedToCreateAccount("discordid") }
            assertEquals(StandardErrorCodes.AccountCreationNotAllowed, exc.errorCode)
            assertTrue(exc.isEndUserAtFault, "End use is expected to be at fault")
            assertTrue(exc.message!!.contains("Hiii"))
        }
    }

    @Test
    fun `Test indefinitely banned user cannot join servers`() {
        val hey = "tested".sha256()
        mockHere<LinkDatabaseFacade> {
            coEvery { getBansFor(hey) } returns listOf(mockk())
        }
        mockHere<LinkBanLogic> {
            every { isBanActive(any()) } returns true
        }
        test {
            val adv = canUserJoinServers(mockk {
                every { msftIdHash } returns hey
                every { discordId } returns "banneduid"
            })
            assertTrue(adv is Disallowed, "Expected disallowed")
        }
    }

    @Test
    fun `Test normal user can join servers`() {
        val hey = "tested".sha256()
        mockHere<LinkDatabaseFacade> {
            coEvery { getBansFor(hey) } returns listOf()
        }
        test {
            val adv = canUserJoinServers(mockk { every { msftIdHash } returns hey })
            assertEquals(Allowed, adv, "Expected allowed")
        }
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

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test relink fails on user already identifiable`() {
        mockHere<LinkDatabaseFacade> {
            coEvery { isUserIdentifiable("userid") } returns true
        }
        val sd = get<LinkServerDatabase>()
        runBlocking {
            val exc = assertFailsWith<LinkEndpointException> {
                sd.relinkMicrosoftIdentity("userid", "this doesn't matter", "this doesn't matter either")
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

    private fun <R> test(block: suspend LinkServerDatabase.() -> R) =
        runBlocking {
            block(get())
        }
}
