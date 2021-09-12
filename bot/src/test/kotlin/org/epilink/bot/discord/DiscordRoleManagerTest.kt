/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import guru.zoroark.shedinja.dsl.put
import guru.zoroark.shedinja.environment.get
import guru.zoroark.shedinja.test.ShedinjaBaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyAll
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.epilink.bot.CacheClient
import org.epilink.bot.MemoryCacheClient
import org.epilink.bot.config.DiscordConfiguration
import org.epilink.bot.config.DiscordServerSpec
import org.epilink.bot.db.Allowed
import org.epilink.bot.db.DatabaseFacade
import org.epilink.bot.db.Disallowed
import org.epilink.bot.db.IdentityManager
import org.epilink.bot.db.PermissionChecks
import org.epilink.bot.db.User
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.putMock
import org.epilink.bot.rulebook.Rule
import org.epilink.bot.rulebook.Rulebook
import org.epilink.bot.rulebook.StrongIdentityRule
import org.epilink.bot.rulebook.WeakIdentityRule
import org.epilink.bot.stest
import org.epilink.bot.web.declareNoOpI18n
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscordRoleManagerTest : ShedinjaBaseTest<RoleManager>(
    RoleManager::class, {
        put<RoleManager>(::RoleManagerImpl)
    }
) {
    @Test
    fun `Test role update for disallowed user`() = stest {
        val user: User = mockk()
        val sd = putMock<DatabaseFacade> {
            coEvery { getUser("userid") } returns user
        }
        val pc = putMock<PermissionChecks> {
            coEvery { canUserJoinServers(user) } returns Disallowed("This is my reason", "my.reason")
        }
        val dcf = putMock<DiscordClientFacade> {
            coEvery { sendDirectMessage("userid", any()) } just runs
            coEvery { getGuildName("Hello") } returns "There"
        }
        declareNoOpI18n()
        val dm = putMock<DiscordMessages> {
            every { getCouldNotJoinEmbed(any(), "There", "This is my reason") } returns mockk()
        }
        val result = subject.getRolesForUser("userid", mockk(), true, listOf("Hello"))
        assertEquals(setOf(), result.first)
        assertFalse(result.second, "Expected sticky roles to not be applied")
        coVerifyAll {
            pc.canUserJoinServers(any())
            dcf.sendDirectMessage("userid", any())
            dcf.getGuildName("Hello")
            sd.getUser("userid")
            dm.getCouldNotJoinEmbed(any(), "There", "This is my reason")
        }
        confirmVerified(sd, dcf, dm)
    }

    @Test
    fun `Test on join, unmonitored guild`() = stest {
        val cfg = putMock<DiscordConfiguration> {
            every { servers } returns listOf(mockk { every { id } returns "otherid" })
        }
        subject.handleNewUser("guildid", "My Awesome Guild", "userid")
        coVerifyAll { cfg.servers }
        confirmVerified(cfg)
    }

    @Test
    fun `Test on join, unknown user, send greeting`() = stest {
        val cfg = putMock<DiscordConfiguration> {
            every { servers } returns listOf(
                DiscordServerSpec(
                    id = "guildid",
                    enableWelcomeMessage = true,
                    roles = mockk(),
                    stickyRoles = listOf()
                )
            )
        }
        val mockem = mockk<DiscordEmbed>()
        val db = putMock<DatabaseFacade> {
            coEvery { getUser("jacques") } returns null
        }
        declareNoOpI18n()
        val dm = putMock<DiscordMessages> {
            every { getGreetingsEmbed(any(), "guildid", "My Awesome Guild") } returns mockem
        }
        val dcf = putMock<DiscordClientFacade> {
            coEvery { sendDirectMessage("jacques", mockem) } just runs
        }
        subject.handleNewUser("guildid", "My Awesome Guild", "jacques")
        coVerifyAll {
            cfg.servers
            dm.getGreetingsEmbed(any(), "guildid", "My Awesome Guild")
            dcf.sendDirectMessage("jacques", mockem)
            db.getUser("jacques")
        }
        confirmVerified(cfg, dm, dcf, db)
    }

    @Test
    fun `Test on join, unknown user, no greeting`() = stest {
        val cfg = putMock<DiscordConfiguration> {
            every { servers } returns listOf(
                DiscordServerSpec(
                    id = "guildid",
                    enableWelcomeMessage = false,
                    roles = mockk(),
                    stickyRoles = listOf()
                )
            )
        }
        val db = putMock<DatabaseFacade> {
            coEvery { getUser("jacques") } returns null
        }
        declareNoOpI18n()
        val dm = putMock<DiscordMessages> {
            every { getGreetingsEmbed(any(), "guildid", "My Awesome Guild") } returns null
        }
        subject.handleNewUser("guildid", "My Awesome Guild", "jacques")
        coVerifyAll {
            cfg.servers
            dm.getGreetingsEmbed(any(), "guildid", "My Awesome Guild")
            db.getUser("jacques")
        }
        confirmVerified(cfg, dm, db)
    }

    @Test
    fun `Test on join, known`() = stest {
        val cfg = putMock<DiscordConfiguration> {
            every { servers } returns listOf(
                DiscordServerSpec(
                    id = "guildid",
                    enableWelcomeMessage = false,
                    roles = mockk(),
                    stickyRoles = listOf()
                )
            )
        }
        val usermock: User = mockk()
        val db = putMock<DatabaseFacade> {
            coEvery { getUser("jacques") } returns usermock
        }
        put { spyk(RoleManagerImpl(scope)) }
        val rm = get<RoleManagerImpl>()

        coEvery { rm.updateRolesOnGuilds("jacques", listOf("guildid"), true) } just runs
        rm.handleNewUser("guildid", "My Awesome Guild", "jacques")

        coVerifyAll {
            cfg.servers
            db.getUser("jacques")
            rm.updateRolesOnGuilds("jacques", listOf("guildid"), true)
            rm.handleNewUser("guildid", "My Awesome Guild", "jacques")
        }
        confirmVerified(cfg, rm, db)
    }

    @Test
    fun `Test rules relevant for guilds`() = stest {
        val weakRule: WeakIdentityRule = mockk()
        val strongRule: StrongIdentityRule = mockk()
        val otherRule: Rule = mockk()
        val fourthRule: Rule = mockk()
        putMock<Rulebook> {
            every { rules } returns mapOf(
                "WeakRule" to weakRule,
                "StrongRule" to strongRule,
                "OtherRule" to otherRule,
                "FourthRule" to fourthRule
            )
        }
        putMock<DiscordConfiguration> {
            every { servers } returns listOf(
                // Typical server with a mix of standard and rule-based roles
                mockk {
                    every { id } returns "guildid1"
                    every { roles } returns mapOf(
                        "_known" to "g1rk",
                        "weakrole" to "g1rw",
                        "strongrole" to "g1rs"
                    )
                    every { requires } returns listOf("WeakRule", "StrongRule")
                },
                // Purely standard roles guild
                mockk {
                    every { id } returns "guildid2"
                    every { roles } returns mapOf(
                        "_known" to "g2rk",
                        "_identified" to "g2ri"
                    )
                    every { requires } returns listOf()
                },
                // Purely rule-based roles guild
                mockk {
                    every { id } returns "guildid3"
                    every { roles } returns mapOf(
                        "weakrole" to "g3rw",
                        "otherrole" to "g3ro"
                    )
                    every { requires } returns listOf("WeakRule", "OtherRule")
                },
                // Guild that should be ignored
                mockk {
                    every { id } returns "guildid4"
                    every { requires } returns listOf()
                }
            )
        }
        val rm = get<RoleManager>()
        val rules = rm.getRulesRelevantForGuilds(listOf("guildid1", "guildid2", "guildid3"))
        println(rules)
        assertEquals(3, rules.size)
        assertTrue(
            rules.contains(
                RuleWithRequestingGuilds(weakRule, setOf("guildid1", "guildid3"))
            ),
            "Did not contain weak rule (or weak rule entry is incorrect)"
        )
        assertTrue(
            rules.contains(
                RuleWithRequestingGuilds(strongRule, setOf("guildid1"))
            ),
            "Did not contain strong rule (or strong rule entry is incorrect)"
        )
        assertTrue(
            rules.contains(
                RuleWithRequestingGuilds(otherRule, setOf("guildid3"))
            )
        )

    }

    @Test
    fun `Test get roles for unknown user`() = stest {
        putMock<DatabaseFacade> {
            coEvery { getUser("userid") } returns null
        }
        val rm = get<RoleManager>()
        val roles = rm.getRolesForUser("userid", mockk(), false, listOf())
        assertEquals(setOf(), roles.first, "roles should be empty")
        assertTrue(roles.second, "Sticky roles should be considered")
    }

    @Test
    fun `Test get roles for disallowed user, do not tell`() = stest {
        val user: User = mockk()
        putMock<DatabaseFacade> {
            coEvery { getUser("userid") } returns user
        }
        putMock<PermissionChecks> {
            coEvery { canUserJoinServers(user) } returns Disallowed("This is my reason", "yes.maybe")
        }
        val rm = get<RoleManager>()
        val roles = rm.getRolesForUser("userid", mockk(), false, listOf())
        assertEquals(setOf(), roles.first, "roles should be empty")
        assertFalse(roles.second, "Sticky roles should be ignored")
    }

    @Test
    fun `Test get roles for disallowed user, notify`() = stest {
        val user: User = mockk()
        putMock<DatabaseFacade> {
            coEvery { getUser("userid") } returns user
        }
        putMock<PermissionChecks> {
            coEvery { canUserJoinServers(user) } returns Disallowed("This is my reason", "yes.allo")
        }
        val embed: DiscordEmbed = mockk()
        val dcf = putMock<DiscordClientFacade> {
            coEvery { sendDirectMessage("userid", embed) } just runs
            coEvery { getGuildName("HELLO THERE") } returns "GENERAL KENOBI"
        }
        declareNoOpI18n()
        putMock<DiscordMessages> {
            every { getCouldNotJoinEmbed(any(), "GENERAL KENOBI", "This is my reason") } returns embed
        }
        val rm = get<RoleManager>()
        val roles = rm.getRolesForUser("userid", mockk(), true, listOf("HELLO THERE"))
        assertEquals(setOf(), roles.first, "roles should be empty")
        assertFalse(roles.second, "Sticky roles should be ignored")
        coVerify { dcf.sendDirectMessage("userid", embed) }
    }

    @Test
    fun `Test get roles, no rules, not identifiable`() = stest {
        val user: User = mockk()
        @OptIn(UsesTrueIdentity::class)
        putMock<DatabaseFacade> {
            coEvery { getUser("userid") } returns user
            coEvery { isUserIdentifiable(user) } returns false
        }
        putMock<PermissionChecks> {
            coEvery { canUserJoinServers(user) } returns Allowed
        }
        val rm = get<RoleManager>()
        val roles = rm.getRolesForUser("userid", listOf(), true, listOf())
        assertEquals(setOf("_known", "_notIdentified"), roles.first)
        assertTrue(roles.second)
    }

    @Test
    fun `Test get roles, no rules, identifiable`() = stest {
        val user: User = mockk()
        @OptIn(UsesTrueIdentity::class)
        putMock<DatabaseFacade> {
            coEvery { getUser("userid") } returns user
            coEvery { isUserIdentifiable(user) } returns true
        }
        putMock<PermissionChecks> {
            coEvery { canUserJoinServers(user) } returns Allowed
        }
        val rm = get<RoleManager>()
        val roles = rm.getRolesForUser("userid", listOf(), true, listOf())
        assertEquals(setOf("_known", "_identified"), roles.first)
        assertTrue(roles.second)
    }

    @Test
    @OptIn(UsesTrueIdentity::class)
    fun `Test get roles, with rules, identifiable`() = stest {
        val user: User = mockk { every { discordId } returns "userid" }
        val lia = putMock<IdentityManager> {
            coEvery { accessIdentity(user, any(), any(), any()) } returns "email@@"
        }
        putMock<DatabaseFacade> {
            coEvery { getUser("userid") } returns user
            coEvery { isUserIdentifiable(user) } returns true
        }
        putMock<PermissionChecks> {
            coEvery { canUserJoinServers(user) } returns Allowed
        }
        val dui = DiscordUserInfo("userid", "uname", "disc")
        putMock<DiscordClientFacade> {
            coEvery { getDiscordUserInfo("userid") } returns dui.copy()
            coEvery { getGuildName(any()) } returns "NAME"
        }
        val rule1 = StrongIdentityRule("StrongRule", null) {
            assertEquals("email@@", it)
            assertEquals("userid", userDiscordId)
            assertEquals("uname", userDiscordName)
            assertEquals("disc", userDiscordDiscriminator)
            roles += "sir_$it"
            roles += "sirId_$userDiscordId"
        }
        val rule2 = WeakIdentityRule("WeakRule", null) {
            assertEquals("userid", userDiscordId)
            assertEquals("uname", userDiscordName)
            assertEquals("disc", userDiscordDiscriminator)
            roles += "wirId_$userDiscordId"
            roles += "wirDisc_$userDiscordDiscriminator"
        }
        put<CacheClient> { MemoryCacheClient() }
        val rm = get<RoleManager>()
        val roles = rm.getRolesForUser(
            "userid",
            listOf(
                RuleWithRequestingGuilds(rule1, setOf("Guildo", "Abcde")),
                RuleWithRequestingGuilds(rule2, setOf("Otherrr"))
            ),
            true,
            listOf()
        )
        assertEquals(
            setOf("_known", "_identified", "sir_email@@", "sirId_userid", "wirId_userid", "wirDisc_disc"),
            roles.first
        )
        assertTrue(roles.second)
        coVerify { lia.accessIdentity(user, any(), any(), any()) }
    }

    @Test
    fun `Test get roles, with rules, not identifiable`() = stest {
        val user: User = mockk { every { discordId } returns "userid" }
        @OptIn(UsesTrueIdentity::class)
        putMock<DatabaseFacade> {
            coEvery { getUser("userid") } returns user
            coEvery { isUserIdentifiable(user) } returns false
        }
        putMock<PermissionChecks> {
            coEvery { canUserJoinServers(user) } returns Allowed
        }
        val dui = DiscordUserInfo("userid", "uname", "disc")
        putMock<DiscordClientFacade> {
            coEvery { getDiscordUserInfo("userid") } returns dui.copy()
        }
        val rule1 = StrongIdentityRule("StrongRule", null, mockk())
        val rule2 = WeakIdentityRule("WeakRule", null) {
            assertEquals("userid", userDiscordId)
            assertEquals("uname", userDiscordName)
            assertEquals("disc", userDiscordDiscriminator)
            roles += "wirId_$userDiscordId"
            roles += "wirDisc_$userDiscordDiscriminator"
        }
        put<CacheClient> { MemoryCacheClient() }
        val rm = get<RoleManager>()
        val roles = rm.getRolesForUser(
            "userid",
            listOf(
                RuleWithRequestingGuilds(rule1, setOf("Guildo", "Abcde")),
                RuleWithRequestingGuilds(rule2, setOf("Other"))
            ),
            true,
            listOf()
        )
        assertEquals(setOf("_known", "_notIdentified", "wirId_userid", "wirDisc_disc"), roles.first)
        assertTrue(roles.second)
    }

    @Test
    fun `Test update user with roles applying sticky roles`() =
        updateUserWithRolesTest(true)

    @Test
    fun `Test update user with roles ignoring sticky roles`() =
        updateUserWithRolesTest(false)

    private fun updateUserWithRolesTest(applySticky: Boolean) = stest {
        val dcf = putMock<DiscordClientFacade> {
            coEvery { manageRoles(any(), any(), any(), any()) } just runs
        }
        putMock<DiscordConfiguration> {
            every { servers } returns listOf(
                mockk {
                    every { id } returns "guildid"
                    every { roles } returns mapOf(
                        "eladd" to "addMe",
                        "eladd2" to "addMeToo",
                        "elrem" to "removeMe",
                        "elrem2" to "removeMeToo",
                        "sr1" to "maybeRemoveMe",
                        "sr2" to "maybeRemoveMeAlso"
                    )
                    every { stickyRoles } returns listOf("sr1")
                }
            )
            every { stickyRoles } returns listOf("sr2")
        }
        val rm = get<RoleManager>()
        runBlocking {
            rm.updateUserWithRoles("userid", "guildid", setOf("eladd", "eladd2", "elneut", "elneut2"), applySticky)
        }
        coVerify {
            dcf.manageRoles(
                "userid", "guildid", setOf("addMe", "addMeToo"),
                if (applySticky) setOf("removeMe", "removeMeToo")
                else setOf("removeMe", "removeMeToo", "maybeRemoveMe", "maybeRemoveMeAlso")
            )
        }
        confirmVerified(dcf)
    }
}
