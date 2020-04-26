/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.rulebooks

import kotlinx.coroutines.runBlocking
import org.epilink.bot.rulebook.*
import java.time.Duration
import kotlin.test.*

class RulebookDslTest {
    @Test
    fun `Test weak rule without cache`() {
        var shouldBeChanged = false
        val block: RuleDeterminer = { shouldBeChanged = true }
        val rb = rulebook {
            "I am a weak rule"(block)
        }
        assertEquals(1, rb.rules.size)
        runBlocking {
            rb.rules["I am a weak rule"].apply {
                assertNotNull(this)
                assertNull(cacheDuration)
                assertEquals("I am a weak rule", name)
                assertTrue(this is WeakIdentityRule)
                determineRoles("", "", "")
                assertTrue(shouldBeChanged)
            }
        }
    }

    @Test
    fun `Test strong rule without cache`() {
        var shouldBeChanged = false
        val block: RuleDeterminerWithIdentity = { shouldBeChanged = true }
        val rb = rulebook {
            "I am a strong rule" % block
        }
        assertEquals(1, rb.rules.size)
        runBlocking {
            rb.rules["I am a strong rule"].apply {
                assertNotNull(this)
                assertNull(cacheDuration)
                assertEquals("I am a strong rule", name)
                assertTrue(this is StrongIdentityRule)
                determineRoles("", "", "", "")
                assertTrue(shouldBeChanged)
            }
        }
    }

    @Test
    fun `Test weak rule with cache`() {
        var shouldBeChanged = false
        val block: RuleDeterminer = { shouldBeChanged = true }
        val rb = rulebook {
            ("I am a weak rule" cachedFor Duration.ofHours(1))(block)
        }
        assertEquals(1, rb.rules.size)
        runBlocking {
            rb.rules["I am a weak rule"].apply {
                assertNotNull(this)
                assertEquals(Duration.ofHours(1), cacheDuration)
                assertEquals("I am a weak rule", name)
                assertTrue(this is WeakIdentityRule)
                determineRoles("", "", "")
                assertTrue(shouldBeChanged)
            }
        }
    }

    @Test
    fun `Test strong rule with cache`() {
        var shouldBeChanged = false
        val block: RuleDeterminerWithIdentity = { shouldBeChanged = true }
        val rb = rulebook {
            ("I am a strong rule" cachedFor Duration.ofMinutes(1)) % block
        }
        assertEquals(1, rb.rules.size)
        runBlocking {
            rb.rules["I am a strong rule"].apply {
                assertNotNull(this)
                assertEquals(Duration.ofMinutes(1), cacheDuration)
                assertEquals("I am a strong rule", name)
                assertTrue(this is StrongIdentityRule)
                determineRoles("", "", "", "")
                assertTrue(shouldBeChanged)
            }
        }
    }

    @Test
    fun `cachedFor test`() {
        val result = "Hello" cachedFor Duration.ofHours(1)
        assertEquals("Hello", result.name)
        assertEquals(Duration.ofHours(1), result.duration)
    }
}