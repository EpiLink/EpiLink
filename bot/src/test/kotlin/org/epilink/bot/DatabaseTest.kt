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
import kotlin.test.*

class DatabaseTest : KoinTest {
    @BeforeTest
    fun setupKoin() {
        startKoin {
            modules(module {
                single<LinkServerDatabase> { LinkServerDatabaseImpl() }
            })
        }
    }

    @AfterTest
    fun tearDownKoin() {
        stopKoin()
    }

    @Test
    fun `Test Discord user who already exists cannot create account (advisory)`() {
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
    fun `Test Discord user who already exists cannot create account (creation failure)`() {
        mockHere<LinkDatabaseFacade> {
            coEvery { doesUserExist("discordid") } returns true
            coEvery { getBansFor(any()) } returns listOf()
            coEvery { isMicrosoftAccountAlreadyLinked(any()) } returns false
        }
        test {
            val exc = assertFailsWith<LinkEndpointException> {
                createUser("discordid", "", "", true)
            }
            assertEquals(StandardErrorCodes.AccountCreationNotAllowed, exc.errorCode)
            assertTrue(exc.isEndUserAtFault, "End user should be considered to be at fault")
            assertTrue(exc.message!!.contains("already exists"))
        }
    }

    @Test
    fun `Test Discord user who does not exist should be able to create account (advisory)`() {
        mockHere<LinkDatabaseFacade> {
            coEvery { doesUserExist("discordid") } returns false
        }
        test {
            val adv = isDiscordUserAllowedToCreateAccount("discordid")
            assertEquals(Allowed, adv)
        }
    }

    @Test
    fun `Successful account creation`() {
        val hash = "tested".sha256()
        val fac = mockHere<LinkDatabaseFacade> {
            coEvery { doesUserExist("discordid") } returns false
            coEvery { getBansFor(hash) } returns listOf()
            coEvery { isMicrosoftAccountAlreadyLinked(hash) } returns false
            coEvery { recordNewUser("discordid", hash, "eeemail", true, any()) } returns mockk()
        }
        test {
            createUser("discordid", "tested", "eeemail", true)
        }
        coVerify { fac.recordNewUser("discordid", hash, "eeemail", true, any()) }
    }

    private fun <R> test(block: suspend LinkServerDatabase.() -> R) =
        runBlocking {
            block(get())
        }
}

private fun String.sha256(): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(this.toByteArray(StandardCharsets.UTF_8))
}
