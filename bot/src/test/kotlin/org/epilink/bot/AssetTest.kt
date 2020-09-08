/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.epilink.bot.config.ResourceAssetConfig
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.*

class AssetTest {
    @Test
    fun `Test load asset url`() {
        val asset = ResourceAssetConfig(url = "This is a URL")
        val realAsset = runBlocking { loadAsset(asset, "TestAsset", Paths.get("")) }
        assertTrue(realAsset is ResourceAsset.Url)
        assertEquals("This is a URL", realAsset.url)
    }

    @Test
    fun `Test load asset file`() {
        // Temporary file
        val tmpFile = createTempFile("tmp_el_test")
        tmpFile.writeBytes(byteArrayOf(1, 2, 3))
        val asset = ResourceAssetConfig(file = tmpFile.toPath().toAbsolutePath().toString(), contentType = "text/plain")
        val realAsset = runBlocking { loadAsset(asset, "thing", Paths.get("")) }
        assertTrue(realAsset is ResourceAsset.File)
        assertEquals(ContentType.Text.Plain, realAsset.contentType)
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(realAsset.contents))
    }

    @Test
    fun `Test load nothing`() {
        val a = runBlocking { loadAsset(ResourceAssetConfig(), "E", Paths.get("")) }
        assertEquals(ResourceAsset.None, a)
    }

    @Test
    fun `Test asset as url - none`() {
        assertNull(ResourceAsset.None.asUrl("a name"))
    }

    @Test
    fun `Test asset as url - url`() {
        assertEquals("YUP", ResourceAsset.Url("YUP").asUrl("a name"))
    }

    @Test
    fun `Test asset as url - file`() {
        assertEquals("/api/v1/meta/a name", ResourceAsset.File(byteArrayOf(), null).asUrl("a name"))
    }
}