/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import io.mockk.coEvery
import kotlinx.coroutines.runBlocking
import org.epilink.bot.discord.*
import org.koin.dsl.module
import org.koin.test.get
import kotlin.test.Test
import kotlin.test.assertEquals


class DiscordTargetTest : KoinBaseTest(
    module {
        single<LinkDiscordTargets> { LinkDiscordTargetsImpl() }
    }
) {
    @Test
    fun `Test parse Discord user ping (without !)`() = test {
        assertEquals(TargetParseResult.Success.UserById("1234567890"), parseDiscordTarget("<@1234567890>"))
    }

    @Test
    fun `Test parse Discord user ping (with !)`() = test {
        assertEquals(TargetParseResult.Success.UserById("1357924680"), parseDiscordTarget("<@!1357924680>"))
    }

    @Test
    fun `Test parse Discord role ping`() = test {
        assertEquals(TargetParseResult.Success.RoleById("0987654321"), parseDiscordTarget("<@&0987654321>"))
    }

    @Test
    fun `Test parse Discord ID directly`() = test {
        assertEquals(TargetParseResult.Success.UserById("1234567890"), parseDiscordTarget("1234567890"))
    }

    @Test
    fun `Test parse role ID`() = test {
        assertEquals(TargetParseResult.Success.RoleById("0987654321"), parseDiscordTarget("/0987654321"))
    }

    @Test
    fun `Test parse role by name`() = test {
        assertEquals(TargetParseResult.Success.RoleByName("Role Name Yay"), parseDiscordTarget("|Role Name Yay"))
    }

    @Test
    fun `Test parse special everyone`() = test {
        assertEquals(TargetParseResult.Success.Everyone, parseDiscordTarget("!everyone"))
    }

    @Test
    fun `Test resolve user by id`() = test {
        assertEquals(
            TargetResult.User("12345"),
            resolveDiscordTarget(TargetParseResult.Success.UserById("12345"), "" /* unused */)
        )
    }

    @Test
    fun `Test resolve role by name`() = test {
        mockHere<LinkDiscordClientFacade> {
            coEvery { getRoleIdByName("Role Name Yay", "servid") } returns "roleid"
        }
        assertEquals(
            TargetResult.Role("roleid"),
            resolveDiscordTarget(TargetParseResult.Success.RoleByName("Role Name Yay"), "servid")
        )
    }

    @Test
    fun `Test resolve role by id`() = test {
        assertEquals(
            TargetResult.Role("09876"),
            resolveDiscordTarget(TargetParseResult.Success.RoleById("09876"), "" /* unused */)
        )
    }

    @Test
    fun `Test resolve everyone special value`() = test {
        assertEquals(
            TargetResult.Everyone,
            resolveDiscordTarget(TargetParseResult.Success.Everyone, "" /* unused */)
        )
    }

    private fun test(block: suspend LinkDiscordTargets.() -> Unit) {
        runBlocking { block(get()) }
    }
}