package org.epilink.bot

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.epilink.bot.db.*
import org.epilink.bot.rulebook.Rulebook
import org.koin.core.get
import org.koin.dsl.module
import kotlin.test.*

class PermissionChecksTest : KoinBaseTest(
    module {
        single<LinkPermissionChecks> { LinkPermissionChecksImpl() }
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
    fun `Test indefinitely banned user cannot join servers`() {
        val hey = "tested".sha256()
        mockHere<LinkDatabaseFacade> {
            coEvery { getBansFor(hey) } returns listOf(mockk { every { reason } returns "HELLO THERE" })
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
            assertTrue(adv.reason.contains("HELLO THERE"), "Expected ban reason to be present")
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

    private fun <R> test(block: suspend LinkPermissionChecks.() -> R) =
        runBlocking {
            block(get())
        }
}