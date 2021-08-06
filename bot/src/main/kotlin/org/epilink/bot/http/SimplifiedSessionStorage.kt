/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.http

import io.ktor.sessions.SessionStorage
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.reader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

private const val BUFFER_SIZE = 1024

internal abstract class SimplifiedSessionStorage : SessionStorage {
    abstract suspend fun read(id: String): ByteArray?
    abstract suspend fun write(id: String, data: ByteArray?)

    override suspend fun <R> read(id: String, consumer: suspend (ByteReadChannel) -> R): R {
        val data = read(id) ?: throw NoSuchElementException("Session $id not found")
        return consumer(ByteReadChannel(data))
    }

    override suspend fun write(id: String, provider: suspend (ByteWriteChannel) -> Unit) {
        return provider(CoroutineScope(Dispatchers.IO).reader(coroutineContext, autoFlush = true) {
            write(id, channel.readAvailable())
        }.channel)
    }
}

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

