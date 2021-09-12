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
import guru.zoroark.shedinja.test.UnsafeMutableEnvironment
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.epilink.bot.discord.cmd.CountCommand
import org.epilink.bot.putMock
import org.epilink.bot.softPutMock
import org.epilink.bot.stest
import org.epilink.bot.web.declareNoOpI18n
import kotlin.test.Test

class CountCommandTest : ShedinjaBaseTest<Command>(Command::class, { put<Command>(::CountCommand) }) {
    private fun UnsafeMutableEnvironment.mockMessagePipeline(
        channelId: String,
        discordMessagesReceiver: MockKMatcherScope.(DiscordMessages) -> DiscordEmbed
    ): Pair<DiscordClientFacade, DiscordEmbed> {
        val embed = mockk<DiscordEmbed>()
        putMock<DiscordMessages> {
            every { discordMessagesReceiver(this@putMock) } returns embed
        }
        declareNoOpI18n()
        val f = putMock<DiscordClientFacade> {
            coEvery { sendChannelMessage(channelId, embed) } returns ""
        }
        return f to embed
    }

    @Test
    fun `Test wrong target command`() = stest {
        putMock<DiscordTargets> {
            every { parseDiscordTarget("OWO OWO OWO") } returns TargetParseResult.Error
        }
        val (f, embed) = mockMessagePipeline("channel") { it.getWrongTargetCommandReply(any(), "OWO OWO OWO") }
        subject.run(
            fullCommand = "",
            commandBody = "OWO OWO OWO",
            sender = mockk(),
            senderId = "",
            channelId = "channel",
            guildId = ""
        )
        coVerify { f.sendChannelMessage("channel", embed) }
    }

    @Test
    fun `Test correct target command on everyone`() = stest {
        val members = ('a'..'j').map { it.toString() }.toList()
        putMock<DiscordTargets> {
            every { parseDiscordTarget("UWU UWU UWU") } returns TargetParseResult.Success.Everyone
            coEvery {
                resolveDiscordTarget(
                    TargetParseResult.Success.Everyone,
                    "the_guild"
                )
            } returns TargetResult.Everyone
        }
        val (_, embed) = mockMessagePipeline("the_channel") {
            it.getSuccessCommandReply(
                any(),
                "count.success",
                listOf(10)
            )
        }
        val f = softPutMock<DiscordClientFacade> {
            coEvery { getMembers("the_guild") } returns members
        }
        subject.run(
            fullCommand = "",
            commandBody = "UWU UWU UWU",
            sender = +"the_user",
            senderId = "the_user",
            channelId = "the_channel",
            guildId = "the_guild"
        )
        coVerify { f.sendChannelMessage("the_channel", embed) }
    }

    @Test
    fun `Test correct target command on role name`() = stest {
        val members = ('a'..'j').map { it.toString() }.toList()
        putMock<DiscordTargets> {
            every { parseDiscordTarget("UWU UWU UWU") } returns TargetParseResult.Success.RoleByName("The Role")
            coEvery {
                resolveDiscordTarget(
                    TargetParseResult.Success.RoleByName("The Role"),
                    "the_guild"
                )
            } returns TargetResult.Role("The Role Id")
        }
        val (_, embed) = mockMessagePipeline("the_channel") {
            it.getSuccessCommandReply(
                any(),
                "count.success",
                listOf(10)
            )
        }
        val f = softPutMock<DiscordClientFacade> {
            coEvery { getMembersWithRole("The Role Id", "the_guild") } returns members
        }
        subject.run(
            fullCommand = "",
            commandBody = "UWU UWU UWU",
            sender = +"the_user",
            senderId = "the_user",
            channelId = "the_channel",
            guildId = "the_guild"
        )
        coVerify { f.sendChannelMessage("the_channel", embed) }
    }

    @Test
    fun `Test correct target command on role ID`() = stest {
        val members = ('a'..'j').map { it.toString() }.toList()
        putMock<DiscordTargets> {
            every { parseDiscordTarget("UWU UWU UWU") } returns TargetParseResult.Success.RoleById("The Role Id")
            coEvery {
                resolveDiscordTarget(
                    TargetParseResult.Success.RoleById("The Role Id"),
                    "the_guild"
                )
            } returns TargetResult.Role("The Role Id")
        }
        val (_, embed) = mockMessagePipeline("the_channel") {
            it.getSuccessCommandReply(
                any(),
                "count.success",
                listOf(10)
            )
        }
        val f = softPutMock<DiscordClientFacade> {
            coEvery { getMembersWithRole("The Role Id", "the_guild") } returns members
        }
        subject.run(
            fullCommand = "",
            commandBody = "UWU UWU UWU",
            sender = +"the_user",
            senderId = "the_user",
            channelId = "the_channel",
            guildId = "the_guild"
        )
        coVerify { f.sendChannelMessage("the_channel", embed) }
    }

    @Test
    fun `Test correct target command on single user in guild`() = stest {
        putMock<DiscordTargets> {
            every { parseDiscordTarget("UWU UWU UWU") } returns TargetParseResult.Success.UserById("The User")
            coEvery {
                resolveDiscordTarget(
                    TargetParseResult.Success.UserById("The User"),
                    "the_guild"
                )
            } returns TargetResult.User("The User")
        }
        val (_, embed) = mockMessagePipeline("the_channel") {
            it.getSuccessCommandReply(
                any(),
                "count.success",
                listOf(1)
            )
        }
        val f = softPutMock<DiscordClientFacade> {
            coEvery { isUserInGuild("The User", "the_guild") } returns true
        }
        subject.run(
            fullCommand = "",
            commandBody = "UWU UWU UWU",
            sender = +"the_user",
            senderId = "the_user",
            channelId = "the_channel",
            guildId = "the_guild"
        )
        coVerify { f.sendChannelMessage("the_channel", embed) }
    }

    @Test
    fun `Test correct target command on single user not in guild`() = stest {
        putMock<DiscordTargets> {
            every { parseDiscordTarget("UWU UWU UWU") } returns TargetParseResult.Success.UserById("The User")
            coEvery {
                resolveDiscordTarget(
                    TargetParseResult.Success.UserById("The User"),
                    "the_guild"
                )
            } returns TargetResult.User("The User")
        }
        val (_, embed) = mockMessagePipeline("the_channel") { it.getWrongTargetCommandReply(any(), "UWU UWU UWU") }
        val f = softPutMock<DiscordClientFacade> {
            coEvery { isUserInGuild("The User", "the_guild") } returns false
        }
        subject.run(
            fullCommand = "",
            commandBody = "UWU UWU UWU",
            sender = +"the_user",
            senderId = "the_user",
            channelId = "the_channel",
            guildId = "the_guild"
        )

        coVerify { f.sendChannelMessage("the_channel", embed) }
    }
}
