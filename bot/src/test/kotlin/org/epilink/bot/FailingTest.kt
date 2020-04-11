package org.epilink.bot

import kotlin.test.*

class FailingTest {
    @Test
    fun `Fake failing test`() {
        error("Oops!")
    }
}