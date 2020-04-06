package org.epilink.bot

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.*
import io.ktor.sessions.SessionStorage
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.mockk.every
import io.mockk.mockk
import org.epilink.bot.config.LinkConfiguration
import org.epilink.bot.http.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.test.mock.declare
import kotlin.test.*

data class ApiSuccess(
    val success: Boolean,
    val message: String?,
    val data: Map<String, Any?>
) {
    init {
        assertTrue(success)
    }
}

class BackEndTest : KoinTest {

    private val env = LinkServerEnvironment(
        minimalConfig.copy(
            name = "EpiLink Test Instance"
        ), mockk()
    )

    @BeforeTest
    fun setupKoin() {
        startKoin {
            modules(module {
                single<LinkBackEnd> { LinkBackEndImpl() }
                single<SessionStorageProvider> { DummySessionStorageProvider }
            })
        }
    }

    @AfterTest
    fun tearDownKoin() {
        stopKoin()
    }

    @Test
    fun `Test meta information gathering`() {
        mockHere<LinkServerEnvironment> {
            every { name } returns "EpiLink Test Instance"
        }
        mockHere<LinkDiscordBackEnd> {
            every { getAuthorizeStub() } returns "I am a Discord authorize stub"
        }
        mockHere<LinkMicrosoftBackEnd> {
            every { getAuthorizeStub() } returns "I am a Microsoft authorize stub"
        }
        withTestEpiLink {
            val call = handleRequest(HttpMethod.Get, "/api/v1/meta/info")
            call.assertStatus(HttpStatusCode.OK)
            val data = fromJson<ApiSuccess>(call.response).data
            assertEquals("EpiLink Test Instance", data["title"])
            assertEquals(null, data.getValue("logo"))
            assertEquals("I am a Discord authorize stub", data.getString("authorizeStub_discord"))
            assertEquals("I am a Microsoft authorize stub", data.getString("authorizeStub_msft"))
        }
    }

    private fun <R> withTestEpiLink(block: TestApplicationEngine.() -> R): R =
        withTestApplication({
            with(get<LinkBackEnd>()) {
                epilinkApiModule()
            }
        }, block)

    private inline fun <reified T : Any> mockHere(crossinline body: T.() -> Unit) : T =
        declare { mockk(block = body) }
}

private fun TestApplicationCall.assertStatus(status: HttpStatusCode) {
    assertEquals(status, this.response.status())
}

object DummySessionStorageProvider : SessionStorageProvider, SessionStorage {
    override fun createStorage(prefix: String): SessionStorage {
        return this
    }

    override suspend fun start() {
        throw NotImplementedError()
    }

    override suspend fun invalidate(id: String) {
        throw NotImplementedError()
    }

    override suspend fun <R> read(id: String, consumer: suspend (ByteReadChannel) -> R): R {
        throw NotImplementedError()
    }

    override suspend fun write(id: String, provider: suspend (ByteWriteChannel) -> Unit) {
        throw NotImplementedError()
    }
}

private fun <K, V : Any?> Map<K, V>.getString(key: K): String =
    this.getValue(key) as String

inline fun <reified T> fromJson(response: TestApplicationResponse): T {
    return jacksonObjectMapper().readValue(response.content!!)
}