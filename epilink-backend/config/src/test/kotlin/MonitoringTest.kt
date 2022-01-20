/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.config

import io.mockk.every
import io.mockk.mockk
import org.epilink.backend.config.DiscordConfiguration
import org.epilink.backend.config.isMonitored
import kotlin.test.*

class MonitoringTest {
    @Test
    fun `Test monitoring check`() {
        val ldc: DiscordConfiguration = mockk {
            every { servers } returns listOf(
                mockk { every { id } returns "YEET" }
            )
        }
        assertTrue(ldc.isMonitored("YEET"))
        assertFalse(ldc.isMonitored("Yote"))
    }
}
