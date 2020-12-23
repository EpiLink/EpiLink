package org.epilink.bot.discord

import io.mockk.every
import io.mockk.slot

/**
 * Mocks the default behavior for the "get message" feature of the given LinkDiscordMessagesI18n: it will return the
 * second argument (the i18n key)
 */
fun LinkDiscordMessagesI18n.defaultMock() {
    val keySlot = slot<String>()
    every { get(any(), capture(keySlot)) } answers { keySlot.captured }
}
