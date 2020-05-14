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
import io.ktor.sessions.defaultSessionSerializer
import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.apache.commons.codec.binary.Hex
import org.epilink.bot.db.LinkServerDatabase
import org.epilink.bot.db.LinkUser
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.discord.LinkDiscordMessages
import org.epilink.bot.discord.RuleMediator
import org.epilink.bot.http.SimplifiedSessionStorage
import org.epilink.bot.http.sessions.ConnectedSession
import org.koin.test.KoinTest
import java.time.Duration
import java.time.Instant
import java.util.*
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

@OptIn(
    KtorExperimentalAPI::class, // We get a choice between a deprecated or an experimental func...
    UsesTrueIdentity::class // for setting up identity mocks
)
internal fun KoinTest.setupSession(
    sessionStorage: SimplifiedSessionStorage,
    discId: String = "discordid",
    discUsername: String = "discorduser#1234",
    discAvatarUrl: String? = "https://avatar/url",
    msIdHash: ByteArray = byteArrayOf(1, 2, 3, 4, 5),
    created: Instant = Instant.now() - Duration.ofDays(1),
    trueIdentity: String? = null
): String {
    val u: LinkUser = mockk {
        every { discordId } returns discId
        every { msftIdHash } returns msIdHash
        every { creationDate } returns created
    }
    softMockHere<LinkServerDatabase> {
        coEvery { getUser(discId) } returns u
        if (trueIdentity != null) {
            coEvery { isUserIdentifiable(discId) } returns true
            coEvery { accessIdentity(u, any(), any(), any()) } returns trueIdentity
        }
    }
    // Generate an ID
    val arr = ByteArray(128)
    Random().nextBytes(arr)
    val id = Hex.encodeHexString(arr)
    // Generate the session data
    val data = defaultSessionSerializer<ConnectedSession>().serialize(
        ConnectedSession(discId, discUsername, discAvatarUrl)
    ).toByteArray()
    // Put that in our test session storage
    runBlocking { sessionStorage.write(id, data) }
    return id
}