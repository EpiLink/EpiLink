/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.epilink.bot.CacheClient
import org.epilink.bot.KoinBaseTest
import org.epilink.bot.MemoryCacheClient
import org.epilink.bot.config.LinkDiscordConfig
import org.epilink.bot.config.LinkDiscordServerSpec
import org.epilink.bot.db.*
import org.epilink.bot.mockHere
import org.epilink.bot.rulebook.Rule
import org.epilink.bot.rulebook.Rulebook
import org.epilink.bot.rulebook.StrongIdentityRule
import org.epilink.bot.rulebook.WeakIdentityRule
import org.epilink.bot.web.declareNoOpI18n
import org.koin.dsl.module
import org.koin.test.get
import org.koin.test.mock.declare
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscordRoleManagerTest : KoinBaseTest<LinkRoleManager>(
    LinkRoleManager::class,
    module {
        single<LinkRoleManager> { LinkRoleManagerImpl() }
    }
) {
    @Test
    fun `Test role update for disallowed user`() {
        val user: LinkUser = mockk()
        val sd = mockHere<LinkDatabaseFacade> {
            coEvery { getUser("userid") } returns user
        }
        val pc = mockHere<LinkPermissionChecks> {
            coEvery { canUserJoinServers(user) } returns Disallowed("This is my reason", "my.reason")
        }
        val dcf = mockHere<LinkDiscordClientFacade> {
            coEvery { sendDirectMessage("userid", any()) } just runs
            coEvery { getGuildName("Hello") } returns "There"
        }
        declareNoOpI18n()
        val dm = mockHere<LinkDiscordMessages> {
            every { getCouldNotJoinEmbed(any(), "There", "This is my reason") } returns mockk()
        }
        test {
            val result = getRolesForUser("userid", mockk(), true, listOf("Hello"))
            assertEquals(setOf(), result.first)
            assertFalse(result.second, "Expected sticky roles to not be applied")
        }
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
    fun `Test on join, unmonitored guild`() {
        val cfg = mockDiscordConfigHere {
            server("otherid")
        }
        test { handleNewUser("guildid", "My Awesome Guild", "userid") }
        coVerifyAll { cfg.servers }
        confirmVerified(cfg)
    }

    @Test
    fun `Test on join, unknown user, send greeting`() {
        val cfg = mockDiscordConfigHere {
            server("guildid") {
                enableWelcomeMessage = true
                stickyRoles()
            }
        }
        val mockem = mockk<DiscordEmbed>()
        val db = mockHere<LinkDatabaseFacade> {
            coEvery { getUser("jacques") } returns null
        }
        declareNoOpI18n()
        val dm = mockHere<LinkDiscordMessages> {
            every { getGreetingsEmbed(any(), "guildid", "My Awesome Guild") } returns mockem
        }
        val dcf = mockHere<LinkDiscordClientFacade> {
            coEvery { sendDirectMessage("jacques", mockem) } just runs
        }
        test {
            handleNewUser("guildid", "My Awesome Guild", "jacques")
        }
        coVerifyAll {
            cfg.servers
            dm.getGreetingsEmbed(any(), "guildid", "My Awesome Guild")
            dcf.sendDirectMessage("jacques", mockem)
            db.getUser("jacques")
        }
        confirmVerified(cfg, dm, dcf, db)
    }

    @Test
    fun `Test on join, unknown user, no greeting`() {
        val cfg = mockDiscordConfigHere {
            server("guildid") {
                enableWelcomeMessage = false
                stickyRoles()
            }
        }
        val db = mockHere<LinkDatabaseFacade> {
            coEvery { getUser("jacques") } returns null
        }
        declareNoOpI18n()
        val dm = mockHere<LinkDiscordMessages> {
            every { getGreetingsEmbed(any(), "guildid", "My Awesome Guild") } returns null
        }
        test {
            handleNewUser("guildid", "My Awesome Guild", "jacques")
        }
        coVerifyAll {
            cfg.servers
            dm.getGreetingsEmbed(any(), "guildid", "My Awesome Guild")
            db.getUser("jacques")
        }
        confirmVerified(cfg, dm, db)
    }

    @Test
    fun `Test on join, known`() {
        val cfg = mockDiscordConfigHere {
            server("guildid") {
                enableWelcomeMessage = false
                stickyRoles()
            }
        }
        val userMock: LinkUser = mockk()
        val db = mockHere<LinkDatabaseFacade> {
            coEvery { getUser("jacques") } returns userMock
        }
        val rm = declare { spyk(LinkRoleManagerImpl()) }
        coEvery { rm.updateRolesOnGuilds("jacques", listOf("guildid"), true) } just runs
        runBlocking {
            rm.handleNewUser("guildid", "My Awesome Guild", "jacques")
        }
        coVerifyAll {
            cfg.servers
            db.getUser("jacques")
            rm.updateRolesOnGuilds("jacques", listOf("guildid"), true)
            rm.handleNewUser("guildid", "My Awesome Guild", "jacques")
        }
        confirmVerified(cfg, rm, db)
    }

    @Test
    fun `Test rules relevant for guilds`() {
        val weakRule: WeakIdentityRule = mockk()
        val strongRule: StrongIdentityRule = mockk()
        val otherRule: Rule = mockk()
        val fourthRule: Rule = mockk()
        mockHere<Rulebook> {
            every { rules } returns mapOf(
                "WeakRule" to weakRule,
                "StrongRule" to strongRule,
                "OtherRule" to otherRule,
                "FourthRule" to fourthRule
            )
        }
        mockDiscordConfigHere {
            // Typical server with a mix of standard and rule-based roles
            server("guildid1") {
                "_known" boundTo "g1rk"
                "weakrole" boundTo "g1rw"
                "strongrole" boundTo "g1rs"
                requires("WeakRule", "StrongRule")
            }
            // Purely standard roles guild
            server("guildid2") {
                "_known" boundTo "g2rk"
                "_identified" boundTo "g2ri"
            }
            // Purely rule-based roles guild
            server("guildid3") {
                "weakrole" boundTo "g3rw"
                "otherrole" boundTo "g3ro"
                requires("WeakRule", "OtherRule")
            }
            // Guild that should be ignored
            server("guildid4")
        }
        runBlocking {
            val rm = get<LinkRoleManager>()
            val rules = rm.getRulesRelevantForGuilds("guildid1", "guildid2", "guildid3")
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
    }

    @Test
    fun `Test get roles for unknown user`() {
        mockHere<LinkDatabaseFacade> {
            coEvery { getUser("userid") } returns null
        }
        val rm = get<LinkRoleManager>()
        runBlocking {
            val roles = rm.getRolesForUser("userid", mockk(), false, listOf())
            assertEquals(setOf(), roles.first, "roles should be empty")
            assertTrue(roles.second, "Sticky roles should be considered")
        }
    }

    @Test
    fun `Test get roles for disallowed user, do not tell`() {
        val user: LinkUser = mockk()
        mockHere<LinkDatabaseFacade> {
            coEvery { getUser("userid") } returns user
        }
        mockHere<LinkPermissionChecks> {
            coEvery { canUserJoinServers(user) } returns Disallowed("This is my reason", "yes.maybe")
        }
        val rm = get<LinkRoleManager>()
        runBlocking {
            val roles = rm.getRolesForUser("userid", mockk(), false, listOf())
            assertEquals(setOf(), roles.first, "roles should be empty")
            assertFalse(roles.second, "Sticky roles should be ignored")
        }
    }

    @Test
    fun `Test get roles for disallowed user, notify`() {
        val user: LinkUser = mockk()
        mockHere<LinkDatabaseFacade> {
            coEvery { getUser("userid") } returns user
        }
        mockHere<LinkPermissionChecks> {
            coEvery { canUserJoinServers(user) } returns Disallowed("This is my reason", "yes.allo")
        }
        val embed: DiscordEmbed = mockk()
        val dcf = mockHere<LinkDiscordClientFacade> {
            coEvery { sendDirectMessage("userid", embed) } just runs
            coEvery { getGuildName("HELLO THERE") } returns "GENERAL KENOBI"
        }
        declareNoOpI18n()
        mockHere<LinkDiscordMessages> {
            every { getCouldNotJoinEmbed(any(), "GENERAL KENOBI", "This is my reason") } returns embed
        }
        val rm = get<LinkRoleManager>()
        runBlocking {
            val roles = rm.getRolesForUser("userid", mockk(), true, listOf("HELLO THERE"))
            assertEquals(setOf(), roles.first, "roles should be empty")
            assertFalse(roles.second, "Sticky roles should be ignored")
        }
        coVerify { dcf.sendDirectMessage("userid", embed) }
    }

    @Test
    fun `Test get roles, no rules, not identifiable`() {
        val user: LinkUser = mockk()
        @OptIn(UsesTrueIdentity::class)
        mockHere<LinkDatabaseFacade> {
            coEvery { getUser("userid") } returns user
            coEvery { isUserIdentifiable(user) } returns false
        }
        mockHere<LinkPermissionChecks> {
            coEvery { canUserJoinServers(user) } returns Allowed
        }
        val rm = get<LinkRoleManager>()
        runBlocking {
            val roles = rm.getRolesForUser("userid", listOf(), true, listOf())
            assertEquals(setOf("_known", "_notIdentified"), roles.first)
            assertTrue(roles.second)
        }
    }

    @Test
    fun `Test get roles, no rules, identifiable`() {
        val user: LinkUser = mockk()
        @OptIn(UsesTrueIdentity::class)
        mockHere<LinkDatabaseFacade> {
            coEvery { getUser("userid") } returns user
            coEvery { isUserIdentifiable(user) } returns true
        }
        mockHere<LinkPermissionChecks> {
            coEvery { canUserJoinServers(user) } returns Allowed
        }
        val rm = get<LinkRoleManager>()
        runBlocking {
            val roles = rm.getRolesForUser("userid", listOf(), true, listOf())
            assertEquals(setOf("_known", "_identified"), roles.first)
            assertTrue(roles.second)
        }
    }

    @Test
    @OptIn(UsesTrueIdentity::class)
    fun `Test get roles, with rules, identifiable`() {
        val user: LinkUser = mockk { every { discordId } returns "userid" }
        val lia = mockHere<LinkIdManager> {
            coEvery { accessIdentity(user, any(), any(), any()) } returns "email@@"
        }
        mockHere<LinkDatabaseFacade> {
            coEvery { getUser("userid") } returns user
            coEvery { isUserIdentifiable(user) } returns true
        }
        mockHere<LinkPermissionChecks> {
            coEvery { canUserJoinServers(user) } returns Allowed
        }
        val dui = DiscordUserInfo("userid", "uname", "disc")
        mockHere<LinkDiscordClientFacade> {
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
        declare<CacheClient> { MemoryCacheClient() }
        val rm = get<LinkRoleManager>()
        runBlocking {
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
        }
        coVerify { lia.accessIdentity(user, any(), any(), any()) }
    }

    @Test
    fun `Test get roles, with rules, not identifiable`() {
        val user: LinkUser = mockk { every { discordId } returns "userid" }
        @OptIn(UsesTrueIdentity::class)
        mockHere<LinkDatabaseFacade> {
            coEvery { getUser("userid") } returns user
            coEvery { isUserIdentifiable(user) } returns false
        }
        mockHere<LinkPermissionChecks> {
            coEvery { canUserJoinServers(user) } returns Allowed
        }
        val dui = DiscordUserInfo("userid", "uname", "disc")
        mockHere<LinkDiscordClientFacade> {
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
        declare<CacheClient> { MemoryCacheClient() }
        val rm = get<LinkRoleManager>()
        runBlocking {
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
    }

    @Test
    fun `Test update user with roles applying sticky roles`() =
        updateUserWithRolesTest(true)

    @Test
    fun `Test update user with roles ignoring sticky roles`() =
        updateUserWithRolesTest(false)

    private fun updateUserWithRolesTest(applySticky: Boolean) {
        val dcf = mockHere<LinkDiscordClientFacade> {
            coEvery { manageRoles(any(), any(), any(), any()) } just runs
        }
        mockDiscordConfigHere {
            server("guildid") {
                "eladd" boundTo "addMe"
                "eladd2" boundTo "addMeToo"
                "elrem" boundTo "removeMe"
                "elrem2" boundTo "removeMeToo"
                "sr1" boundTo "maybeRemoveMe"
                "sr2" boundTo "maybeRemoveMeAlso"
                stickyRoles("sr1")
            }
            stickyRoles("sr2")
        }
        val rm = get<LinkRoleManager>()
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