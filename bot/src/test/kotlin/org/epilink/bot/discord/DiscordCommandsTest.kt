/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import guru.zoroark.shedinja.dsl.ContextBuilderDsl
import guru.zoroark.shedinja.dsl.put
import guru.zoroark.shedinja.environment.named
import guru.zoroark.shedinja.test.ShedinjaBaseTest
import guru.zoroark.shedinja.test.UnsafeMutableEnvironment
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.epilink.bot.config.DiscordConfiguration
import org.epilink.bot.db.*
import org.epilink.bot.putMock
import org.epilink.bot.softPutMock
import org.epilink.bot.web.declareNoOpI18n
import org.koin.test.mock.declare
import javax.naming.Context
import kotlin.test.*

class DiscordCommandsTest : ShedinjaBaseTest<DiscordCommands>(
    DiscordCommands::class, {
        put<DiscordCommands>(::DiscordCommandsImpl)
    }
) {
    @Test
    fun `Do not accept prefix-less messages`() {
        test(null) {
            // No assertions, as this will crash anyway if anything is called
            handleMessage("The prefix ain't here", "1234", "5678", "90")
        }
    }

    @Test
    fun `Do not accept messages from non-admins if admin-only command`() = test(server = "90") {
        val (dcf, e) = it.mockErrorMessage("cr.nan").mockSend(it, "5678")
        it.declareCommand("hellothere") {}
        handleMessage("e!hellothere", "1234", "5678", "90")
        coVerify { dcf.sendChannelMessage("5678", e) }
    }

    @Test
    fun `Do not accept commands from unmonitored servers if monitored server is required`() = test("1234") {
        val (dcf, e) = it.mockErrorMessage("cr.snm").mockSend(it, "5678")
        it.declareCommand("hellothere", requireMonitored = true) {}
        handleMessage("e!hellothere", "1234", "5678", "90")
        coVerify { dcf.sendChannelMessage("5678", e) }
    }

    @Test
    fun `Accept commands from DMs if monitored server is not required`() = test("1234") {
        val c = it.declareCommandNoOp("hellothere", PermissionLevel.Anyone, requireMonitored = false)
        handleMessage("e!hellothere", "1234", "5678", null)
        coVerify { c.run("e!hellothere", "", null, "1234", "5678", null) }
    }


    @Test
    fun `Accept commands from unmonitored servers if monitored server is not required`() = test {
            val c = it.declareCommandNoOp("hellothere", PermissionLevel.Anyone, requireMonitored = false)
            handleMessage("e!hellothere", "1234", "5678", "90")
            coVerify { c.run("e!hellothere", "", null, "1234", "5678", "90") }
    }

    @Test
    fun `Do not accept messages from non-registered users if admin command`() = test("1234", "90") {
        val (dcf, e) = it.mockErrorMessage("cr.nr").mockSend(it, "5678")
        it.declareCommand("hellothere") {}
        handleMessage("e!hellothere", "1234", "5678", "90")
        coVerify { dcf.sendChannelMessage("5678", e) }
    }

    @Test
    fun `Do not accept messages from non-registered users if user command`() = test(null, "90") {
        val (dcf, e) = it.mockErrorMessage("cr.nr").mockSend(it, "5678")
        it.declareCommand("hellothere", PermissionLevel.User) {}
        handleMessage("e!hellothere", "1234", "5678", "90")
        coVerify { dcf.sendChannelMessage("5678", e) }
    }

    @Test
    fun `Do not accept messages from unidentified admins`() = test {
        val (dcf, e) = it.mockErrorMessage("cr.awni").mockSend(it, "5678")
        val u = mockk<User> { every { discordId } returns "1234" }

        test("1234", "90", u) {
            it.declareCommand("hellothere") {}
            handleMessage("e!hellothere", "1234", "5678", "90")
            coVerify { dcf.sendChannelMessage("5678", e) }
        }
    }

    @Test
    fun `Accept messages without command body`() {
        val u = mockk<User> { every { discordId } returns "1234" }
        test("1234", "90", u, true) {
            val c = it.declareCommandNoOp("hellothere")
            handleMessage("e!hellothere", "1234", "5678", "90")

            coVerify {
                c.run("e!hellothere", "", u, "1234", "5678", "90")
            }
        }
    }

    @Test
    fun `Accept messages with command body`() {
        val u = mockk<User> { every { discordId } returns "1234" }
        test("1234", "90", u, true) {
            val c = it.declareCommandNoOp("hellothere")
            handleMessage("e!hellothere this is my command's body", "1234", "5678", "90")
            coVerify {
                c.run("e!hellothere this is my command's body", "this is my command's body", u, "1234", "5678", "90")
            }
        }
    }

    @Test
    fun `Accept messages from users if user command`() {
        val u = mockk<User> { every { discordId } returns "1234" }
        test(null, "90", u) {
            val c = it.declareCommandNoOp("hellothere", PermissionLevel.User)
            handleMessage("e!hellothere", "1234", "5678", "90")
            coVerify {
                c.run("e!hellothere", "", u, "1234", "5678", "90")
            }
        }
    }

    @Test
    fun `Accept messages from unregistered user if anyone command`() {
        test(null, "90") {
            val c = it.declareCommandNoOp("hellothere", PermissionLevel.Anyone)
            handleMessage("e!hellothere", "1234", "5678", "90")
            coVerify {
                c.run("e!hellothere", "", null, "1234", "5678", "90")
            }
        }
    }

    private fun ContextBuilderDsl.declareCommandNoOp(
        name: String,
        permissionLevel: PermissionLevel = PermissionLevel.Admin,
        requireMonitored: Boolean = true
    ): Command =
        declareCommand(name, permissionLevel, requireMonitored) {
            coEvery { run(any(), any(), any(), any(), any(), any()) } just runs
        }

    private fun ContextBuilderDsl.declareCommand(
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
        put(named("discord.commands")) { listOf(c) }
        return c
    }

    @OptIn(UsesTrueIdentity::class)
    private fun test(
        admin: String? = null,
        server: String? = null,
        user: User? = null,
        identityAvailable: Boolean = false,
        block: suspend DiscordCommands.(UnsafeMutableEnvironment) -> Unit
    ): Unit = super.test({}) {
        val ume = this
        declareNoOpI18n()
        softPutMock<DiscordConfiguration> {
            every { commandsPrefix } returns "e!"
            if (server == null) {
                every { servers } returns listOf()
            } else {
                every { servers } returns listOf(
                    mockk {
                        every { id } returns server
                    }
                )
            }
        }
        softPutMock<DatabaseFacade> {
            coEvery { getUser(any()) } returns user
        }
        if (user != null) {
            softPutMock<PermissionChecks> {
                coEvery { canPerformAdminActions(user) } returns
                        if (identityAvailable) AdminStatus.Admin
                        else AdminStatus.AdminNotIdentifiable
            }
        }
        put(named("admins")) { if (admin == null) listOf() else listOf(admin) }
        runBlocking { block(subject, ume) }
    }

    private fun UnsafeMutableEnvironment.mockErrorMessage(i18nKey: String): DiscordEmbed =
        mockk<DiscordEmbed>().also {
            putMock<DiscordMessages> { every { getErrorCommandReply(any(), i18nKey) } returns it }
        }

    private fun DiscordEmbed.mockSend(env: UnsafeMutableEnvironment, channel: String) =
        env.softPutMock<DiscordClientFacade> {
            coEvery { sendChannelMessage(channel, this@mockSend) } returns ""
        } to this
}
