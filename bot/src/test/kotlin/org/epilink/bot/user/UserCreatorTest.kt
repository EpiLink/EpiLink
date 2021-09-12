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
import io.mockk.mockk
import org.epilink.bot.*
import org.epilink.bot.db.*
import kotlin.test.*

class UserCreatorTest : ShedinjaBaseTest<UserCreator>(
    UserCreator::class, {
        put<UserCreator>(::UserCreatorImpl)
    }
) {

    @Test
    fun `Successful account creation`() = stest {
        val hash = "tested".sha256()
        val fac = putMock<DatabaseFacade> {
            coEvery { recordNewUser("discordid", hash, "eeemail", true, any()) } returns mockk()
        }
        val pc = putMock<PermissionChecks> {
            coEvery { isIdentityProviderUserAllowedToCreateAccount("tested", "eeemail") } returns Allowed
            coEvery { isDiscordUserAllowedToCreateAccount("discordid") } returns Allowed
        }
        subject.createUser("discordid", "tested", "eeemail", true)
        coVerify {
            fac.recordNewUser("discordid", hash, "eeemail", true, any())
            pc.isIdentityProviderUserAllowedToCreateAccount("tested", "eeemail")
            pc.isDiscordUserAllowedToCreateAccount("discordid")
        }
    }

    @Test
    fun `Unsuccessful account creation on msft issue`() = stest {
        val pc = putMock<PermissionChecks> {
            coEvery { isIdentityProviderUserAllowedToCreateAccount("tested", "eeemail") } returns Disallowed(
                "Hello",
                "good.bye"
            )
            coEvery { isDiscordUserAllowedToCreateAccount("discordid") } returns Allowed
        }
        val exc = assertFailsWith<UserEndpointException> {
            subject.createUser("discordid", "tested", "eeemail", true)
        }
        assertEquals(StandardErrorCodes.AccountCreationNotAllowed, exc.errorCode)
        assertEquals("Hello", exc.details)
        assertEquals("good.bye", exc.detailsI18n)
        coVerify { pc.isIdentityProviderUserAllowedToCreateAccount("tested", "eeemail") }
    }

    @Test
    fun `Unsuccessful account creation on Discord issue`() = stest {
        val pc = putMock<PermissionChecks> {
            coEvery { isIdentityProviderUserAllowedToCreateAccount("tested", "eeemail") } returns Allowed
            coEvery { isDiscordUserAllowedToCreateAccount("discordid") } returns Disallowed("Hiii", "he.y")
        }
        val exc = assertFailsWith<UserEndpointException> {
            subject.createUser("discordid", "tested", "eeemail", true)
        }
        assertEquals(StandardErrorCodes.AccountCreationNotAllowed, exc.errorCode)
        assertEquals("Hiii", exc.details)
        assertEquals("he.y", exc.detailsI18n)
        coVerify { pc.isDiscordUserAllowedToCreateAccount("discordid") }
    }
}
