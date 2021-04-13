/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import kotlinx.coroutines.runBlocking
import org.epilink.bot.discord.NoCacheRuleMediator
import org.epilink.bot.discord.RuleResult
import org.epilink.bot.rulebook.RuleException
import org.epilink.bot.rulebook.WeakIdentityRule
import kotlin.test.*

class NoCacheRmTest {
    @Test
    fun `Test run rule`() = runBlocking {
        val rule = WeakIdentityRule("e", null) {
            assertEquals("dis", this.userDiscordDiscriminator)
            assertEquals("id", this.userDiscordId)
            assertEquals("name", this.userDiscordName)
            roles += "hey"
        }
        assertEquals(RuleResult.Success(listOf("hey")), NoCacheRuleMediator().runRule(rule, "id", "name", "dis", null))
    }

    @Test
    fun `Test run rule crash`() = runBlocking {
        val rule = WeakIdentityRule("e", null) {
            error("oh no")
        }
        val result = NoCacheRuleMediator().runRule(rule, "", "", "", null)
        assertTrue(result is RuleResult.Failure)
        assertTrue(result.exception is RuleException)
    }
}