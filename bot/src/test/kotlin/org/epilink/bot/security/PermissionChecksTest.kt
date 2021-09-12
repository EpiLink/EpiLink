/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.security

import guru.zoroark.shedinja.dsl.put
import guru.zoroark.shedinja.environment.named
import guru.zoroark.shedinja.test.ShedinjaBaseTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.apache.commons.codec.binary.Hex
import org.epilink.bot.db.AdminStatus
import org.epilink.bot.db.Allowed
import org.epilink.bot.db.BanLogic
import org.epilink.bot.db.DatabaseFacade
import org.epilink.bot.db.Disallowed
import org.epilink.bot.db.PermissionChecks
import org.epilink.bot.db.PermissionChecksImpl
import org.epilink.bot.db.User
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.putMock
import org.epilink.bot.rulebook.Rulebook
import org.epilink.bot.sha256
import org.epilink.bot.stest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PermissionChecksTest : ShedinjaBaseTest<PermissionChecks>(
    PermissionChecks::class, {
        put<PermissionChecks>(::PermissionChecksImpl)
    }
) {
    @Test
    fun `Test Discord user who already exists cannot create account`() = stest {
        putMock<DatabaseFacade> {
            coEvery { doesUserExist("discordid") } returns true
        }
        val adv = subject.isDiscordUserAllowedToCreateAccount("discordid")
        assertTrue(adv is Disallowed, "Creation should be disallowed")
        assertTrue(
            adv.reason.contains("already exists"),
            "Disallowed message should contain the phrase already exists"
        )
    }

    @Test
    fun `Test Discord user who does not exist should be able to create account`() = stest {
        putMock<DatabaseFacade> {
            coEvery { doesUserExist("discordid") } returns false
        }
        val adv = subject.isDiscordUserAllowedToCreateAccount("discordid")
        assertEquals(Allowed, adv)
    }

    @Test
    fun `Test Microsoft user who already exists cannot create account`() = stest {
        val hey = "hey".sha256()
        putMock<DatabaseFacade> {
            coEvery { isIdentityAccountAlreadyLinked(hey) } returns true
        }
        val adv = subject.isIdentityProviderUserAllowedToCreateAccount("hey", "emailemail")
        assertTrue(adv is Disallowed, "Creation should be disallowed")
        assertTrue(adv.reason.contains("already"), "Reason should contain word already")
        assertEquals("pc.ala", adv.reasonI18n)
        assertEquals(
            mapOf("idpIdHashHex" to Hex.encodeHexString(hey).let { it.substring(0, it.length / 2) }),
            adv.reasonI18nData
        )
    }

    @Test
    fun `Test banned Microsoft user cannot create account`() = stest {
        val hey = "hey".sha256()
        putMock<DatabaseFacade> {
            coEvery { isIdentityAccountAlreadyLinked(hey) } returns false
            coEvery { getBansFor(hey) } returns listOf(mockk { every { reason } returns "badboi" })
        }
        putMock<BanLogic> {
            every { isBanActive(any()) } returns true
        }
        putMock<Rulebook> {
            every { validator } returns { true }
        }
        val adv = subject.isIdentityProviderUserAllowedToCreateAccount("hey", "mailmail")
        assertTrue(adv is Disallowed, "Creation should be disallowed")
        assertTrue(adv.reason.contains("banned"), "Reason should contain word banned")
        assertTrue(adv.reason.contains("badboi"), "Reason should contain ban reason")
    }

    @Test
    fun `Test Microsoft user with no validator can create account`() = stest {
        val hey = "hey".sha256()
        putMock<DatabaseFacade> {
            coEvery { isIdentityAccountAlreadyLinked(hey) } returns false
            coEvery { getBansFor(hey) } returns listOf()
        }
        putMock<Rulebook> {
            every { validator } returns null
        }
        val adv = subject.isIdentityProviderUserAllowedToCreateAccount("hey", "mailmail")
        assertTrue(adv is Allowed)
    }

    @Test
    fun `Test Microsoft user with email rejected cannot create account`() = stest {
        val hey = "hey".sha256()
        putMock<DatabaseFacade> {
            coEvery { isIdentityAccountAlreadyLinked(hey) } returns false
        }
        putMock<Rulebook> {
            every { validator } returns { it != "mailmail" }
        }
        val adv = subject.isIdentityProviderUserAllowedToCreateAccount("hey", "mailmail")
        assertTrue(adv is Disallowed, "Creation should be disallowed")
        assertTrue(adv.reason.contains("e-mail", ignoreCase = true), "Reason should contain word e-mail")
    }

    @Test
    fun `Test indefinitely banned user cannot join servers`() = stest {
        val hey = "tested".sha256()
        putMock<DatabaseFacade> {
            coEvery { getBansFor(hey) } returns listOf(mockk { every { reason } returns "HELLO THERE" })
        }
        putMock<BanLogic> {
            every { isBanActive(any()) } returns true
        }
        val adv = subject.canUserJoinServers(
            mockk {
                every { idpIdHash } returns hey
                every { discordId } returns "banneduid"
            }
        )
        assertTrue(adv is Disallowed, "Expected disallowed")
        assertTrue(adv.reason.contains("HELLO THERE"), "Expected ban reason to be present")
    }

    @Test
    fun `Test normal user can join servers`() = stest {
        val hey = "tested".sha256()
        putMock<DatabaseFacade> {
            coEvery { getBansFor(hey) } returns listOf()
        }
        val adv = subject.canUserJoinServers(mockk { every { idpIdHash } returns hey })
        assertEquals(Allowed, adv, "Expected allowed")
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test admin not an admin check fails`() = stest {
        val user = mockk<User> { every { discordId } returns "discordIdNotAdmin" }
        put(named("admins")) { listOf("notme", "notyou") }
        val result = subject.canPerformAdminActions(user)
        assertEquals(AdminStatus.NotAdmin, result)
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test admin not identifiable check fails`() = stest {
        val user = mockk<User> { every { discordId } returns "notyou" }
        put(named("admins")) { listOf("notme", "notyou") }
        putMock<DatabaseFacade> {
            coEvery { isUserIdentifiable(user) } returns false
        }
        val result = subject.canPerformAdminActions(user)
        assertEquals(AdminStatus.AdminNotIdentifiable, result)
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test admin check success`() = stest {
        val user = mockk<User> { every { discordId } returns "notyou" }
        put(named("admins")) { listOf("notme", "notyou") }
        putMock<DatabaseFacade> {
            coEvery { isUserIdentifiable(user) } returns true
        }
        val result = subject.canPerformAdminActions(user)
        assertEquals(AdminStatus.Admin, result)
    }
}
