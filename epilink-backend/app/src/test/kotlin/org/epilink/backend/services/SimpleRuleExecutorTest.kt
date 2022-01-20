/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.epilink.backend.KoinBaseTest
import org.epilink.backend.cache.NoCacheRuleCache
import org.epilink.backend.cache.RuleCache
import org.epilink.backend.rulebook.RuleException
import org.epilink.backend.rulebook.WeakIdentityRule
import org.koin.dsl.module

class SimpleRuleExecutorTest : KoinBaseTest<RuleExecutor>(
    RuleExecutor::class,
    module {
        single<RuleExecutor> { RuleExecutorImpl() }
        single<RuleCache> { NoCacheRuleCache() }
    }
) {
    @Test
    fun `Test run rule`() {
        val rule = WeakIdentityRule("e", null) {
            assertEquals("dis", this.userDiscordDiscriminator)
            assertEquals("id", this.userDiscordId)
            assertEquals("name", this.userDiscordName)
            roles += "hey"
        }
        test {
            assertEquals(RuleResult.Success(listOf("hey")), executeRule(rule, "id", "name", "dis", null))
        }
    }

    @Test
    fun `Test run rule crash`() {
        val rule = WeakIdentityRule("e", null) {
            error("oh no")
        }
        test {
            val result = executeRule(rule, "", "", "", null)
            assertTrue(result is RuleResult.Failure)
            assertTrue(result.exception is RuleException)
        }
    }
}
