/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
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
            coEvery { isMicrosoftUserAllowedToCreateAccount("tested", "eeemail") } returns Disallowed("Hello", "good.bye")
            coEvery { isDiscordUserAllowedToCreateAccount("discordid") } returns Allowed
        }
        test {
            val exc = assertFailsWith<LinkEndpointUserException> {
                createUser("discordid", "tested", "eeemail", true)
            }
            assertEquals(StandardErrorCodes.AccountCreationNotAllowed, exc.errorCode)
            assertEquals("Hello", exc.details)
            assertEquals("good.bye", exc.detailsI18n)
        }
        coVerify { pc.isMicrosoftUserAllowedToCreateAccount("tested", "eeemail") }
    }


    @Test
    fun `Unsuccessful account creation on Discord issue`() {
        val pc = mockHere<LinkPermissionChecks> {
            coEvery { isMicrosoftUserAllowedToCreateAccount("tested", "eeemail") } returns Allowed
            coEvery { isDiscordUserAllowedToCreateAccount("discordid") } returns Disallowed("Hiii", "he.y")
        }
        test {
            val exc = assertFailsWith<LinkEndpointUserException> {
                createUser("discordid", "tested", "eeemail", true)
            }
            assertEquals(StandardErrorCodes.AccountCreationNotAllowed, exc.errorCode)
            assertEquals("Hiii", exc.details)
            assertEquals("he.y", exc.detailsI18n)
        }
        coVerify { pc.isDiscordUserAllowedToCreateAccount("discordid") }
    }

    private fun <R> test(block: suspend LinkUserCreator.() -> R) =
        runBlocking {
            block(get())
        }
}