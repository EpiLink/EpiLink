/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.services

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.*
import org.epilink.backend.*
import org.epilink.backend.common.UserEndpointException
import org.epilink.backend.db.*
import org.epilink.backend.models.StandardErrorCodes
import org.epilink.backend.services.PermissionChecks
import org.epilink.backend.services.UserCreator
import org.epilink.backend.services.UserCreatorImpl
import org.koin.dsl.module

class UserCreatorTest : KoinBaseTest<UserCreator>(
    UserCreator::class,
    module {
        single<UserCreator> { UserCreatorImpl() }
    }
) {

    @Test
    fun `Successful account creation`() {
        val hash = "tested".sha256()
        val fac = mockHere<DatabaseFacade> {
            coEvery { recordNewUser("discordid", hash, "eeemail", true, any()) } returns mockk()
        }
        val pc = mockHere<PermissionChecks> {
            coEvery { isIdentityProviderUserAllowedToCreateAccount("tested", "eeemail") } returns DatabaseAdvisory.Allowed
            coEvery { isDiscordUserAllowedToCreateAccount("discordid") } returns DatabaseAdvisory.Allowed
        }
        test {
            createUser("discordid", "tested", "eeemail", true)
        }
        coVerify {
            fac.recordNewUser("discordid", hash, "eeemail", true, any())
            pc.isIdentityProviderUserAllowedToCreateAccount("tested", "eeemail")
            pc.isDiscordUserAllowedToCreateAccount("discordid")
        }
    }

    @Test
    fun `Unsuccessful account creation on msft issue`() {
        val pc = mockHere<PermissionChecks> {
            coEvery { isIdentityProviderUserAllowedToCreateAccount("tested", "eeemail") } returns DatabaseAdvisory.Disallowed("Hello", "good.bye")
            coEvery { isDiscordUserAllowedToCreateAccount("discordid") } returns DatabaseAdvisory.Allowed
        }
        test {
            val exc = assertFailsWith<UserEndpointException> {
                createUser("discordid", "tested", "eeemail", true)
            }
            assertEquals(StandardErrorCodes.AccountCreationNotAllowed, exc.errorCode)
            assertEquals("Hello", exc.details)
            assertEquals("good.bye", exc.detailsI18n)
        }
        coVerify { pc.isIdentityProviderUserAllowedToCreateAccount("tested", "eeemail") }
    }

    @Test
    fun `Unsuccessful account creation on Discord issue`() {
        val pc = mockHere<PermissionChecks> {
            coEvery { isIdentityProviderUserAllowedToCreateAccount("tested", "eeemail") } returns DatabaseAdvisory.Allowed
            coEvery { isDiscordUserAllowedToCreateAccount("discordid") } returns DatabaseAdvisory.Disallowed("Hiii", "he.y")
        }
        test {
            val exc = assertFailsWith<UserEndpointException> {
                createUser("discordid", "tested", "eeemail", true)
            }
            assertEquals(StandardErrorCodes.AccountCreationNotAllowed, exc.errorCode)
            assertEquals("Hiii", exc.details)
            assertEquals("he.y", exc.detailsI18n)
        }
        coVerify { pc.isDiscordUserAllowedToCreateAccount("discordid") }
    }
}
