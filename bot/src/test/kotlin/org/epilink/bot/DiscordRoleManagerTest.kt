/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.epilink.bot.config.LinkDiscordConfig
import org.epilink.bot.config.LinkDiscordRoleSpec
import org.epilink.bot.config.LinkDiscordServerSpec
import org.epilink.bot.rulebook.Rule
import org.epilink.bot.rulebook.Rulebook
import org.epilink.bot.rulebook.StrongIdentityRule
import org.epilink.bot.rulebook.WeakIdentityRule
import org.epilink.bot.db.*
import org.epilink.bot.discord.*
import org.koin.dsl.module
import org.koin.test.get
import org.koin.test.mock.declare
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscordRoleManagerTest : KoinBaseTest(
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
            coEvery { isUserInGuild("userid", "guildid") } returns true
            coEvery { getGuildName("Hello") } returns "There"
        }
        val dm = mockHere<LinkDiscordMessages> {
            every { getCouldNotJoinEmbed("There", "This is my reason") } returns mockk()
        }
        runBlocking {
            val rm = get<LinkRoleManager>()
            rm.getRolesForUser("userid", mockk(), true, listOf("Hello"))
        }
        coVerifyAll {
            pc.canUserJoinServers(any())
            dcf.sendDirectMessage("userid", any())
            dcf.getGuildName("Hello")
            sd.getUser("userid")
            dm.getCouldNotJoinEmbed(any(), "This is my reason")
        }
        confirmVerified(sd, dcf, dm)
    }

    @Test
    fun `Test on join, unmonitored guild`() {
        val cfg = mockHere<LinkDiscordConfig> {
            every { servers } returns listOf(mockk { every { id } returns "otherid" })
        }
        runBlocking {
            val rm = get<LinkRoleManager>()
            rm.handleNewUser("guildid", "My Awesome Guild", "userid")
        }
        coVerifyAll {
            cfg.servers
        }
        confirmVerified(cfg)
    }

    @Test
    fun `Test on join, unknown user, send greeting`() {
        val cfg = mockHere<LinkDiscordConfig> {
            every { servers } returns listOf(
                LinkDiscordServerSpec(
                    id = "guildid",
                    enableWelcomeMessage = true,
                    roles = mockk()
                )
            )
        }
        val mockem = mockk<DiscordEmbed>()
        val db = mockHere<LinkDatabaseFacade> {
            coEvery { getUser("jacques") } returns null
        }
        val dm = mockHere<LinkDiscordMessages> {
            every { getGreetingsEmbed("guildid", "My Awesome Guild") } returns mockem
        }
        val dcf = mockHere<LinkDiscordClientFacade> {
            coEvery { sendDirectMessage("jacques", mockem) } just runs
        }
        runBlocking {
            val rm = get<LinkRoleManager>()
            rm.handleNewUser("guildid", "My Awesome Guild", "jacques")
        }
        coVerifyAll {
            cfg.servers
            dm.getGreetingsEmbed("guildid", "My Awesome Guild")
            dcf.sendDirectMessage("jacques", mockem)
            db.getUser("jacques")
        }
        confirmVerified(cfg, dm, dcf, db)
    }

    @Test
    fun `Test on join, unknown user, no greeting`() {
        val cfg = mockHere<LinkDiscordConfig> {
            every { servers } returns listOf(
                LinkDiscordServerSpec(
                    id = "guildid",
                    enableWelcomeMessage = false,
                    roles = mockk()
                )
            )
        }
        val db = mockHere<LinkDatabaseFacade> {
            coEvery { getUser("jacques") } returns null
        }
        val dm = mockHere<LinkDiscordMessages> {
            every { getGreetingsEmbed("guildid", "My Awesome Guild") } returns null
        }
        runBlocking {
            val rm = get<LinkRoleManager>()
            rm.handleNewUser("guildid", "My Awesome Guild", "jacques")
        }
        coVerifyAll {
            cfg.servers
            dm.getGreetingsEmbed("guildid", "My Awesome Guild")
            db.getUser("jacques")
        }
        confirmVerified(cfg, dm, db)
    }

    @Test
    fun `Test on join, known`() {
        val cfg = mockHere<LinkDiscordConfig> {
            every { servers } returns listOf(
                LinkDiscordServerSpec(
                    id = "guildid",
                    enableWelcomeMessage = false,
                    roles = mockk()
                )
            )
        }
        val usermock: LinkUser = mockk()
        val db = mockHere<LinkDatabaseFacade> {
            coEvery { getUser("jacques") } returns usermock
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
        mockHere<LinkDiscordConfig> {
            every { servers } returns listOf(
                // Typical server with a mix of standard and rule-based roles
                mockk {
                    every { id } returns "guildid1"
                    every { roles } returns mapOf(
                        "_known" to "g1rk",
                        "weakrole" to "g1rw",
                        "strongrole" to "g1rs"
                    )
                },
                // Purely standard roles guild
                mockk {
                    every { id } returns "guildid2"
                    every { roles } returns mapOf(
                        "_known" to "g2rk",
                        "_identified" to "g2ri"
                    )
                },
                // Purely rule-based roles guild
                mockk {
                    every { id } returns "guildid3"
                    every { roles } returns mapOf(
                        "weakrole" to "g3rw",
                        "otherrole" to "g3ro"
                    )
                },
                // Guild that should be ignored
                mockk {
                    every { id } returns "guildid4"
                }
            )

            every { roles } returns listOf(
                LinkDiscordRoleSpec("weakrole", rule = "WeakRule"),
                LinkDiscordRoleSpec("strongrole", rule = "StrongRule"),
                LinkDiscordRoleSpec("otherrole", rule = "OtherRule")
            )
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
            assertTrue(roles.isEmpty(), "roles should be empty")
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
            assertTrue(roles.isEmpty(), "roles should be empty")
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
        mockHere<LinkDiscordMessages> {
            every { getCouldNotJoinEmbed("GENERAL KENOBI", "This is my reason") } returns embed
        }
        val rm = get<LinkRoleManager>()
        runBlocking {
            val roles = rm.getRolesForUser("userid", mockk(), true, listOf("HELLO THERE"))
            assertTrue(roles.isEmpty(), "roles should be empty")
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
            assertEquals(setOf("_known"), roles)
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
            assertEquals(setOf("_known", "_identified"), roles)
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
                roles
            )
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
            assertEquals(setOf("_known", "wirId_userid", "wirDisc_disc"), roles)
        }
    }

    @Test
    fun `Test update user with roles`() {
        val dcf = mockHere<LinkDiscordClientFacade> {
            coEvery { manageRoles(any(), any(), any(), any()) } just runs
        }
        mockHere<LinkDiscordConfig> {
            every { servers } returns listOf(
                mockk {
                    every { id } returns "guildid"
                    every { roles } returns mapOf(
                        "eladd" to "addMe",
                        "eladd2" to "addMeToo",
                        "elrem" to "removeMe",
                        "elrem2" to "removeMeToo"
                    )
                }
            )
        }
        val rm = get<LinkRoleManager>()
        runBlocking {
            rm.updateUserWithRoles("userid", "guildid", setOf("eladd", "eladd2", "elneut", "elneut2"))
        }
        coVerify { dcf.manageRoles("userid", "guildid", setOf("addMe", "addMeToo"), setOf("removeMe", "removeMeToo")) }
        confirmVerified(dcf)
    }
}