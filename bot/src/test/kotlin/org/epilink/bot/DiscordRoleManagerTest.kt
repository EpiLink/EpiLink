package org.epilink.bot

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.epilink.bot.config.LinkDiscordConfig
import org.epilink.bot.config.LinkDiscordRoleSpec
import org.epilink.bot.config.LinkDiscordServerSpec
import org.epilink.bot.config.rulebook.Rulebook
import org.epilink.bot.config.rulebook.StrongIdentityRule
import org.epilink.bot.config.rulebook.WeakIdentityRule
import org.epilink.bot.db.*
import org.epilink.bot.discord.*
import org.koin.dsl.module
import org.koin.test.get
import kotlin.test.Test
import kotlin.test.assertEquals

class DiscordRoleManagerTest : KoinBaseTest(
    module {
        single<LinkRoleManager> { LinkRoleManagerImpl() }
    }
) {
    @Test
    fun `Test role update for disallowed user`() {
        val sd = mockHere<LinkServerDatabase> {
            coEvery { canUserJoinServers(any()) } returns Disallowed("This is my reason")
        }
        val dcf = mockHere<LinkDiscordClientFacade> {
            coEvery { sendDirectMessage("userid", any()) } just runs
        }
        val dm = mockHere<LinkDiscordMessages> {
            every { getCouldNotJoinEmbed(any(), "This is my reason") } returns mockk()
        }
        runBlocking {
            val rm = get<LinkRoleManager>()
            rm.updateRolesOnGuilds(mockk { every { discordId } returns "userid" }, mockk(), true)
        }
        coVerifyAll {
            sd.canUserJoinServers(any())
            dcf.sendDirectMessage("userid", any())
            dm.getCouldNotJoinEmbed(any(), "This is my reason")
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test role update on guilds`() {
        // Gigantic test that pretty much represents the complete functionality of the role update
        val r = mockHere<Rulebook> {
            every { rules } returns mapOf(
                "My Rule" to WeakIdentityRule("My Rule") {
                    assertEquals("userid", userDiscordId)
                    assertEquals("hi", userDiscordName)
                    assertEquals("1234", userDiscordDiscriminator)
                    roles += "fromWeak"
                },
                "My Strong Rule" to StrongIdentityRule("My Stronger Rule") { email ->
                    assertEquals("userid", userDiscordId)
                    assertEquals("hi", userDiscordName)
                    assertEquals("1234", userDiscordDiscriminator)
                    assertEquals("user@example.com", email)
                    roles += "fromStrong"
                },
                "Not Called Rule" to WeakIdentityRule("Not Called Rule") {
                    error("Don't call me!")
                },
                "Not Called Strong Rule" to StrongIdentityRule("Not Called Strong Rule") {
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
            coEvery { canUserJoinServers(dbUser) } returns Allowed
            coEvery { isUserIdentifiable("userid") } returns true
            coEvery { accessIdentity(dbUser, any(), any(), any()) } returns "user@example.com"
        }
        val dm = mockHere<LinkDiscordMessages> {
            coEvery { getIdentityAccessEmbed(true, any(), any()) } returns mockk()
        }
        runBlocking {
            val rm = get<LinkRoleManager>()
            rm.updateRolesOnGuilds(dbUser, listOf("serverid", "otherserver", "ghostserver"), true)
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
            db.isUserIdentifiable("userid")
            db.accessIdentity(dbUser, any(), any(), any())
            dm.getIdentityAccessEmbed(true, any(), any())
        }
        confirmVerified(r, dc, dcf, db, dm, dbUser)
    }
}