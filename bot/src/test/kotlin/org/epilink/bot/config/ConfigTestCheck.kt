/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.config

import io.mockk.every
import io.mockk.mockk
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
            every { identityPromptText } returns null
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
            every { identityPromptText } returns null
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
            every { identityPromptText } returns null
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
            every { identityPromptText } returns null
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
            every { identityPromptText } returns null
        }
        val reports = config.check()
        assertTrue(reports.any { it is ConfigWarning && it.message.contains("identityPromptText") })
    }

    @Test
    fun `Test no trailing slash at end of front-end URL triggers error`() {
        val config = mockk<LinkWebServerConfiguration> {
            every { frontendUrl } returns "https://whereismy.slash"
            // Normal config
            every { rateLimitingProfile } returns RateLimitingProfile.Standard
            every { enableAdminEndpoints } returns true
        }
        val reports = config.check()
        assertTrue(reports.any { it is ConfigError && it.shouldFail && it.message.contains("frontendUrl") })
    }

    @Test
    fun `Test lenient rate limiting profile triggers warning`() {
        val config = mockk<LinkWebServerConfiguration> {
            every { rateLimitingProfile } returns RateLimitingProfile.Lenient
            // Normal config
            every { frontendUrl } returns "https://my.slash.ishere/"
            every { enableAdminEndpoints } returns true
        }
        val reports = config.check()
        assertTrue(reports.any { it is ConfigWarning && it.message.contains("Lenient") })
    }

    @Test
    fun `Test disabled rate limiting profile triggers non-fatal error`() {
        val config = mockk<LinkWebServerConfiguration> {
            every { rateLimitingProfile } returns RateLimitingProfile.Disabled
            // Normal config
            every { frontendUrl } returns "https://my.slash.ishere/"
            every { enableAdminEndpoints } returns true
        }
        val reports = config.check()
        assertTrue(reports.any { it is ConfigError && !it.shouldFail && it.message.contains("Disabled") })
    }

    @Test
    fun `Test enabled admin endpoints message`() {
        val config = mockk<LinkWebServerConfiguration> {
            every { enableAdminEndpoints } returns true
            // Normal config
            every { frontendUrl } returns "https://my.slash.ishere/"
            every { rateLimitingProfile } returns RateLimitingProfile.Standard
        }
        val reports = config.check()
        assertTrue(reports.any { it is ConfigInfo && it.message.contains("endpoints") && it.message.contains("enabled") })
    }

    @Test
    fun `Test disabled admin endpoints message`() {
        val config = mockk<LinkWebServerConfiguration> {
            every { enableAdminEndpoints } returns false
            // Normal config
            every { frontendUrl } returns "https://my.slash.ishere/"
            every { rateLimitingProfile } returns RateLimitingProfile.Standard
        }
        val reports = config.check()
        assertTrue(reports.any { it is ConfigInfo && it.message.contains("endpoints") && it.message.contains("disabled") })
    }

    private val defaultTokenValue = "..."

    @Test
    fun `Test default Discord client id triggers error`() {
        assertContainsSingleError(tokens(discordOAuthClientId = defaultTokenValue).check(), "discordOAuthClientId")
    }

    @Test
    fun `Test default Discord client secret triggers error`() {
        assertContainsSingleError(tokens(discordOAuthSecret = defaultTokenValue).check(), "discordOAuthSecret")
    }

    @Test
    fun `Test default Discord token triggers error`() {
        assertContainsSingleError(tokens(discordToken = defaultTokenValue).check(), "discordToken")
    }

    @Test
    fun `Test default Microsoft client id triggers error`() {
        assertContainsSingleError(tokens(msftOAuthClientId = defaultTokenValue).check(), "idpOAuthClientId")
    }

    @Test
    fun `Test default Microsoft client secrt triggers error`() {
        assertContainsSingleError(tokens(msftOAuthSecret = defaultTokenValue).check(), "idpOAuthSecret")
    }

    /**
     * Assert that the report has a single error, whose message must contain the provided substring
     */
    private fun assertContainsSingleError(report: List<ConfigReportElement>, vararg substring: String) {
        val messages = report.joinToString(" // ") { it.message }
        assertEquals(1, report.size, "Expected only one error but got: $messages")
        report[0].message.let { msg ->
            substring.forEach { s ->
                assertTrue(msg.contains(s), "Message '$msg' does not contain expected substring $s")
            }
        }
    }

    /**
     * Assert that the report has at least one error whose message must contain the provided substring
     */
    private fun assertContainsError(report: List<ConfigReportElement>, vararg substring: String) {
        val messages = report.joinToString(" // ") { it.message }
        assertTrue(report.any { el ->
            substring.all { s ->
                el.message.contains(s)
            }
        }, "None of the messages matched the expected substrings ($messages)")
    }

    /**
     * Create a fake LinkTokens with valid values for all parameters. To create invalid values, provide an argument.
     */
    private fun tokens(
        discordOAuthClientId: String = "a",
        discordOAuthSecret: String = "b",
        discordToken: String = "c",
        msftOAuthClientId: String = "d",
        msftOAuthSecret: String = "e"
    ): LinkTokens = LinkTokens(
        discordOAuthClientId = discordOAuthClientId,
        discordOAuthSecret = discordOAuthSecret,
        discordToken = discordToken,
        idpOAuthClientId = msftOAuthClientId,
        idpOAuthSecret = msftOAuthSecret
    )

    private val testValidLanguages = setOf("a", "b", "c")

    @Test
    fun `Test invalid default language config error`() {
        val r = languages(default = "blahblah").checkCoherenceWithLanguages(testValidLanguages)
        assertContainsError(r, "default", "blahblah")
    }

    @Test
    fun `Test preferred does not contain default language config error`() {
        val r = languages(default = "b", preferred = listOf("c")).checkCoherenceWithLanguages(testValidLanguages)
        assertContainsSingleError(r, "must contain the default language (b)")
    }

    @Test
    fun `Test invalid preferred language config error`() {
        val r = languages(preferred = listOf("a", "klkl")).checkCoherenceWithLanguages(testValidLanguages)
        assertContainsSingleError(r, "klkl")
    }

    private fun languages(
        default: String = "a",
        preferred: List<String> = listOf("a")
    ) = mockk<LinkDiscordConfig> {
        every { defaultLanguage } returns default
        every { preferredLanguages } returns preferred
    }
}