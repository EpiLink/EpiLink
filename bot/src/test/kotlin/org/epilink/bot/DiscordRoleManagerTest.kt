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
        val sd = mockHere<LinkServerDatabase> {
            coEvery { getUser("userid") } returns user
            coEvery { canUserJoinServers(user) } returns Disallowed("This is my reason")
        }
        val dcf = mockHere<LinkDiscordClientFacade> {
            coEvery { sendDirectMessage("userid", any()) } just runs
            coEvery { isUserInGuild("userid", "guildid") } returns true
        }
        val dm = mockHere<LinkDiscordMessages> {
            every { getCouldNotJoinEmbed(any(), "This is my reason") } returns mockk()
        }
        runBlocking {
            val rm = get<LinkRoleManager>()
            rm.getRolesForUser("userid", mockk(), mockk(), true)
        }
        coVerifyAll {
            sd.canUserJoinServers(any())
            dcf.sendDirectMessage("userid", any())
            sd.getUser("userid")
            dm.getCouldNotJoinEmbed(any(), "This is my reason")
        }
        confirmVerified(sd, dcf, dm)
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test role update on guilds`() {
        // Gigantic test that pretty much represents the complete functionality of the role update
        val r = mockHere<Rulebook> {
            every { rules } returns mapOf(
                "My Rule" to WeakIdentityRule("My Rule", null) {
                    assertEquals("userid", userDiscordId)
                    assertEquals("hi", userDiscordName)
                    assertEquals("1234", userDiscordDiscriminator)
                    roles += "fromWeak"
                },
                "My Strong Rule" to StrongIdentityRule("My Stronger Rule", null) { email ->
                    assertEquals("userid", userDiscordId)
                    assertEquals("hi", userDiscordName)
                    assertEquals("1234", userDiscordDiscriminator)
                    assertEquals("user@example.com", email)
                    roles += "fromStrong"
                },
                "Not Called Rule" to WeakIdentityRule("Not Called Rule", null) {
                    error("Don't call me!")
                },
                "Not Called Strong Rule" to StrongIdentityRule("Not Called Strong Rule", null) {
                    error("Don't call me!")
                }
            )
        }
        val dc = mockHere<LinkDiscordConfig> {
            every { roles } returns listOf(
                LinkDiscordRoleSpec("fromWeak", rule = "My Rule"),
                LinkDiscordRoleSpec("fromStrong", rule = "My Strong Rule"),
                LinkDiscordRoleSpec("fromNotCalled", rule = "Not Called Rule"),
                LinkDiscordRoleSpec("fromNotStrongCalled", rule = "Not Called Strong Rule")
            )
            every { servers } returns listOf(
                LinkDiscordServerSpec(
                    enableWelcomeMessage = true,
                    id = "serverid",
                    roles = mapOf(
                        "fromWeak" to "discordrolefromweak",
                        "fromStrong" to "discordrolefromstrong",
                        "cantGetThis" to "discordrolenever"
                    )
                ),
                LinkDiscordServerSpec(
                    enableWelcomeMessage = false,
                    id = "otherserver",
                    roles = mapOf(
                        "fromWeak" to "otherrolefromweak",
                        "_known" to "helloworld",
                        "_identified" to "helloooo"
                    )
                ),
                LinkDiscordServerSpec(
                    enableWelcomeMessage = true,
                    id = "ghostserver",
                    roles = mapOf(
                        "fromStrong" to "ghostweak",
                        "_known" to "ghostknown"
                    )
                )
            )
        }
        val dcf = mockHere<LinkDiscordClientFacade> {
            coEvery { isUserInGuild("userid", "serverid") } returns true
            coEvery { isUserInGuild("userid", "otherserver") } returns true
            coEvery { isUserInGuild("userid", "ghostserver") } returns false
            coEvery { getDiscordUserInfo("userid") } returns DiscordUserInfo("userid", "hi", "1234")
            coEvery { getGuildName("serverid") } returns "My Amazing Server"
            coEvery { sendDirectMessage("userid", any()) } just runs
            coEvery {
                manageRoles(
                    "userid",
                    "serverid",
                    setOf("discordrolefromweak", "discordrolefromstrong"),
                    setOf("discordrolenever")
                )
            } just runs
            coEvery {
                manageRoles(
                    "userid",
                    "otherserver",
                    setOf("otherrolefromweak", "helloworld", "helloooo"),
                    setOf()
                )
            } just runs
        }
        val dbUser: LinkUser = mockk { every { discordId } returns "userid" }
        val db = mockHere<LinkServerDatabase> {
            coEvery { getUser("userid") } returns dbUser
            coEvery { canUserJoinServers(dbUser) } returns Allowed
            coEvery { isUserIdentifiable("userid") } returns true
            coEvery { accessIdentity(dbUser, any(), any(), any()) } returns "user@example.com"
        }
        val dm = mockHere<LinkDiscordMessages> {
            coEvery { getIdentityAccessEmbed(true, any(), any()) } returns mockk()
        }
        declare<RuleMediator> { NoCacheRuleMediator() }
        runBlocking {
            val rm = get<LinkRoleManager>()
            rm.updateRolesOnGuilds("userid", listOf("serverid", "otherserver", "ghostserver"), true)
        }
        coVerifyAll {
            r.rules
            dc.roles
            dc.servers
            dbUser.discordId
            dcf.isUserInGuild("userid", "serverid")
            dcf.isUserInGuild("userid", "otherserver")
            dcf.isUserInGuild("userid", "ghostserver")
            dcf.getDiscordUserInfo("userid")
            dcf.getGuildName("serverid")
            dcf.sendDirectMessage("userid", any())
            dcf.manageRoles("userid", "serverid", any(), any())
            dcf.manageRoles("userid", "otherserver", any(), any())
            db.canUserJoinServers(dbUser)
            db.getUser("userid")
            db.isUserIdentifiable("userid")
            db.accessIdentity(dbUser, any(), any(), any())
            dm.getIdentityAccessEmbed(true, any(), any())
        }
        confirmVerified(r, dc, dcf, db, dm, dbUser)
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
        val db = mockHere<LinkServerDatabase> {
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
        val db = mockHere<LinkServerDatabase> {
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
        val db = mockHere<LinkServerDatabase> {
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
        mockHere<LinkServerDatabase> {
            coEvery { getUser("userid") } returns null
        }
        val rm = get<LinkRoleManager>()
        runBlocking {
            val roles = rm.getRolesForUser("userid", mockk(), mockk(), false)
            assertTrue(roles.isEmpty(), "roles should be empty")
        }
    }

    @Test
    fun `Test get roles for disallowed user, do not tell`() {
        val user: LinkUser = mockk()
        mockHere<LinkServerDatabase> {
            coEvery { getUser("userid") } returns user
            coEvery { canUserJoinServers(user) } returns Disallowed("This is my reason")
        }
        val rm = get<LinkRoleManager>()
        runBlocking {
            val roles = rm.getRolesForUser("userid", mockk(), mockk(), false)
            assertTrue(roles.isEmpty(), "roles should be empty")
        }
    }

    @Test
    fun `Test get roles for disallowed user, notify`() {
        val user: LinkUser = mockk()
        mockHere<LinkServerDatabase> {
            coEvery { getUser("userid") } returns user
            coEvery { canUserJoinServers(user) } returns Disallowed("This is my reason")
        }
        val embed: DiscordEmbed = mockk()
        val dcf = mockHere<LinkDiscordClientFacade> {
            coEvery { sendDirectMessage("userid", embed) } just runs
        }
        mockHere<LinkDiscordMessages> {
            every { getCouldNotJoinEmbed(any(), "This is my reason") } returns embed
        }
        val rm = get<LinkRoleManager>()
        runBlocking {
            val roles = rm.getRolesForUser("userid", mockk(), mockk(), true)
            assertTrue(roles.isEmpty(), "roles should be empty")
        }
        coVerify { dcf.sendDirectMessage("userid", embed) }
    }

    @Test
    fun `Test get roles, no rules, not identifiable`() {
        val user: LinkUser = mockk()
        @OptIn(UsesTrueIdentity::class)
        mockHere<LinkServerDatabase> {
            coEvery { getUser("userid") } returns user
            coEvery { canUserJoinServers(user) } returns Allowed
            coEvery { isUserIdentifiable("userid") } returns false
        }
        val rm = get<LinkRoleManager>()
        runBlocking {
            val roles = rm.getRolesForUser("userid", listOf(), listOf(), true)
            assertEquals(setOf("_known"), roles)
        }
    }

    @Test
    fun `Test get roles, no rules, identifiable`() {
        val user: LinkUser = mockk()
        @OptIn(UsesTrueIdentity::class)
        mockHere<LinkServerDatabase> {
            coEvery { getUser("userid") } returns user
            coEvery { canUserJoinServers(user) } returns Allowed
            coEvery { isUserIdentifiable("userid") } returns true
        }
        val rm = get<LinkRoleManager>()
        runBlocking {
            val roles = rm.getRolesForUser("userid", listOf(), listOf(), true)
            assertEquals(setOf("_known", "_identified"), roles)
        }
    }

    @Test
    fun `Test get roles, with rules, identifiable`() {
        val user: LinkUser = mockk { every { discordId } returns "userid" }
        @OptIn(UsesTrueIdentity::class)
        mockHere<LinkServerDatabase> {
            coEvery { getUser("userid") } returns user
            coEvery { canUserJoinServers(user) } returns Allowed
            coEvery { isUserIdentifiable("userid") } returns true
            coEvery { accessIdentity(user, any(), any(), any()) } returns "email@@"
        }
        val dui = DiscordUserInfo("userid", "uname", "disc")
        val embed: DiscordEmbed = mockk()
        val dcf = mockHere<LinkDiscordClientFacade> {
            coEvery { getDiscordUserInfo("userid") } returns dui.copy()
            coEvery { sendDirectMessage("userid", embed) } just runs
        }
        mockHere<LinkDiscordMessages> {
            coEvery { getIdentityAccessEmbed(true, any(), any()) } returns embed
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
        declare<RuleMediator> { NoCacheRuleMediator() }
        val rm = get<LinkRoleManager>()
        runBlocking {
            val roles = rm.getRolesForUser("userid", listOf(rule1, rule2), listOf("Guildo", "Abcde"), true)
            assertEquals(
                setOf("_known", "_identified", "sir_email@@", "sirId_userid", "wirId_userid", "wirDisc_disc"),
                roles
            )
        }
        coVerify { dcf.sendDirectMessage("userid", embed) }
    }

    @Test
    fun `Test get roles, with rules, not identifiable`() {
        val user: LinkUser = mockk { every { discordId } returns "userid" }
        @OptIn(UsesTrueIdentity::class)
        mockHere<LinkServerDatabase> {
            coEvery { getUser("userid") } returns user
            coEvery { canUserJoinServers(user) } returns Allowed
            coEvery { isUserIdentifiable("userid") } returns false
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
        declare<RuleMediator> { NoCacheRuleMediator() }
        val rm = get<LinkRoleManager>()
        runBlocking {
            val roles = rm.getRolesForUser("userid", listOf(rule1, rule2), listOf("Guildo", "Abcde"), true)
            assertEquals(
                setOf("_known", "wirId_userid", "wirDisc_disc"),
                roles
            )
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