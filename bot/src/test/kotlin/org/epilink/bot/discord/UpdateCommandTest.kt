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
import guru.zoroark.shedinja.test.ShedinjaBaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.delay
import org.epilink.bot.db.User
import org.epilink.bot.discord.cmd.UpdateCommand
import org.epilink.bot.putMock
import org.epilink.bot.softPutMock
import org.epilink.bot.stest
import org.epilink.bot.web.declareNoOpI18n
import kotlin.test.Test
import kotlin.test.assertTrue

class UpdateCommandTest : ShedinjaBaseTest<Command>(
    Command::class, {
        put<Command> { UpdateCommand() }
    }
) {
    @Test
    fun `Test wrong target cannot be parsed`() = stest {
        putMock<DiscordTargets> { every { parseDiscordTarget("HELLO THERE") } returns TargetParseResult.Error }
        val embed = mockk<DiscordEmbed>()
        declareNoOpI18n()
        putMock<DiscordMessages> { every { getWrongTargetCommandReply(any(), "HELLO THERE") } returns embed }
        val f = putMock<DiscordClientFacade> { coEvery { sendChannelMessage("channel", embed) } returns "" }
        subject.run(
            fullCommand = "",
            commandBody = "HELLO THERE",
            // Unused
            sender = mockk(),
            senderId = "",
            channelId = "channel",
            guildId = ""
        )
        coVerify { f.sendChannelMessage("channel", embed) }
    }

    @Test
    fun `Test non existent role update`() = stest {
        val parsedTarget = TargetParseResult.Success.RoleByName("Rooole")
        val embed = mockk<DiscordEmbed>()
        putMock<DiscordTargets> {
            every { parseDiscordTarget("HELLO I AM COMMAND BODY") } returns parsedTarget
            coEvery { resolveDiscordTarget(parsedTarget, "guild") } returns TargetResult.RoleNotFound("Rooole")
        }
        declareNoOpI18n()
        putMock<DiscordMessages> {
            every {
                getWrongTargetCommandReply(
                    any(),
                    "HELLO I AM COMMAND BODY"
                )
            } returns embed
        }
        val f = putMock<DiscordClientFacade> { coEvery { sendChannelMessage("channel", embed) } returns "" }
        subject.run(
            fullCommand = "",
            commandBody = "HELLO I AM COMMAND BODY",
            sender = +"user",
            senderId = "user",
            channelId = "channel",
            guildId = "guild" // Unused
        )
        coVerify { f.sendChannelMessage("channel", embed) }
    }

    @Test
    fun `Test role update`() = stest {
        // Target resolution
        val parsedTarget = TargetParseResult.Success.RoleByName("Rooole")
        putMock<DiscordTargets> {
            every { parseDiscordTarget("HELLO I AM COMMAND BODY") } returns parsedTarget
            coEvery { resolveDiscordTarget(parsedTarget, "guild") } returns TargetResult.Role("Rooole ID")
        }
        // Role update
        val fakeList = listOf("a", "b", "c")
        putMock<DiscordClientFacade> {
            coEvery { getMembersWithRole("Rooole ID", "guild") } returns fakeList
        }
        val rm = putMock<RoleManager> {
            coEvery { invalidateAllRoles(any()) } just runs
        }
        // Message reply
        val embed = mockk<DiscordEmbed>()
        declareNoOpI18n()
        putMock<DiscordMessages> {
            every {
                getSuccessCommandReply(
                    any(),
                    any(),
                    listOf("Rooole ID", 3)
                )
            } returns embed
        }
        val f = softPutMock<DiscordClientFacade> {
            // (soft mock because already defined above)
            coEvery { sendChannelMessage("channel", embed) } returns "sentMessage"
            coEvery { addReaction("channel", "sentMessage", "✅") } just runs
        }
        subject.run(
            fullCommand = "",
            commandBody = "HELLO I AM COMMAND BODY",
            sender = +"user",
            senderId = "user",
            channelId = "channel",
            guildId = "guild" // unused
        )
        delay(2000)
        coVerify {
            f.sendChannelMessage("channel", embed)
            rm.invalidateAllRoles("a")
            rm.invalidateAllRoles("b")
            rm.invalidateAllRoles("c")
            f.addReaction("channel", "sentMessage", "✅")
        }
    }

    @Test
    fun `Test user update`() = stest {
        // Target resolution
        val parsedTarget = TargetParseResult.Success.UserById("targetid")
        putMock<DiscordTargets> {
            every { parseDiscordTarget("HELLO I AM COMMAND BODY") } returns parsedTarget
            coEvery { resolveDiscordTarget(parsedTarget, "guild") } returns TargetResult.User("targetid")
        }
        val rm = putMock<RoleManager> {
            coEvery { invalidateAllRoles("targetid") } just runs
        }
        // Message reply
        val embed = mockk<DiscordEmbed>()
        declareNoOpI18n()
        putMock<DiscordMessages> { every { getSuccessCommandReply(any(), any()) } returns embed }
        val f = putMock<DiscordClientFacade> {
            coEvery { sendChannelMessage("channel", embed) } returns "sentMessage"
            coEvery { addReaction("channel", "sentMessage", "✅") } just runs
        }
        subject.run(
            fullCommand = "",
            commandBody = "HELLO I AM COMMAND BODY",
            sender = +"user",
            senderId = "user",
            channelId = "channel",
            guildId = "guild" // unused
        )
        delay(2000)
        coVerify {
            f.sendChannelMessage("channel", embed)
            f.addReaction("channel", "sentMessage", "✅")
            rm.invalidateAllRoles("targetid")
        }
    }

    @Test
    fun `Test everyone update`() = stest {
        // Target resolution
        val parsedTarget = TargetParseResult.Success.Everyone
        putMock<DiscordTargets> {
            every { parseDiscordTarget("HELLO I AM COMMAND BODY") } returns parsedTarget
            coEvery { resolveDiscordTarget(parsedTarget, "guild") } returns TargetResult.Everyone
        }
        // Role update
        val fakeList = listOf("a", "b", "c")
        putMock<DiscordClientFacade> {
            coEvery { getMembers("guild") } returns fakeList
        }
        val rm = putMock<RoleManager> {
            coEvery { invalidateAllRoles(any()) } just runs
        }
        // Message reply
        val embed = mockk<DiscordEmbed>()
        declareNoOpI18n()
        putMock<DiscordMessages> { every { getSuccessCommandReply(any(), any(), listOf(3)) } returns embed }
        val f = softPutMock<DiscordClientFacade> { // (soft mock because already defined above)
            coEvery { sendChannelMessage("channel", embed) } returns "sentMessage"
            coEvery { addReaction("channel", "sentMessage", "✅") } just runs
        }
        subject.run(
            fullCommand = "",
            commandBody = "HELLO I AM COMMAND BODY",
            sender = +"user",
            senderId = "user",
            channelId = "channel",
            guildId = "guild" // unused
        )
        delay(2000)
        coVerify {
            f.sendChannelMessage("channel", embed)
            rm.invalidateAllRoles("a")
            rm.invalidateAllRoles("b")
            rm.invalidateAllRoles("c")
            f.addReaction("channel", "sentMessage", "✅")
        }
    }

    @Test
    fun `Test one update crash does not crash everything else`() = stest {
        // Target resolution
        val parsedTarget = TargetParseResult.Success.Everyone
        putMock<DiscordTargets> {
            every { parseDiscordTarget("HELLO I AM COMMAND BODY") } returns parsedTarget
            coEvery { resolveDiscordTarget(parsedTarget, "guild") } returns TargetResult.Everyone
        }
        // Role update
        val fakeList = List(26) { ('a' + it).toString() }
        putMock<DiscordClientFacade> {
            coEvery { getMembers("guild") } returns fakeList
        }
        var eFailed = false
        val rm = putMock<RoleManager> {
            coEvery { invalidateAllRoles(any()) } just runs
            coEvery { invalidateAllRoles("e") } answers { eFailed = true; error("oh no") }
        }
        // Message reply
        val embed = mockk<DiscordEmbed>()
        declareNoOpI18n()
        putMock<DiscordMessages> { every { getSuccessCommandReply(any(), any(), listOf(26)) } returns embed }
        val f = softPutMock<DiscordClientFacade> { // (soft mock because already defined above)
            coEvery { sendChannelMessage("channel", embed) } returns "sentMessage"
            coEvery { addReaction("channel", "sentMessage", "✅") } just runs
            coEvery { addReaction("channel", "sentMessage", "⚠") } just runs
        }
        subject.run(
            fullCommand = "",
            commandBody = "HELLO I AM COMMAND BODY",
            sender = +"user",
            senderId = "user",
            channelId = "channel",
            guildId = "guild" // unused
        )
        delay(2000)
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
