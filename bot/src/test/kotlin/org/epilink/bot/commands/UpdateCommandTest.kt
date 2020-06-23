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
import org.epilink.bot.db.LinkUser
import org.epilink.bot.discord.*
import org.epilink.bot.discord.cmd.UpdateCommand
import org.epilink.bot.mockHere
import org.epilink.bot.softMockHere
import org.koin.core.get
import org.koin.dsl.module
import kotlin.test.*

class UpdateCommandTest : KoinBaseTest(
    module {
        single<Command> { UpdateCommand() }
    }
) {
    @Test
    fun `Test wrong target cannot be parsed`() {
        mockHere<LinkDiscordTargets> { every { parseDiscordTarget("HELLO THERE") } returns TargetParseResult.Error }
        val embed = mockk<DiscordEmbed>()
        mockHere<LinkDiscordMessages> { every { getWrongTargetCommandReply("HELLO THERE") } returns embed }
        val f = mockHere<LinkDiscordClientFacade> { coEvery { sendChannelMessage("channel", embed) } just runs }
        val ctx = mockContext(channelId = "channel", commandBody = "HELLO THERE")
        test { ctx.run() }
        coVerify { f.sendChannelMessage("channel", embed) }
    }

    @Test
    fun `Test non existent role update`() {
        val parsedTarget = TargetParseResult.Success.RoleByName("Rooole")
        val embed = mockk<DiscordEmbed>()
        mockHere<LinkDiscordTargets> {
            every { parseDiscordTarget("HELLO I AM COMMAND BODY") } returns parsedTarget
            coEvery { resolveDiscordTarget(parsedTarget, "guild") } returns TargetResult.RoleNotFound("Rooole")
        }
        mockHere<LinkDiscordMessages> { every { getWrongTargetCommandReply("HELLO I AM COMMAND BODY") } returns embed }
        val f = mockHere<LinkDiscordClientFacade> { coEvery { sendChannelMessage("channel", embed) } just runs }
        val ctx = mockContext(
            channelId = "channel",
            commandBody = "HELLO I AM COMMAND BODY",
            guildId = "guild",
            sender = +"user"
        )
        test { ctx.run() }
        coVerify { f.sendChannelMessage("channel", embed) }
    }

    @Test
    fun `Test role update`() {
        // Target resolution
        val parsedTarget = TargetParseResult.Success.RoleByName("Rooole")
        mockHere<LinkDiscordTargets> {
            every { parseDiscordTarget("HELLO I AM COMMAND BODY") } returns parsedTarget
            coEvery { resolveDiscordTarget(parsedTarget, "guild") } returns TargetResult.Role("Rooole ID")
        }
        // Role update
        val fakeList = listOf("a", "b", "c")
        mockHere<LinkDiscordClientFacade> {
            coEvery { getMembersWithRole("Rooole ID", "guild") } returns fakeList
        }
        val rm = mockHere<LinkRoleManager> {
            coEvery { invalidateAllRoles(any()) } returns mockk()
        }
        // Message reply
        val embed = mockk<DiscordEmbed>()
        mockHere<LinkDiscordMessages> { every { getSuccessCommandReply(any()) } returns embed }
        val f = softMockHere<LinkDiscordClientFacade> { coEvery { sendChannelMessage("channel", embed) } just runs }
        // (soft mock because already defined above)
        val ctx = mockContext(
            channelId = "channel",
            commandBody = "HELLO I AM COMMAND BODY",
            guildId = "guild",
            sender = +"user"
        )
        test { ctx.run() }
        coVerify {
            f.sendChannelMessage("channel", embed)
            rm.invalidateAllRoles("a")
            rm.invalidateAllRoles("b")
            rm.invalidateAllRoles("c")
        }
    }

    @Test
    fun `Test user update`() {
        // Target resolution
        val parsedTarget = TargetParseResult.Success.UserById("targetid")
        mockHere<LinkDiscordTargets> {
            every { parseDiscordTarget("HELLO I AM COMMAND BODY") } returns parsedTarget
            coEvery { resolveDiscordTarget(parsedTarget, "guild") } returns TargetResult.User("targetid")
        }
        val rm = mockHere<LinkRoleManager> {
            coEvery { invalidateAllRoles("targetid") } returns mockk()
        }
        // Message reply
        val embed = mockk<DiscordEmbed>()
        mockHere<LinkDiscordMessages> { every { getSuccessCommandReply(any()) } returns embed }
        val f = softMockHere<LinkDiscordClientFacade> { coEvery { sendChannelMessage("channel", embed) } just runs }
        // (soft mock because already defined above)
        val ctx = mockContext(
            channelId = "channel",
            commandBody = "HELLO I AM COMMAND BODY",
            guildId = "guild",
            sender = +"user"
        )
        test { ctx.run() }
        coVerify {
            f.sendChannelMessage("channel", embed)
            rm.invalidateAllRoles("targetid")
        }
    }

    @Test
    fun `Test everyone update`() {
        // Target resolution
        val parsedTarget = TargetParseResult.Success.Everyone
        mockHere<LinkDiscordTargets> {
            every { parseDiscordTarget("HELLO I AM COMMAND BODY") } returns parsedTarget
            coEvery { resolveDiscordTarget(parsedTarget, "guild") } returns TargetResult.Everyone
        }
        // Role update
        val fakeList = listOf("a", "b", "c")
        mockHere<LinkDiscordClientFacade> {
            coEvery { getMembers("guild") } returns fakeList
        }
        val rm = mockHere<LinkRoleManager> {
            coEvery { invalidateAllRoles(any()) } returns mockk()
        }
        // Message reply
        val embed = mockk<DiscordEmbed>()
        mockHere<LinkDiscordMessages> { every { getSuccessCommandReply(any()) } returns embed }
        val f = softMockHere<LinkDiscordClientFacade> { coEvery { sendChannelMessage("channel", embed) } just runs }
        // (soft mock because already defined above)
        val ctx = mockContext(
            channelId = "channel",
            commandBody = "HELLO I AM COMMAND BODY",
            guildId = "guild",
            sender = +"user"
        )
        test { ctx.run() }
        coVerify {
            f.sendChannelMessage("channel", embed)
            rm.invalidateAllRoles("a")
            rm.invalidateAllRoles("b")
            rm.invalidateAllRoles("c")
        }
    }

    private operator fun String.unaryPlus(): LinkUser = mockk {
        every { discordId } returns this@unaryPlus
    }

    private fun test(block: suspend Command.() -> Unit) {
        runBlocking { get<Command>().block() }
    }

    private fun mockContext(
        commandBody: String? = null,
        fullCommand: String? = null,
        sender: LinkUser? = null,
        channelId: String? = null,
        guildId: String? = null
    ): CommandContext = mockk {
        commandBody?.let { every { this@mockk.commandBody } returns it }
        fullCommand?.let { every { this@mockk.fullCommand } returns it }
        sender?.let { every { this@mockk.sender } returns it }
        channelId?.let { every { this@mockk.channelId } returns it }
        guildId?.let { every { this@mockk.guildId } returns it }
    }
}
