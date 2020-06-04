package org.epilink.bot

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.epilink.bot.db.*
import org.koin.core.get
import org.koin.dsl.module
import kotlin.test.*

class UserCreatorTest : KoinBaseTest(
    module {
        single<LinkUserCreator> { LinkUserCreatorImpl() }
    }
) {

    @Test
    fun `Successful account creation`() {
        val hash = "tested".sha256()
        val fac = mockHere<LinkDatabaseFacade> {
            coEvery { recordNewUser("discordid", hash, "eeemail", true, any()) } returns mockk()
        }
        val pc = mockHere<LinkPermissionChecks> {
            coEvery { isMicrosoftUserAllowedToCreateAccount("tested", "eeemail") } returns Allowed
            coEvery { isDiscordUserAllowedToCreateAccount("discordid") } returns Allowed
        }
        test {
            createUser("discordid", "tested", "eeemail", true)
        }
        coVerify {
            fac.recordNewUser("discordid", hash, "eeemail", true, any())
            pc.isMicrosoftUserAllowedToCreateAccount("tested", "eeemail")
            pc.isDiscordUserAllowedToCreateAccount("discordid")
        }
    }

    @Test
    fun `Unsuccessful account creation on msft issue`() {
        val pc = mockHere<LinkPermissionChecks> {
            coEvery { isMicrosoftUserAllowedToCreateAccount("tested", "eeemail") } returns Disallowed("Hello")
            coEvery { isDiscordUserAllowedToCreateAccount("discordid") } returns Allowed
        }
        test {
            val exc = assertFailsWith<LinkEndpointException> {
                createUser("discordid", "tested", "eeemail", true)
            }
            assertEquals(StandardErrorCodes.AccountCreationNotAllowed, exc.errorCode)
            assertTrue(exc.isEndUserAtFault, "End user is expected to be at fault")
            assertTrue(exc.message!!.contains("Hello"))
        }
        coVerify { pc.isMicrosoftUserAllowedToCreateAccount("tested", "eeemail") }
    }


    @Test
    fun `Unsuccessful account creation on Discord issue`() {
        val pc = mockHere<LinkPermissionChecks> {
            coEvery { isMicrosoftUserAllowedToCreateAccount("tested", "eeemail") } returns Allowed
            coEvery { isDiscordUserAllowedToCreateAccount("discordid") } returns Disallowed("Hiii")
        }
        test {
            val exc = assertFailsWith<LinkEndpointException> {
                createUser("discordid", "tested", "eeemail", true)
            }
            assertEquals(StandardErrorCodes.AccountCreationNotAllowed, exc.errorCode)
            assertTrue(exc.isEndUserAtFault, "End use is expected to be at fault")
            assertTrue(exc.message!!.contains("Hiii"), "Expected failure reason in message (message = ${exc.message})")
        }
        coVerify { pc.isDiscordUserAllowedToCreateAccount("discordid") }
    }

    private fun <R> test(block: suspend LinkUserCreator.() -> R) =
        runBlocking {
            block(get())
        }
}