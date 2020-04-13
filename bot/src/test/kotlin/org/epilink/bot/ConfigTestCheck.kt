package org.epilink.bot

import io.mockk.every
import io.mockk.mockk
import org.epilink.bot.config.ConfigError
import org.epilink.bot.config.ConfigWarning
import org.epilink.bot.config.LinkLegalConfiguration
import org.epilink.bot.config.check
import kotlin.test.*

class ConfigTestCheck {
    @Test
    fun `Test no tos in config triggers warning`() {
        val config = mockk<LinkLegalConfiguration> {
            every { tos } returns null
            every { tosFile } returns null
            // Valid config for policies
            every { policy } returns ""
            every { policyFile } returns null
        }
        val reports = config.check()
        assertTrue(reports.any { it is ConfigWarning && it.message.contains("no tos", ignoreCase = true) })
    }

    @Test
    fun `Test both tos and tosFile in config triggers error`() {
        val config = mockk<LinkLegalConfiguration> {
            every { tos } returns ""
            every { tosFile } returns ""
            // Valid config for policies
            every { policy } returns ""
            every { policyFile } returns null
        }
        val reports = config.check()
        assertTrue(reports.any { it is ConfigError && it.shouldFail && it.message.contains("a tos and a tosFile") })
    }

    @Test
    fun `Test no privacy policy in config triggers warning`() {
        val config = mockk<LinkLegalConfiguration> {
            every { tos } returns ""
            every { tosFile } returns null
            every { policy } returns null
            every { policyFile } returns null
        }

        val reports = config.check()
        assertTrue(reports.any { it is ConfigWarning && it.message.contains("no privacy policy", ignoreCase = true) })
    }

    @Test
    fun `Test both privacy policy and privacy policy file in config triggers error`() {
        val config = mockk<LinkLegalConfiguration> {
            every { tos } returns ""
            every { tosFile } returns null
            every { policy } returns ""
            every { policyFile } returns ""
        }
        val reports = config.check()
        assertTrue(reports.any { it is ConfigError && it.shouldFail && it.message.contains("a policy and a policyFile") })
    }

    @Test
    fun `Test no identity prompt text triggers warning`() {
        val config = mockk<LinkLegalConfiguration> {
            every { identityPromptText } returns null
            // Normal configs for everything else
            every { tos } returns ""
            every { tosFile } returns null
            every { policy } returns ""
            every { policyFile } returns null
        }
        val reports = config.check()
        assertTrue(reports.any { it is ConfigWarning && it.message.contains("identityPromptText") })
    }
}