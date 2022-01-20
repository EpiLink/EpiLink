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
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.apache.commons.codec.binary.Hex
import org.epilink.backend.KoinBaseTest
import org.epilink.backend.db.DatabaseAdvisory
import org.epilink.backend.db.DatabaseFacade
import org.epilink.backend.db.User
import org.epilink.backend.db.UsesTrueIdentity
import org.epilink.backend.mockHere
import org.epilink.backend.rulebook.Rulebook
import org.epilink.backend.sha256
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.mock.declare

class PermissionChecksTest : KoinBaseTest<PermissionChecks>(
    PermissionChecks::class,
    module {
        single<PermissionChecks> { PermissionChecksImpl() }
    }
) {
    @Test
    fun `Test Discord user who already exists cannot create account`() {
        mockHere<DatabaseFacade> {
            coEvery { doesUserExist("discordid") } returns true
        }
        test {
            val adv = isDiscordUserAllowedToCreateAccount("discordid")
            assertTrue(adv is DatabaseAdvisory.Disallowed, "Creation should be disallowed")
            assertTrue(
                adv.reason.contains("already exists"),
                "Disallowed message should contain the phrase already exists"
            )
        }
    }

    @Test
    fun `Test Discord user who does not exist should be able to create account`() {
        mockHere<DatabaseFacade> {
            coEvery { doesUserExist("discordid") } returns false
        }
        test {
            val adv = isDiscordUserAllowedToCreateAccount("discordid")
            assertEquals(DatabaseAdvisory.Allowed, adv)
        }
    }

    @Test
    fun `Test Microsoft user who already exists cannot create account`() {
        val hey = "hey".sha256()
        mockHere<DatabaseFacade> {
            coEvery { isIdentityAccountAlreadyLinked(hey) } returns true
        }
        test {
            val adv = isIdentityProviderUserAllowedToCreateAccount("hey", "emailemail")
            assertTrue(adv is DatabaseAdvisory.Disallowed, "Creation should be disallowed")
            assertTrue(adv.reason.contains("already"), "Reason should contain word already")
            assertEquals("pc.ala", adv.reasonI18n)
            assertEquals(
                mapOf("idpIdHashHex" to Hex.encodeHexString(hey).let { it.substring(0, it.length / 2) }),
                adv.reasonI18nData
            )
        }
    }

    @Test
    fun `Test banned Microsoft user cannot create account`() {
        val hey = "hey".sha256()
        mockHere<DatabaseFacade> {
            coEvery { isIdentityAccountAlreadyLinked(hey) } returns false
            coEvery { getBansFor(hey) } returns listOf(mockk { every { reason } returns "badboi" })
        }
        mockHere<BanLogic> {
            every { isBanActive(any()) } returns true
        }
        mockHere<Rulebook> {
            every { validator } returns { true }
        }
        test {
            val adv = isIdentityProviderUserAllowedToCreateAccount("hey", "mailmail")
            assertTrue(adv is DatabaseAdvisory.Disallowed, "Creation should be disallowed")
            assertTrue(adv.reason.contains("banned"), "Reason should contain word banned")
            assertTrue(adv.reason.contains("badboi"), "Reason should contain ban reason")
        }
    }

    @Test
    fun `Test Microsoft user with no validator can create account`() {
        val hey = "hey".sha256()
        mockHere<DatabaseFacade> {
            coEvery { isIdentityAccountAlreadyLinked(hey) } returns false
            coEvery { getBansFor(hey) } returns listOf()
        }
        mockHere<Rulebook> {
            every { validator } returns null
        }
        test {
            val adv = isIdentityProviderUserAllowedToCreateAccount("hey", "mailmail")
            assertTrue(adv is DatabaseAdvisory.Allowed)
        }
    }

    @Test
    fun `Test Microsoft user with email rejected cannot create account`() {
        val hey = "hey".sha256()
        mockHere<DatabaseFacade> {
            coEvery { isIdentityAccountAlreadyLinked(hey) } returns false
        }
        mockHere<Rulebook> {
            every { validator } returns { it != "mailmail" }
        }
        test {
            val adv = isIdentityProviderUserAllowedToCreateAccount("hey", "mailmail")
            assertTrue(adv is DatabaseAdvisory.Disallowed, "Creation should be disallowed")
            assertTrue(adv.reason.contains("e-mail", ignoreCase = true), "Reason should contain word e-mail")
        }
    }

    @Test
    fun `Test indefinitely banned user cannot join servers`() {
        val hey = "tested".sha256()
        mockHere<DatabaseFacade> {
            coEvery { getBansFor(hey) } returns listOf(mockk { every { reason } returns "HELLO THERE" })
        }
        mockHere<BanLogic> {
            every { isBanActive(any()) } returns true
        }
        test {
            val adv = canUserJoinServers(
                mockk {
                    every { idpIdHash } returns hey
                    every { discordId } returns "banneduid"
                }
            )
            assertTrue(adv is DatabaseAdvisory.Disallowed, "Expected disallowed")
            assertTrue(adv.reason.contains("HELLO THERE"), "Expected ban reason to be present")
        }
    }

    @Test
    fun `Test normal user can join servers`() {
        val hey = "tested".sha256()
        mockHere<DatabaseFacade> {
            coEvery { getBansFor(hey) } returns listOf()
        }
        test {
            val adv = canUserJoinServers(mockk { every { idpIdHash } returns hey })
            assertEquals(DatabaseAdvisory.Allowed, adv, "Expected allowed")
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test admin not an admin check fails`() {
        val user = mockk<User> { every { discordId } returns "discordIdNotAdmin" }
        declare(named("admins")) { listOf("notme", "notyou") }
        test {
            val result = canPerformAdminActions(user)
            assertEquals(AdminStatus.NotAdmin, result)
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test admin not identifiable check fails`() {
        val user = mockk<User> { every { discordId } returns "notyou" }
        declare(named("admins")) { listOf("notme", "notyou") }
        mockHere<DatabaseFacade> {
            coEvery { isUserIdentifiable(user) } returns false
        }
        test {
            val result = canPerformAdminActions(user)
            assertEquals(AdminStatus.AdminNotIdentifiable, result)
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test admin check success`() {
        val user = mockk<User> { every { discordId } returns "notyou" }
        declare(named("admins")) { listOf("notme", "notyou") }
        mockHere<DatabaseFacade> {
            coEvery { isUserIdentifiable(user) } returns true
        }
        test {
            val result = canPerformAdminActions(user)
            assertEquals(AdminStatus.Admin, result)
        }
    }
}
