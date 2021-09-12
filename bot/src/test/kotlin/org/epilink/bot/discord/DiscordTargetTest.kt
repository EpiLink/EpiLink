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
import org.epilink.bot.putMock
import org.epilink.bot.stest
import kotlin.test.Test
import kotlin.test.assertEquals

class DiscordTargetTest : ShedinjaBaseTest<DiscordTargets>(
    DiscordTargets::class, {
        put<DiscordTargets>(::DiscordTargetsImpl)
    }
) {
    @Test
    fun `Test parse Discord user ping (without !)`() = test {
        assertEquals(TargetParseResult.Success.UserById("1234567890"), subject.parseDiscordTarget("<@1234567890>"))
    }

    @Test
    fun `Test parse Discord user ping (with !)`() = test {
        assertEquals(TargetParseResult.Success.UserById("1357924680"), subject.parseDiscordTarget("<@!1357924680>"))
    }

    @Test
    fun `Test parse Discord role ping`() = test {
        assertEquals(TargetParseResult.Success.RoleById("0987654321"), subject.parseDiscordTarget("<@&0987654321>"))
    }

    @Test
    fun `Test parse Discord ID directly`() = test {
        assertEquals(TargetParseResult.Success.UserById("1234567890"), subject.parseDiscordTarget("1234567890"))
    }

    @Test
    fun `Test parse role ID`() = test {
        assertEquals(TargetParseResult.Success.RoleById("0987654321"), subject.parseDiscordTarget("/0987654321"))
    }

    @Test
    fun `Test parse role by name`() = test {
        assertEquals(TargetParseResult.Success.RoleByName("Role Name Yay"), subject.parseDiscordTarget("|Role Name Yay"))
    }

    @Test
    fun `Test parse special everyone`() = test {
        assertEquals(TargetParseResult.Success.Everyone, subject.parseDiscordTarget("!everyone"))
    }

    @Test
    fun `Test resolve user by id`() = stest {
        assertEquals(
            TargetResult.User("12345"),
            subject.resolveDiscordTarget(TargetParseResult.Success.UserById("12345"), "" /* unused */)
        )
    }

    @Test
    fun `Test resolve role by name`() = stest {
        putMock<DiscordClientFacade> {
            coEvery { getRoleIdByName("Role Name Yay", "servid") } returns "roleid"
        }
        assertEquals(
            TargetResult.Role("roleid"),
            subject.resolveDiscordTarget(TargetParseResult.Success.RoleByName("Role Name Yay"), "servid")
        )
    }

    @Test
    fun `Test resolve role by name not found`() = stest {
        putMock<DiscordClientFacade> {
            coEvery { getRoleIdByName("Role50", "servid") } returns null
        }
        assertEquals(
            TargetResult.RoleNotFound("Role50"),
            subject.resolveDiscordTarget(TargetParseResult.Success.RoleByName("Role50"), "servid")
        )
    }

    @Test
    fun `Test resolve role by id`() = stest {
        assertEquals(
            TargetResult.Role("09876"),
            subject.resolveDiscordTarget(TargetParseResult.Success.RoleById("09876"), "" /* unused */)
        )
    }

    @Test
    fun `Test resolve everyone special value`() = stest {
        assertEquals(
            TargetResult.Everyone,
            subject.resolveDiscordTarget(TargetParseResult.Success.Everyone, "" /* unused */)
        )
    }
}
