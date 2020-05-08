/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import io.ktor.sessions.SessionStorage
import org.epilink.bot.discord.RuleMediator
import org.epilink.bot.http.SimplifiedSessionStorage
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertFalse
import kotlin.test.assertTrue

data class ApiSuccess(
    val success: Boolean,
    val message: String?,
    val data: Map<String, Any?>?
) {
    init {
        assertTrue(success)
    }
}

data class ApiError(
    val success: Boolean,
    val message: String,
    val data: ApiErrorDetails
) {
    init {
        assertFalse(success)
    }
}

data class ApiErrorDetails(
    val code: Int,
    val description: String
)

class DummyCacheClient(private val sessionStorageProvider: () -> SessionStorage) : CacheClient {
    override suspend fun start() {}

    override fun newRuleMediator(prefix: String): RuleMediator {
        error("Not supported")
    }

    override fun newSessionStorage(prefix: String): SessionStorage = sessionStorageProvider()
}

// TODO is this necessary?
internal class UnsafeTestSessionStorage : SimplifiedSessionStorage() {
    val sessions = ConcurrentHashMap<String, ByteArray>()
    override suspend fun read(id: String): ByteArray? {
        return sessions[id]
    }

    override suspend fun write(id: String, data: ByteArray?) {
        if (data == null)
            sessions.remove(id)
        else
            sessions[id] = data
    }

    override suspend fun invalidate(id: String) {
        sessions.remove(id)
    }
}