/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.commands

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import org.epilink.backend.KoinBaseTest
import org.epilink.backend.config.DiscordEmbed
import org.epilink.backend.db.User
import org.epilink.backend.discord.DiscordClientFacade
import org.epilink.backend.mockHere
import org.epilink.backend.services.Command
import org.epilink.backend.services.DiscordMessages
import org.epilink.backend.services.DiscordTargets
import org.epilink.backend.services.RoleManager
import org.epilink.backend.services.TargetParseResult
import org.epilink.backend.services.TargetResult
import org.epilink.backend.softMockHere
import org.epilink.backend.http.declareNoOpI18n
import org.koin.dsl.module

class UpdateCommandTest : KoinBaseTest<Command>(
    Command::class,
    module {
        single<Command> { UpdateCommand() }
    }
) {
    @Test
    fun `Test wrong target cannot be parsed`() {
        mockHere<DiscordTargets> { every { parseDiscordTarget("HELLO THERE") } returns TargetParseResult.Error }
        val embed = mockk<DiscordEmbed>()
        declareNoOpI18n()
        mockHere<DiscordMessages> { every { getWrongTargetCommandReply(any(), "HELLO THERE") } returns embed }
        val f = mockHere<DiscordClientFacade> { coEvery { sendChannelMessage("channel", embed) } returns "" }
        test {
            run(
                fullCommand = "",
                commandBody = "HELLO THERE",
                // Unused
                sender = mockk(),
                senderId = "",
                channelId = "channel",
                guildId = ""
            )
        }
        coVerify { f.sendChannelMessage("channel", embed) }
    }

    @Test
    fun `Test non existent role update`() {
        val parsedTarget = TargetParseResult.Success.RoleByName("Rooole")
        val embed = mockk<DiscordEmbed>()
        mockHere<DiscordTargets> {
            every { parseDiscordTarget("HELLO I AM COMMAND BODY") } returns parsedTarget
            coEvery { resolveDiscordTarget(parsedTarget, "guild") } returns TargetResult.RoleNotFound("Rooole")
        }
        declareNoOpI18n()
        mockHere<DiscordMessages> {
            every {
                getWrongTargetCommandReply(
                    any(),
                    "HELLO I AM COMMAND BODY"
                )
            } returns embed
        }
        val f = mockHere<DiscordClientFacade> { coEvery { sendChannelMessage("channel", embed) } returns "" }
        test {
            run(
                fullCommand = "",
                commandBody = "HELLO I AM COMMAND BODY",
                sender = +"user",
                senderId = "user",
                channelId = "channel",
                guildId = "guild" // Unused
            )
        }
        coVerify { f.sendChannelMessage("channel", embed) }
    }

    @Test
    fun `Test role update`() {
        // Target resolution
        val parsedTarget = TargetParseResult.Success.RoleByName("Rooole")
        mockHere<DiscordTargets> {
            every { parseDiscordTarget("HELLO I AM COMMAND BODY") } returns parsedTarget
            coEvery { resolveDiscordTarget(parsedTarget, "guild") } returns TargetResult.Role("Rooole ID")
        }
        // Role update
        val fakeList = listOf("a", "b", "c")
        mockHere<DiscordClientFacade> {
            coEvery { getMembersWithRole("Rooole ID", "guild") } returns fakeList
        }
        val rm = mockHere<RoleManager> {
            coEvery { invalidateAllRoles(any()) } just runs
        }
        // Message reply
        val embed = mockk<DiscordEmbed>()
        declareNoOpI18n()
        mockHere<DiscordMessages> { every { getSuccessCommandReply(any(), any(), listOf("Rooole ID", 3)) } returns embed }
        val f = softMockHere<DiscordClientFacade> {
            // (soft mock because already defined above)
            coEvery { sendChannelMessage("channel", embed) } returns "sentMessage"
            coEvery { addReaction("channel", "sentMessage", "✅") } just runs
        }
        test {
            run(
                fullCommand = "",
                commandBody = "HELLO I AM COMMAND BODY",
                sender = +"user",
                senderId = "user",
                channelId = "channel",
                guildId = "guild" // unused
            )
            delay(2000)
        }
        coVerify {
            f.sendChannelMessage("channel", embed)
            rm.invalidateAllRoles("a")
            rm.invalidateAllRoles("b")
            rm.invalidateAllRoles("c")
            f.addReaction("channel", "sentMessage", "✅")
        }
    }

    @Test
    fun `Test user update`() {
        // Target resolution
        val parsedTarget = TargetParseResult.Success.UserById("targetid")
        mockHere<DiscordTargets> {
            every { parseDiscordTarget("HELLO I AM COMMAND BODY") } returns parsedTarget
            coEvery { resolveDiscordTarget(parsedTarget, "guild") } returns TargetResult.User("targetid")
        }
        val rm = mockHere<RoleManager> {
            coEvery { invalidateAllRoles("targetid") } just runs
        }
        // Message reply
        val embed = mockk<DiscordEmbed>()
        declareNoOpI18n()
        mockHere<DiscordMessages> { every { getSuccessCommandReply(any(), any()) } returns embed }
        val f = mockHere<DiscordClientFacade> {
            coEvery { sendChannelMessage("channel", embed) } returns "sentMessage"
            coEvery { addReaction("channel", "sentMessage", "✅") } just runs
        }
        test {
            run(
                fullCommand = "",
                commandBody = "HELLO I AM COMMAND BODY",
                sender = +"user",
                senderId = "user",
                channelId = "channel",
                guildId = "guild" // unused
            )
            delay(2000)
        }
        coVerify {
            f.sendChannelMessage("channel", embed)
            f.addReaction("channel", "sentMessage", "✅")
            rm.invalidateAllRoles("targetid")
        }
    }

    @Test
    fun `Test everyone update`() {
        // Target resolution
        val parsedTarget = TargetParseResult.Success.Everyone
        mockHere<DiscordTargets> {
            every { parseDiscordTarget("HELLO I AM COMMAND BODY") } returns parsedTarget
            coEvery { resolveDiscordTarget(parsedTarget, "guild") } returns TargetResult.Everyone
        }
        // Role update
        val fakeList = listOf("a", "b", "c")
        mockHere<DiscordClientFacade> {
            coEvery { getMembers("guild") } returns fakeList
        }
        val rm = mockHere<RoleManager> {
            coEvery { invalidateAllRoles(any()) } just runs
        }
        // Message reply
        val embed = mockk<DiscordEmbed>()
        declareNoOpI18n()
        mockHere<DiscordMessages> { every { getSuccessCommandReply(any(), any(), listOf(3)) } returns embed }
        val f = softMockHere<DiscordClientFacade> { // (soft mock because already defined above)
            coEvery { sendChannelMessage("channel", embed) } returns "sentMessage"
            coEvery { addReaction("channel", "sentMessage", "✅") } just runs
        }
        test {
            run(
                fullCommand = "",
                commandBody = "HELLO I AM COMMAND BODY",
                sender = +"user",
                senderId = "user",
                channelId = "channel",
                guildId = "guild" // unused
            )
            delay(2000)
        }
        coVerify {
            f.sendChannelMessage("channel", embed)
            rm.invalidateAllRoles("a")
            rm.invalidateAllRoles("b")
            rm.invalidateAllRoles("c")
            f.addReaction("channel", "sentMessage", "✅")
        }
    }

    @Test
    fun `Test one update crash does not crash everything else`() {
        // Target resolution
        val parsedTarget = TargetParseResult.Success.Everyone
        mockHere<DiscordTargets> {
            every { parseDiscordTarget("HELLO I AM COMMAND BODY") } returns parsedTarget
            coEvery { resolveDiscordTarget(parsedTarget, "guild") } returns TargetResult.Everyone
        }
        // Role update
        val fakeList = List(26) { ('a' + it).toString() }
        mockHere<DiscordClientFacade> {
            coEvery { getMembers("guild") } returns fakeList
        }
        var eFailed = false
        val rm = mockHere<RoleManager> {
            coEvery { invalidateAllRoles(any()) } just runs
            coEvery { invalidateAllRoles("e") } answers { eFailed = true; error("oh no") }
        }
        // Message reply
        val embed = mockk<DiscordEmbed>()
        declareNoOpI18n()
        mockHere<DiscordMessages> { every { getSuccessCommandReply(any(), any(), listOf(26)) } returns embed }
        val f = softMockHere<DiscordClientFacade> { // (soft mock because already defined above)
            coEvery { sendChannelMessage("channel", embed) } returns "sentMessage"
            coEvery { addReaction("channel", "sentMessage", "✅") } just runs
            coEvery { addReaction("channel", "sentMessage", "⚠") } just runs
        }
        test {
            run(
                fullCommand = "",
                commandBody = "HELLO I AM COMMAND BODY",
                sender = +"user",
                senderId = "user",
                channelId = "channel",
                guildId = "guild" // unused
            )
            delay(2000)
        }
        assertTrue(eFailed, "The e discordId did not throw")
        coVerify {
            f.sendChannelMessage("channel", embed)
            for (c in 'a'..'z')
                rm.invalidateAllRoles(c.toString())
            f.addReaction("channel", "sentMessage", "✅")
            f.addReaction("channel", "sentMessage", "⚠")
        }
    }
}

operator fun String.unaryPlus(): User = mockk {
    every { discordId } returns this@unaryPlus
}
