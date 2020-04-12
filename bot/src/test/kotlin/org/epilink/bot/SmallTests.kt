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