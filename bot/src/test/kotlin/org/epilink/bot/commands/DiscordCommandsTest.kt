/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.commands

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.epilink.bot.KoinBaseTest
import org.epilink.bot.config.LinkDiscordConfig
import org.epilink.bot.db.*
import org.epilink.bot.declareNoOpI18n
import org.epilink.bot.discord.*
import org.epilink.bot.mockHere
import org.epilink.bot.softMockHere
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.get
import org.koin.test.mock.declare
import kotlin.test.*

class DiscordCommandsTest : KoinBaseTest<LinkDiscordCommands>(
    LinkDiscordCommands::class,
    module {
        single<LinkDiscordCommands> { LinkDiscordCommandsImpl() }
    }
) {
    @Test
    fun `Do not accept prefix-less messages`() {
        test {
            // No assertions, as this will crash anyway if anything is called
            handleMessage("The prefix ain't here", "1234", "5678", "90")
        }
    }

    @Test
    fun `Do not accept messages from non-admins if admin-only command`() {
        val (dcf, e) = mockErrorMessage("cr.nan").mockSend("5678")
        declareCommand("hellothere") {}
        test(server = "90") {
            handleMessage("e!hellothere", "1234", "5678", "90")
            coVerify { dcf.sendChannelMessage("5678", e) }
        }
    }

    @Test
    fun `Do not accept commands from unmonitored servers if monitored server is required`() {
        val (dcf, e) = mockErrorMessage("cr.snm").mockSend("5678")
        declareCommand("hellothere", requireMonitored = true) {}
        test("1234") {
            handleMessage("e!hellothere", "1234", "5678", "90")
            coVerify { dcf.sendChannelMessage("5678", e) }
        }
    }

    @Test
    fun `Accept commands from DMs if monitored server is not required`() {
        val c = declareCommandNoOp("hellothere", PermissionLevel.Anyone, requireMonitored = false)
        test("1234") {
            handleMessage("e!hellothere", "1234", "5678", null)
            coVerify { c.run("e!hellothere", "", null, "1234", "5678", null) }
        }
    }

    @Test
    fun `Accept commands from unmonitored servers if monitored server is not required`() {
        val c = declareCommandNoOp("hellothere", PermissionLevel.Anyone, requireMonitored = false)
        test {
            handleMessage("e!hellothere", "1234", "5678", "90")
            coVerify { c.run("e!hellothere", "", null, "1234", "5678", "90") }
        }
    }

    @Test
    fun `Do not accept messages from non-registered users if admin command`() {
        val (dcf, e) = mockErrorMessage("cr.nr").mockSend("5678")
        declareCommand("hellothere") {}
        test("1234", "90") {
            handleMessage("e!hellothere", "1234", "5678", "90")
            coVerify { dcf.sendChannelMessage("5678", e) }
        }
    }

    @Test
    fun `Do not accept messages from non-registered users if user command`() {
        val (dcf, e) = mockErrorMessage("cr.nr").mockSend("5678")
        declareCommand("hellothere", PermissionLevel.User) {}
        test(null, "90") {
            handleMessage("e!hellothere", "1234", "5678", "90")
            coVerify { dcf.sendChannelMessage("5678", e) }
        }
    }

    @Test
    fun `Do not accept messages from unidentified admins`() {
        val (dcf, e) = mockErrorMessage("cr.awni").mockSend("5678")
        val u = mockk<LinkUser> { every { discordId } returns "1234" }
        declareCommand("hellothere") {}
        test("1234", "90", u) {
            handleMessage("e!hellothere", "1234", "5678", "90")
            coVerify { dcf.sendChannelMessage("5678", e) }
        }
    }

    @Test
    fun `Accept messages without command body`() {
        val c = declareCommandNoOp("hellothere")
        val u = mockk<LinkUser> { every { discordId } returns "1234" }
        test("1234", "90", u, true) {
            handleMessage("e!hellothere", "1234", "5678", "90")
        }
        coVerify {
            c.run("e!hellothere", "", u, "1234", "5678", "90")
        }
    }

    @Test
    fun `Accept messages with command body`() {
        val c = declareCommandNoOp("hellothere")
        val u = mockk<LinkUser> { every { discordId } returns "1234" }
        test("1234", "90", u, true) {
            handleMessage("e!hellothere this is my command's body", "1234", "5678", "90")
        }
        coVerify {
            c.run("e!hellothere this is my command's body", "this is my command's body", u, "1234", "5678", "90")
        }
    }

    @Test
    fun `Accept messages from users if user command`() {
        val c = declareCommandNoOp("hellothere", PermissionLevel.User)
        val u = mockk<LinkUser> { every { discordId } returns "1234" }
        test(null, "90", u) {
            handleMessage("e!hellothere", "1234", "5678", "90")
        }
        coVerify {
            c.run("e!hellothere", "", u, "1234", "5678", "90")
        }
    }

    @Test
    fun `Accept messages from unregistered user if anyone command`() {
        val c = declareCommandNoOp("hellothere", PermissionLevel.Anyone)
        test(null, "90") {
            handleMessage("e!hellothere", "1234", "5678", "90")
        }
        coVerify {
            c.run("e!hellothere", "", null, "1234", "5678", "90")
        }
    }

    private fun declareCommandNoOp(
        name: String,
        permissionLevel: PermissionLevel = PermissionLevel.Admin,
        requireMonitored: Boolean = true
    ): Command =
        declareCommand(name, permissionLevel, requireMonitored) {
            coEvery { run(any(), any(), any(), any(), any(), any()) } just runs
        }

    private fun declareCommand(
        name: String,
        permissionLevel: PermissionLevel = PermissionLevel.Admin,
        requireMonitored: Boolean = true,
        initializer: Command.() -> Unit
    ): Command {
        val c = mockk<Command> {
            every { this@mockk.name } returns name
            every { this@mockk.permissionLevel } returns permissionLevel
            every { this@mockk.requireMonitoredServer } returns requireMonitored
            initializer()
        }
        declare(named("discord.commands")) { listOf(c) }
        return c
    }

    @OptIn(UsesTrueIdentity::class)
    private fun test(
        admin: String? = null,
        server: String? = null,
        user: LinkUser? = null,
        identityAvailable: Boolean = false,
        block: suspend LinkDiscordCommands.() -> Unit
    ) {
        declareNoOpI18n()
        softMockHere<LinkDiscordConfig> {
            every { commandsPrefix } returns "e!"
            if (server == null)
                every { servers } returns listOf()
            else
                every { servers } returns listOf(mockk {
                    every { id } returns server
                })
        }
        softMockHere<LinkDatabaseFacade> {
            coEvery { getUser(any()) } returns user
        }
        if (user != null) {
            softMockHere<LinkPermissionChecks> {
                coEvery { canPerformAdminActions(user) } returns
                        if (identityAvailable) AdminStatus.Admin
                        else AdminStatus.AdminNotIdentifiable
            }
        }
        declare(named("admins")) { if (admin == null) listOf() else listOf(admin) }
        test(block)
    }

    private fun mockErrorMessage(i18nKey: String): DiscordEmbed =
        mockk<DiscordEmbed>().also {
            mockHere<LinkDiscordMessages> { every { getErrorCommandReply(any(), i18nKey) } returns it }
        }

    private fun DiscordEmbed.mockSend(channel: String) =
        softMockHere<LinkDiscordClientFacade> { coEvery { sendChannelMessage(channel, this@mockSend) } returns "" } to this

}