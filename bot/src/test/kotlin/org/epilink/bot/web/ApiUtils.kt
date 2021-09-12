/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.web

import guru.zoroark.shedinja.dsl.put
import guru.zoroark.shedinja.test.UnsafeMutableEnvironment
import io.ktor.sessions.SessionStorage
import io.ktor.sessions.defaultSessionSerializer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.apache.commons.codec.binary.Hex
import org.epilink.bot.CacheClient
import org.epilink.bot.db.DatabaseFacade
import org.epilink.bot.db.IdentityManager
import org.epilink.bot.db.UnlinkCooldownStorage
import org.epilink.bot.db.User
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.discord.DiscordMessagesI18n
import org.epilink.bot.discord.RuleMediator
import org.epilink.bot.http.SimplifiedSessionStorage
import org.epilink.bot.http.sessions.ConnectedSession
import org.epilink.bot.softPutMock
import java.time.Duration
import java.time.Instant
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import kotlin.test.assertFalse
import kotlin.test.assertTrue

data class ApiSuccess(
    val success: Boolean,
    val message: String?,
    val message_i18n: String?,
    val message_i18n_data: Map<String, String>,
    val data: Map<String, Any?>?
) {
    init {
        assertTrue(success)
    }
}

data class ApiError(
    val success: Boolean,
    val message: String,
    val message_i18n: String,
    val message_i18n_data: Map<String, String>,
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

    override fun newUnlinkCooldownStorage(prefix: String): UnlinkCooldownStorage {
        error("Not supported")
    }
}

// Not strictly necessary, but we require SimplifiedSessionStorage objects in tests
internal class UnsafeTestSessionStorage : SimplifiedSessionStorage() {
    private val sessions = ConcurrentHashMap<String, ByteArray>()
    override suspend fun read(id: String): ByteArray? {
        return sessions[id]
    }

    override suspend fun write(id: String, data: ByteArray?) {
        if (data == null) {
            sessions.remove(id)
        } else {
            sessions[id] = data
        }
    }

    override suspend fun invalidate(id: String) {
        sessions.remove(id)
    }
}

@OptIn(
    UsesTrueIdentity::class // for setting up identity mocks
)
internal fun UnsafeMutableEnvironment.setupSession(
    sessionStorage: SimplifiedSessionStorage,
    discId: String = "discordid",
    discUsername: String = "discorduser#1234",
    discAvatarUrl: String? = "https://avatar/url",
    msIdHash: ByteArray = byteArrayOf(1, 2, 3, 4, 5),
    created: Instant = Instant.now() - Duration.ofDays(1),
    trueIdentity: String? = null
): String {
    val u: User = mockk {
        every { this@mockk.discordId } returns discId
        every { this@mockk.idpIdHash } returns msIdHash
        every { this@mockk.creationDate } returns created
    }
    softPutMock<DatabaseFacade> {
        coEvery { getUser(discId) } returns u
        if (trueIdentity != null) {
            coEvery { isUserIdentifiable(u) } returns true
        }
    }
    if (trueIdentity != null) {
        softPutMock<IdentityManager> {
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


object NoOpI18n : DiscordMessagesI18n {
    override val availableLanguages = setOf("")

    override val preferredLanguages = listOf("")

    override val defaultLanguage = ""

    override suspend fun getLanguage(discordId: String?): String = ""

    override fun get(language: String, key: String): String = key

    override suspend fun setLanguage(discordId: String, language: String): Boolean {
        error("Not implemented")
    }
}

fun UnsafeMutableEnvironment.declareNoOpI18n() {
    put<DiscordMessagesI18n> { NoOpI18n }
}
