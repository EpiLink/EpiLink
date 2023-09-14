/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import guru.zoroark.tegral.di.dsl.put
import guru.zoroark.tegral.di.environment.named
import guru.zoroark.tegral.di.test.TegralSubjectTest
import guru.zoroark.tegral.di.test.TestMutableInjectionEnvironment
import guru.zoroark.tegral.di.test.mockk.putMock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.epilink.bot.config.DiscordConfiguration
import org.epilink.bot.db.AdminStatus
import org.epilink.bot.db.DatabaseFacade
import org.epilink.bot.db.PermissionChecks
import org.epilink.bot.db.User
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.putMockOrApply
import org.epilink.bot.web.declareNoOpI18n
import kotlin.test.Test

class DiscordCommandsTest : TegralSubjectTest<DiscordCommands>(
    DiscordCommands::class,
    { put<DiscordCommands>(::DiscordCommandsImpl) }
) {
    // FIXME il manque probablement des setupMocks ?
    @Test
    fun `Do not accept prefix-less messages`() = test {
        setupMocks()
        // No assertions, as this will crash anyway if anything is called
        subject.handleMessage("The prefix ain't here", "1234", "5678", "90")
    }

    @Test
    fun `Do not accept messages from non-admins if admin-only command`() = test {
        val (dcf, e) = mockErrorMessage("cr.nan", "5678")
        declareCommand("hellothere") {}
        setupMocks(server = "90")
        subject.handleMessage("e!hellothere", "1234", "5678", "90")
        coVerify { dcf.sendChannelMessage("5678", e) }
    }

    @Test
    fun `Do not accept commands from unmonitored servers if monitored server is required`() = test {
        val (dcf, e) = mockErrorMessage("cr.snm", "5678")
        declareCommand("hellothere", requireMonitored = true) {}
        setupMocks("1234")
        subject.handleMessage("e!hellothere", "1234", "5678", "90")
        coVerify { dcf.sendChannelMessage("5678", e) }
    }

    @Test
    fun `Accept commands from DMs if monitored server is not required`() = test {
        val c = declareCommandNoOp("hellothere", PermissionLevel.Anyone, requireMonitored = false)
        setupMocks("1234")
        subject.handleMessage("e!hellothere", "1234", "5678", null)
        coVerify { c.run("e!hellothere", "", null, "1234", "5678", null) }
    }

    @Test
    fun `Accept commands from unmonitored servers if monitored server is not required`() = test {
        setupMocks()
        val c = declareCommandNoOp("hellothere", PermissionLevel.Anyone, requireMonitored = false)
        subject.handleMessage("e!hellothere", "1234", "5678", "90")
        coVerify { c.run("e!hellothere", "", null, "1234", "5678", "90") }
    }

    @Test
    fun `Do not accept messages from non-registered users if admin command`() = test {
        val (dcf, e) = mockErrorMessage("cr.nr", "5678")
        declareCommand("hellothere") {}
        setupMocks("1234", "90")
        subject.handleMessage("e!hellothere", "1234", "5678", "90")
        coVerify { dcf.sendChannelMessage("5678", e) }
    }

    @Test
    fun `Do not accept messages from non-registered users if user command`() = test {
        val (dcf, e) = mockErrorMessage("cr.nr", "5678")
        declareCommand("hellothere", PermissionLevel.User) {}
        setupMocks(null, "90")
        subject.handleMessage("e!hellothere", "1234", "5678", "90")
        coVerify { dcf.sendChannelMessage("5678", e) }
    }

    @Test
    fun `Do not accept messages from unidentified admins`() = test {
        val (dcf, e) = mockErrorMessage("cr.awni", "5678")
        val u = mockk<User> { every { discordId } returns "1234" }
        declareCommand("hellothere") {}
        setupMocks("1234", "90", u)
        subject.handleMessage("e!hellothere", "1234", "5678", "90")
        coVerify { dcf.sendChannelMessage("5678", e) }
    }

    @Test
    fun `Accept messages without command body`() = test {
        val c = declareCommandNoOp("hellothere")
        val u = mockk<User> { every { discordId } returns "1234" }
        setupMocks("1234", "90", u, true)
        subject.handleMessage("e!hellothere", "1234", "5678", "90")
        coVerify {
            c.run("e!hellothere", "", u, "1234", "5678", "90")
        }
    }

    @Test
    fun `Accept messages with command body`() = test {
        val c = declareCommandNoOp("hellothere")
        val u = mockk<User> { every { discordId } returns "1234" }
        setupMocks("1234", "90", u, true)
        subject.handleMessage("e!hellothere this is my command's body", "1234", "5678", "90")
        coVerify {
            c.run("e!hellothere this is my command's body", "this is my command's body", u, "1234", "5678", "90")
        }
    }

    @Test
    fun `Accept messages from users if user command`() = test {
        val c = declareCommandNoOp("hellothere", PermissionLevel.User)
        val u = mockk<User> { every { discordId } returns "1234" }
        setupMocks(null, "90", u)
        subject.handleMessage("e!hellothere", "1234", "5678", "90")
        coVerify {
            c.run("e!hellothere", "", u, "1234", "5678", "90")
        }
    }

    @Test
    fun `Accept messages from unregistered user if anyone command`() = test {
        val c = declareCommandNoOp("hellothere", PermissionLevel.Anyone)
        setupMocks(null, "90")
        subject.handleMessage("e!hellothere", "1234", "5678", "90")
        coVerify {
            c.run("e!hellothere", "", null, "1234", "5678", "90")
        }
    }

    private fun TestMutableInjectionEnvironment.declareCommandNoOp(
        name: String,
        permissionLevel: PermissionLevel = PermissionLevel.Admin,
        requireMonitored: Boolean = true
    ): Command =
        declareCommand(name, permissionLevel, requireMonitored) {
            coEvery { run(any(), any(), any(), any(), any(), any()) } just runs
        }

    private fun TestMutableInjectionEnvironment.declareCommand(
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
    private fun TestMutableInjectionEnvironment.setupMocks(
        admin: String? = null,
        server: String? = null,
        user: User? = null,
        identityAvailable: Boolean = false
    ) {
        declareNoOpI18n()
        putMockOrApply<DiscordConfiguration> {
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
        putMockOrApply<DatabaseFacade> {
            coEvery { getUser(any()) } returns user
        }
        if (user != null) {
            putMockOrApply<PermissionChecks> {
                coEvery { canPerformAdminActions(user) } returns
                    if (identityAvailable) AdminStatus.Admin else AdminStatus.AdminNotIdentifiable
            }
        }
        put(named("admins")) { if (admin == null) listOf() else listOf(admin) }
    }

    private fun TestMutableInjectionEnvironment.mockErrorMessage(
        i18nKey: String,
        sendToChannel: String
    ): Pair<DiscordClientFacade, DiscordEmbed> {
        val discordEmbed = mockk<DiscordEmbed>()
        putMock<DiscordMessages> { every { getErrorCommandReply(any(), i18nKey) } returns discordEmbed }
        val dcf = putMockOrApply<DiscordClientFacade> {
            coEvery {
                sendChannelMessage(
                    sendToChannel,
                    discordEmbed
                )
            } returns ""
        }
        return dcf to discordEmbed
    }
}
