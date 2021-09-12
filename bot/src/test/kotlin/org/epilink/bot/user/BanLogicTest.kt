/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.user

import guru.zoroark.shedinja.dsl.put
import guru.zoroark.shedinja.test.ShedinjaBaseTest
import io.mockk.every
import io.mockk.mockk
import org.epilink.bot.db.Ban
import org.epilink.bot.db.BanLogic
import org.epilink.bot.db.BanLogicImpl
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BanLogicTest : ShedinjaBaseTest<BanLogic>(
    BanLogic::class, {
        put<BanLogic>(::BanLogicImpl)
    }
) {
    @Test
    fun `Revoked ban is not active`() {
        val ban = mockk<Ban> {
            every { revoked } returns true
        }
        test {
            assertFalse(subject.isBanActive(ban))
        }
    }

    @Test
    fun `Expired ban is not active`() {
        val ban = mockk<Ban> {
            every { revoked } returns false
            every { expiresOn } returns Instant.now() - Duration.ofHours(1)
        }
        test {
            assertFalse(subject.isBanActive(ban))
        }
    }

    @Test
    fun `Ban with no expiry is active`() {
        val ban = mockk<Ban> {
            every { revoked } returns false
            every { expiresOn } returns null
        }
        test {
            assertTrue(subject.isBanActive(ban))
        }
    }

    @Test
    fun `Ban with non-expired expiry is active`() {
        val ban = mockk<Ban> {
            every { revoked } returns false
            every { expiresOn } returns Instant.now() + Duration.ofHours(10)
        }
        test {
            assertTrue(subject.isBanActive(ban))
        }
    }
}
