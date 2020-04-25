/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import io.mockk.every
import io.mockk.mockk
import org.epilink.bot.config.LinkDiscordConfig
import org.epilink.bot.config.isMonitored
import kotlin.test.*

class SmallTests {
    @Test
    fun `Test monitoring check`() {
        val ldc: LinkDiscordConfig = mockk {
            every { servers } returns listOf(
                mockk { every { id } returns "YEET" }
            )
        }
        assertTrue(ldc.isMonitored("YEET"))
        assertFalse(ldc.isMonitored("Yote"))
    }
}