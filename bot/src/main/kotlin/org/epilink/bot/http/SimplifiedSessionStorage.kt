/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.http

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

private const val BUFFER_SIZE = 1024

private suspend fun ByteReadChannel.readAvailable(): ByteArray = withContext(Dispatchers.IO) {
    val data = ByteArrayOutputStream()
    val temp = ByteBuffer.allocate(BUFFER_SIZE)
    while (!isClosedForRead) {
        val read = readAvailable(temp)
        if (read <= 0) break
        @Suppress("BlockingMethodInNonBlockingContext") // it IS a blocking context but it's complaining about it
        data.write(temp.array(), 0, read)
        temp.clear()
    }
    data.toByteArray()
}
