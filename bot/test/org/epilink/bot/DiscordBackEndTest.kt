package org.epilink.bot

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.mockk.mockk
import org.epilink.bot.http.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.test.mock.declare
import kotlin.test.*

class DiscordBackEndTest : KoinTest {
    @BeforeTest
    fun setupKoin() {
        startKoin {
            modules(module {
                single { LinkDiscordBackEnd("Discord Client Id", "Discord Secret") }
            })
        }
    }

    @AfterTest
    fun tearDownKoin() {
        stopKoin()
    }

    @Test
    fun `Test Discord auth stub`() {
        val dbe = get<LinkDiscordBackEnd>()
        dbe.getAuthorizeStub().apply {
            assertTrue(contains("client_id=Discord Client Id"), "Expected a client ID")
            assertTrue(contains("scope=identify"), "Expected the scope to be set to identify")
            assertTrue(contains("response_type=code"), "Expected the response type to be code")
            assertTrue(contains(Regex("scope=identify[&$]")), "Expected identify to be the only scope")
            assertTrue(contains("prompt=consent"), "Expected prompt to be set to consent")
            assertFalse(contains("redirect_uri"), "Expected redirect_uri to be absent")
        }
    }
}