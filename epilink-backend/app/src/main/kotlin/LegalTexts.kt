/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend

import io.ktor.http.ContentType
import io.ktor.util.extension
import org.epilink.backend.config.LegalConfiguration
import java.nio.file.Files
import java.nio.file.Path

/**
 * The legal configuration, loaded in memory
 */
data class LegalTexts(
    /**
     * The terms of services' text
     */
    val termsOfServices: LegalText,
    /**
     * The privacy policy's text
     */
    val privacyPolicy: LegalText,
    /**
     * The text shown in the ID prompt ("Remember who I am").
     */
    val idPrompt: String
)

private val defaultTosText =
    """
    <strong>No Terms of Services found.</strong> Please contact your administrator for more information.
    """.trimIndent()

private val defaultPpText =
    """
    <strong>No Privacy Policy found.</strong> Please contact your administrator for more information.
    """.trimIndent()

private val defaultIdPromptText =
    """
    <p class="description">For more information, contact your administrator or consult the privacy policy.</p>
    """.trimIndent()

/**
 * Load the legal texts from the given configuration files
 */
fun LegalConfiguration.load(cfg: Path): LegalTexts {
    return LegalTexts(
        termsOfServices = loadLegalText(tos, tosFile?.let { { cfg.resolveSibling(it) } }, defaultTosText),
        privacyPolicy = loadLegalText(policy, policyFile?.let { { cfg.resolveSibling(it) } }, defaultPpText),
        idPrompt = identityPromptText ?: defaultIdPromptText
    )
}

/**
 * Class for legal texts (Terms of Services and Privacy Policy)
 *
 * @property contentType The content type of the enclosed text
 */
sealed class LegalText(val contentType: ContentType) {
    /**
     * HTML formatted text available as a string
     *
     * @property text the HTML text
     */
    data class Html(val text: String) : LegalText(ContentType.Text.Html)

    /**
     * A PDF document available as a byte array
     *
     * @property data the PDF document
     */
    class Pdf(val data: ByteArray) : LegalText(ContentType.Application.Pdf)
}

/**
 * Load a legal text, from a text value, or if null a file path, or if null returns a default HTML text.
 */
fun loadLegalText(textValue: String?, file: (() -> Path)?, defaultText: String): LegalText {
    return when {
        textValue != null -> LegalText.Html(textValue)
        file != null -> file().let {
            if (it.extension == "pdf") {
                LegalText.Pdf(Files.readAllBytes(it))
            } else {
                LegalText.Html(Files.readString(it))
            }
        }
        else -> LegalText.Html(defaultText)
    }
}
