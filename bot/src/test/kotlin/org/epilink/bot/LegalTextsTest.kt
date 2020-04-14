package org.epilink.bot

import io.mockk.every
import io.mockk.mockk
import org.epilink.bot.config.LinkLegalConfiguration
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LegalTextsTest {
    private val pfolder = Files.createTempDirectory("epilink-tests-legal")
    private val fakeCfg = pfolder.resolve("hello.yaml").apply { Files.createFile(this) }

    @Test
    fun `Test ToS as string`() {
        val str = "Terms of servicesss"
        val cfg = mockk<LinkLegalConfiguration> {
            every { tos } returns str
            // Default behavior for other things
            every { policyFile } returns null
            every { policy } returns null
            every { identityPromptText } returns null
        }
        val loaded = cfg.load(fakeCfg)
        assertSame(str, loaded.tosText)
    }

    @Test
    fun `Test ToS as file`() {
        val str = "Terms of servicesss but in a file\nAmazing!"
        val fileName = "tos-file.html"
        val p = pfolder.resolve(fileName)
        Files.write(p, str.toByteArray())
        val cfg = mockk<LinkLegalConfiguration> {
            every { tos } returns null
            every { tosFile } returns "tos-file.html"
            // Default behavior for other things
            every { policyFile } returns null
            every { policy } returns null
            every { identityPromptText } returns null
        }
        val loaded = cfg.load(fakeCfg)
        assertEquals(str, loaded.tosText)
    }

    @Test
    fun `Test defaults`() {
        val cfg = mockk<LinkLegalConfiguration> {
            every { tos } returns null
            every { tosFile } returns null
            every { policyFile } returns null
            every { policy } returns null
            every { identityPromptText } returns null
        }
        val loaded = cfg.load(fakeCfg)
        assertEquals(
            "<strong>No Terms of Services found.</strong> Please contact your administrator for more information.",
            loaded.tosText
        )
        assertEquals(
            "<strong>No Privacy Policy found.</strong> Please contact your administrator for more information.",
            loaded.policyText
        )
        assertEquals(
            "For more information, contact your administrator or consult the privacy policy.",
            loaded.idPrompt
        )
    }

    @Test
    fun `Test PP as string`() {
        val str = "Privacy policyyyy"
        val cfg = mockk<LinkLegalConfiguration> {
            every { policy } returns str
            // Default behavior for other things
            every { tos } returns null
            every { tosFile } returns null
            every { identityPromptText } returns null
        }
        val loaded = cfg.load(fakeCfg)
        assertSame(str, loaded.policyText)
    }

    @Test
    fun `Test PP as file`() {
        val str = "Privacy policy, in a \nfiiiiiile"
        val fileName = "pp-file.html"
        val p = pfolder.resolve(fileName)
        Files.write(p, str.toByteArray())
        val cfg = mockk<LinkLegalConfiguration> {
            every { policy } returns null
            every { policyFile } returns fileName
            // Default behavior for other things
            every { tos } returns null
            every { tosFile } returns null
            every { identityPromptText } returns null
        }
        val loaded = cfg.load(fakeCfg)
        assertEquals(str, loaded.policyText)
    }

    @Test
    fun `Test ID prompt is passed as is`() {
        val hello = "helloooooooooo"
        val cfg = mockk<LinkLegalConfiguration> {
            every { identityPromptText } returns hello
            every { tos } returns null
            every { tosFile } returns null
            every { policyFile } returns null
            every { policy } returns null
        }
        val loaded = cfg.load(fakeCfg)
        assertSame(hello, loaded.idPrompt)
    }
}