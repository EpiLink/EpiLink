package org.epilink.bot

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.epilink.bot.db.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime
import kotlin.test.*

class DatabaseTest : KoinTest {
    @BeforeTest
    fun setupKoin() {
        startKoin {
            modules(module {
                single<LinkServerDatabase> { spyk(LinkServerDatabaseImpl()) }
            })
        }
    }

    @AfterTest
    fun tearDownKoin() {
        stopKoin()
    }

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
            val adv = isMicrosoftUserAllowedToCreateAccount("hey")
            assertTrue(adv is Disallowed, "Creation should be disallowed")
            assertTrue(adv.reason.contains("already"), "Reason should contain word already")
        }
    }

    @Test
    fun `Test indefinitely banned Microsoft user cannot create account`() {
        val hey = "hey".sha256()
        mockHere<LinkDatabaseFacade> {
            coEvery { isMicrosoftAccountAlreadyLinked(hey) } returns false
            coEvery { getBansFor(hey) } returns listOf(mockk {
                every { expiresOn } returns null
            })
        }
        test {
            val adv = isMicrosoftUserAllowedToCreateAccount("hey")
            assertTrue(adv is Disallowed, "Creation should be disallowed")
            assertTrue(adv.reason.contains("banned"), "Reason should contain word banned")
        }
    }

    @Test
    fun `Test temporarily banned Microsoft user cannot create account`() {
        val hey = "hey".sha256()
        mockHere<LinkDatabaseFacade> {
            coEvery { isMicrosoftAccountAlreadyLinked(hey) } returns false
            coEvery { getBansFor(hey) } returns listOf(mockk {
                every { expiresOn } returns LocalDateTime.now().plusDays(1)
            })
        }
        test {
            val adv = isMicrosoftUserAllowedToCreateAccount("hey")
            assertTrue(adv is Disallowed, "Creation should be disallowed")
            assertTrue(adv.reason.contains("banned"), "Reason should contain word banned")
        }
    }

    @Test
    fun `Test temporarily banned Microsoft user with ban expired can create account`() {
        val hey = "hey".sha256()
        mockHere<LinkDatabaseFacade> {
            coEvery { isMicrosoftAccountAlreadyLinked(hey) } returns false
            coEvery { getBansFor(hey) } returns listOf(mockk {
                every { expiresOn } returns LocalDateTime.now().minusSeconds(1)
            })
        }
        test {
            val adv = isMicrosoftUserAllowedToCreateAccount("hey")
            assertTrue(adv is Allowed)
        }
    }

    @Test
    fun `Successful account creation`() {
        val hash = "tested".sha256()
        val fac = mockHere<LinkDatabaseFacade> {
            coEvery { recordNewUser("discordid", hash, "eeemail", true, any()) } returns mockk()
        }
        test {
            coEvery { isMicrosoftUserAllowedToCreateAccount("tested") } returns Allowed
            coEvery { isDiscordUserAllowedToCreateAccount("discordid") } returns Allowed
            createUser("discordid", "tested", "eeemail", true)
            coVerify { isMicrosoftUserAllowedToCreateAccount("tested") }
            coVerify { isDiscordUserAllowedToCreateAccount("discordid") }
        }
        coVerify { fac.recordNewUser("discordid", hash, "eeemail", true, any()) }
    }

    @Test
    fun `Unsuccessful account creation on msft issue`() {
        test {
            coEvery { isMicrosoftUserAllowedToCreateAccount("tested") } returns Disallowed("Hello")
            coEvery { isDiscordUserAllowedToCreateAccount("discordid") } returns Allowed
            val exc = assertFailsWith<LinkEndpointException> {
                createUser("discordid", "tested", "eeemail", true)
            }
            coVerify { isMicrosoftUserAllowedToCreateAccount("tested") }
            assertEquals(StandardErrorCodes.AccountCreationNotAllowed, exc.errorCode)
            assertTrue(exc.isEndUserAtFault, "End user is expected to be at fault")
            assertTrue(exc.message!!.contains("Hello"))
        }
    }

    @Test
    fun `Unsuccessful account creation on Discord issue`() {
        test {
            coEvery { isMicrosoftUserAllowedToCreateAccount("tested") } returns Allowed
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


    private fun <R> test(block: suspend LinkServerDatabase.() -> R) =
        runBlocking {
            block(get())
        }
}

private fun String.sha256(): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(this.toByteArray(StandardCharsets.UTF_8))
}