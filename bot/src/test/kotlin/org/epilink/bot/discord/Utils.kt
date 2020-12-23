package org.epilink.bot.discord

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.epilink.bot.KoinBaseTest
import org.epilink.bot.config.LinkDiscordConfig
import org.epilink.bot.config.LinkDiscordServerSpec
import org.koin.test.mock.declare

/**
 * Mocks the default behavior for the "get message" feature of the given LinkDiscordMessagesI18n: it will return the
 * second argument (the i18n key)
 */
fun LinkDiscordMessagesI18n.defaultMock() {
    val keySlot = slot<String>()
    every { get(any(), capture(keySlot)) } answers { keySlot.captured }
}

// TODO doc

class MockServerConfigBuilder {
    class MockServerBuilder(private val id: String) {
        private val builtRoles = mutableMapOf<String, String>()
        private val builtRequires = mutableListOf<String>()
        private val builtStickyRoles: MutableList<String> = mutableListOf()
        var enableWelcomeMessage: Boolean? = null
        var welcomeEmbed: DiscordEmbed? = null

        infix fun String.boundTo(boundTo: String) {
            builtRoles += this to boundTo
        }

        fun requires(vararg rules: String) {
            builtRequires += rules
        }

        fun stickyRoles(vararg roles: String) {
            builtStickyRoles += roles
        }

        fun build(): LinkDiscordServerSpec = mockk {
            every { this@mockk.id } returns this@MockServerBuilder.id
            every { this@mockk.roles } returns builtRoles
            every { this@mockk.stickyRoles } returns builtStickyRoles
            every { this@mockk.requires } returns builtRequires
            every { this@mockk.welcomeEmbed } returns this@MockServerBuilder.welcomeEmbed
            val builtEnableWelcomeMessage = this@MockServerBuilder.enableWelcomeMessage
            if (builtEnableWelcomeMessage != null)
                every { this@mockk.enableWelcomeMessage } returns builtEnableWelcomeMessage
        }
    }

    private val builtServers = mutableListOf<LinkDiscordServerSpec>()
    private val builtStickyRoles = mutableListOf<String>()
    var welcomeUrl: String? = null

    fun server(id: String, roles: MockServerBuilder.() -> Unit = {}) =
        MockServerBuilder(id).apply(roles).build().also { builtServers += it }


    fun stickyRoles(vararg roles: String) {
        builtStickyRoles += roles
    }

    fun build(): LinkDiscordConfig = mockk {
        every { servers } returns builtServers
        every { stickyRoles } returns builtStickyRoles
        val builtWelcomeUrl = this@MockServerConfigBuilder.welcomeUrl
        if (builtWelcomeUrl != null)
            every { welcomeUrl } returns builtWelcomeUrl
    }
}

// TODO doc
fun KoinBaseTest<*>.mockDiscordConfigHere(builder: MockServerConfigBuilder.() -> Unit) =
    declare { mockDiscordConfig(builder) }

fun mockDiscordConfig(builder: MockServerConfigBuilder.() -> Unit) =
    MockServerConfigBuilder().apply(builder).build()