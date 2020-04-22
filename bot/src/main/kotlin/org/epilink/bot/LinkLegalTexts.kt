package org.epilink.bot

import org.epilink.bot.config.LinkLegalConfiguration
import java.nio.file.Files
import java.nio.file.Path

/**
 * The legal configuration, loaded in memory
 */
data class LinkLegalTexts(
    /**
     * The terms of services' text
     */
    val tosText: String,
    /**
     * The privacy policy's text
     */
    val policyText: String,
    /**
     * The text shown in the ID prompt ("Remember who I am").
     */
    val idPrompt: String
)

/**
 * Load the legal texts from the given configuration files
 */
fun LinkLegalConfiguration.load(cfg: Path): LinkLegalTexts {
    return LinkLegalTexts(
        tosText = tos ?: tosFile?.let { Files.readString(cfg.resolveSibling(it)) } ?: """
            <strong>No Terms of Services found.</strong> Please contact your administrator for more information.
            """.trimIndent(),
        policyText = policy ?: policyFile?.let { Files.readString(cfg.resolveSibling(it)) } ?: """
            <strong>No Privacy Policy found.</strong> Please contact your administrator for more information.
            """.trimIndent(),
        idPrompt = identityPromptText
            ?: "<p class=\"description\">For more information, contact your administrator or consult the privacy policy.</p>"
    )
}