/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.commands

import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import org.epilink.backend.KoinBaseTest
import org.epilink.backend.config.DiscordEmbed
import org.epilink.backend.discord.DiscordClientFacade
import org.epilink.backend.mockHere
import org.epilink.backend.services.Command
import org.epilink.backend.services.DiscordMessages
import org.epilink.backend.services.DiscordTargets
import org.epilink.backend.services.TargetParseResult
import org.epilink.backend.services.TargetResult
import org.epilink.backend.softMockHere
import org.epilink.backend.http.declareNoOpI18n
import org.koin.dsl.module

class CountCommandTest : KoinBaseTest<Command>(
    Command::class,
    module {
        single<Command> { CountCommand() }
    }
) {
    private fun mockMessagePipeline(
        channelId: String,
        discordMessagesReceiver: MockKMatcherScope.(DiscordMessages) -> DiscordEmbed
    ): Pair<DiscordClientFacade, DiscordEmbed> {
        val embed = mockk<DiscordEmbed>()
        mockHere<DiscordMessages> {
            every { discordMessagesReceiver(this@mockHere) } returns embed
        }
        declareNoOpI18n()
        val f = mockHere<DiscordClientFacade> {
            coEvery { sendChannelMessage(channelId, embed) } returns ""
        }
        return f to embed
    }

    @Test
    fun `Test wrong target command`() {
        mockHere<DiscordTargets> {
            every { parseDiscordTarget("OWO OWO OWO") } returns TargetParseResult.Error
        }
        val (f, embed) = mockMessagePipeline("channel") { it.getWrongTargetCommandReply(any(), "OWO OWO OWO") }
        test {
            run(
                fullCommand = "",
                commandBody = "OWO OWO OWO",
                sender = mockk(),
                senderId = "",
                channelId = "channel",
                guildId = ""
            )
        }
        coVerify { f.sendChannelMessage("channel", embed) }
    }

    @Test
    fun `Test correct target command on everyone`() {
        val members = ('a'..'j').map { it.toString() }.toList()
        mockHere<DiscordTargets> {
            every { parseDiscordTarget("UWU UWU UWU") } returns TargetParseResult.Success.Everyone
            coEvery {
                resolveDiscordTarget(
                    TargetParseResult.Success.Everyone,
                    "the_guild"
                )
            } returns TargetResult.Everyone
        }
        val (_, embed) = mockMessagePipeline("the_channel") { it.getSuccessCommandReply(any(), "count.success", listOf(10)) }
        val f = softMockHere<DiscordClientFacade> {
            coEvery { getMembers("the_guild") } returns members
        }
        test {
            run(
                fullCommand = "",
                commandBody = "UWU UWU UWU",
                sender = +"the_user",
                senderId = "the_user",
                channelId = "the_channel",
                guildId = "the_guild"
            )
        }
        coVerify { f.sendChannelMessage("the_channel", embed) }
    }

    @Test
    fun `Test correct target command on role name`() {
        val members = ('a'..'j').map { it.toString() }.toList()
        mockHere<DiscordTargets> {
            every { parseDiscordTarget("UWU UWU UWU") } returns TargetParseResult.Success.RoleByName("The Role")
            coEvery {
                resolveDiscordTarget(
                    TargetParseResult.Success.RoleByName("The Role"),
                    "the_guild"
                )
            } returns TargetResult.Role("The Role Id")
        }
        val (_, embed) = mockMessagePipeline("the_channel") { it.getSuccessCommandReply(any(), "count.success", listOf(10)) }
        val f = softMockHere<DiscordClientFacade> {
            coEvery { getMembersWithRole("The Role Id", "the_guild") } returns members
        }
        test {
            run(
                fullCommand = "",
                commandBody = "UWU UWU UWU",
                sender = +"the_user",
                senderId = "the_user",
                channelId = "the_channel",
                guildId = "the_guild"
            )
        }
        coVerify { f.sendChannelMessage("the_channel", embed) }
    }

    @Test
    fun `Test correct target command on role ID`() {
        val members = ('a'..'j').map { it.toString() }.toList()
        mockHere<DiscordTargets> {
            every { parseDiscordTarget("UWU UWU UWU") } returns TargetParseResult.Success.RoleById("The Role Id")
            coEvery {
                resolveDiscordTarget(
                    TargetParseResult.Success.RoleById("The Role Id"),
                    "the_guild"
                )
            } returns TargetResult.Role("The Role Id")
        }
        val (_, embed) = mockMessagePipeline("the_channel") { it.getSuccessCommandReply(any(), "count.success", listOf(10)) }
        val f = softMockHere<DiscordClientFacade> {
            coEvery { getMembersWithRole("The Role Id", "the_guild") } returns members
        }
        test {
            run(
                fullCommand = "",
                commandBody = "UWU UWU UWU",
                sender = +"the_user",
                senderId = "the_user",
                channelId = "the_channel",
                guildId = "the_guild"
            )
        }
        coVerify { f.sendChannelMessage("the_channel", embed) }
    }

    @Test
    fun `Test correct target command on single user in guild`() {
        mockHere<DiscordTargets> {
            every { parseDiscordTarget("UWU UWU UWU") } returns TargetParseResult.Success.UserById("The User")
            coEvery {
                resolveDiscordTarget(
                    TargetParseResult.Success.UserById("The User"),
                    "the_guild"
                )
            } returns TargetResult.User("The User")
        }
        val (_, embed) = mockMessagePipeline("the_channel") { it.getSuccessCommandReply(any(), "count.success", listOf(1)) }
        val f = softMockHere<DiscordClientFacade> {
            coEvery { isUserInGuild("The User", "the_guild") } returns true
        }
        test {
            run(
                fullCommand = "",
                commandBody = "UWU UWU UWU",
                sender = +"the_user",
                senderId = "the_user",
                channelId = "the_channel",
                guildId = "the_guild"
            )
        }
        coVerify { f.sendChannelMessage("the_channel", embed) }
    }

    @Test
    fun `Test correct target command on single user not in guild`() {
        mockHere<DiscordTargets> {
            every { parseDiscordTarget("UWU UWU UWU") } returns TargetParseResult.Success.UserById("The User")
            coEvery {
                resolveDiscordTarget(
                    TargetParseResult.Success.UserById("The User"),
                    "the_guild"
                )
            } returns TargetResult.User("The User")
        }
        val (_, embed) = mockMessagePipeline("the_channel") { it.getWrongTargetCommandReply(any(), "UWU UWU UWU") }
        val f = softMockHere<DiscordClientFacade> {
            coEvery { isUserInGuild("The User", "the_guild") } returns false
        }
        test {
            run(
                fullCommand = "",
                commandBody = "UWU UWU UWU",
                sender = +"the_user",
                senderId = "the_user",
                channelId = "the_channel",
                guildId = "the_guild"
            )
        }
        coVerify { f.sendChannelMessage("the_channel", embed) }
    }
}
