/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.web

import guru.zoroark.tegral.di.dsl.put
import guru.zoroark.tegral.di.test.TestMutableInjectionEnvironment
import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.SessionStorage
import io.ktor.server.sessions.defaultSessionSerializer
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext
import io.mockk.CapturingSlot
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
import org.epilink.bot.http.sessions.ConnectedSession
import org.epilink.bot.putMockOrApply
import java.time.Duration
import java.time.Instant
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("ConstructorParameterNaming")
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

@Suppress("ConstructorParameterNaming")
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
    override suspend fun stop() {}

    override fun newRuleMediator(prefix: String): RuleMediator {
        error("Not supported")
    }

    override fun newSessionStorage(prefix: String): SessionStorage = sessionStorageProvider()

    override fun newUnlinkCooldownStorage(prefix: String): UnlinkCooldownStorage {
        error("Not supported")
    }
}

// Not strictly necessary, but we require SessionStorage objects in tests
internal class UnsafeTestSessionStorage : SessionStorage {
    private val sessions = ConcurrentHashMap<String, String>()
    override suspend fun read(id: String): String {
        return sessions[id] ?: throw NoSuchElementException("Session with id $id not found")
    }

    override suspend fun write(id: String, value: String) {
        sessions[id] = value
    }

    override suspend fun invalidate(id: String) {
        sessions.remove(id)
    }
}

// for setting up identity mocks
@OptIn(UsesTrueIdentity::class)
@Suppress("LongParameterList")
internal fun TestMutableInjectionEnvironment.setupSession(
    sessionStorage: SessionStorage,
    discId: String = "discordid",
    discUsername: String = "discorduser#1234",
    discAvatarUrl: String? = "https://avatar/url",
    msIdHash: ByteArray = byteArrayOf(1, 2, 3, 4, 5),
    created: Instant = Instant.now() - Duration.ofDays(1),
    trueIdentity: String? = null
): String {
    val u: User = mockk {
        every { discordId } returns discId
        every { idpIdHash } returns msIdHash
        every { creationDate } returns created
    }
    putMockOrApply<DatabaseFacade> {
        coEvery { getUser(discId) } returns u
        if (trueIdentity != null) {
            coEvery { isUserIdentifiable(u) } returns true
        }
    }
    if (trueIdentity != null) {
        putMockOrApply<IdentityManager> {
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
    )
    // Put that in our test session storage
    runBlocking { sessionStorage.write(id, data) }
    return id
}

suspend fun injectUserIntoAttributes(
    slot: CapturingSlot<PipelineContext<Unit, ApplicationCall>>,
    attribute: AttributeKey<User>,
    db: DatabaseFacade
) {
    val call = slot.captured.context
    call.attributes.put(
        attribute,
        db.getUser(call.sessions.get<ConnectedSession>()!!.discordId)!!
    )
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

fun TestMutableInjectionEnvironment.declareNoOpI18n() {
    put<DiscordMessagesI18n> { NoOpI18n }
}
