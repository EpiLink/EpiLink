/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegalTextsTest {
    private val pfolder = Files.createTempDirectory("epilink-tests-legal")

    @Test
    fun `Test load text`() {
        assertEquals(LegalText.Html("one"), loadLegalText("one", { error("no") }, "two"))
    }

    @Test
    fun `Test load HTML file`() {
        val str = "<p>Terms of servicesss but in a file<br>Amazing!</p>"
        val fileName = "tos-file.html"
        val p = pfolder.resolve(fileName)
        Files.write(p, str.toByteArray())
        assertEquals(LegalText.Html(str), loadLegalText(null, { p }, "no"))
    }

    @Test
    fun `Test load PDF file`() {
        val data = byteArrayOf(1, 2, 3, 4)
        val filename = "no.pdf"
        val p = pfolder.resolve(filename)
        Files.write(p, data)
        val result = loadLegalText(null, { p }, "no")
        assertTrue(result is LegalText.Pdf)
        assertTrue(data.contentEquals(result.data))
    }

    @Test
    fun `Test default value`() {
        assertEquals(LegalText.Html("three"), loadLegalText(null, null, "three"))
    }
}