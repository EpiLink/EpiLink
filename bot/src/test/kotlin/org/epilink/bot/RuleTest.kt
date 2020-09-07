package org.epilink.bot

import kotlinx.coroutines.runBlocking
import org.epilink.bot.rulebook.RuleException
import org.epilink.bot.rulebook.StrongIdentityRule
import org.epilink.bot.rulebook.WeakIdentityRule
import kotlin.test.*

class RuleTest {
    @Test
    fun `Test WIR normal`() = runBlocking {
        val r = WeakIdentityRule("a", null) {
            roles += listOf("id=$userDiscordId", "name=$userDiscordName", "disc=$userDiscordDiscriminator")
        }.determineRoles("did", "dname", "ddisc")
        assertEquals(listOf("id=did", "name=dname", "disc=ddisc"), r)
    }

    @Test
    fun `Test WIR fail`(): Unit = runBlocking {
        assertFailsWith<RuleException> {
            WeakIdentityRule("a", null) { error("oof") }.determineRoles("a", "b", "c")
        }
    }

    @Test
    fun `Test SIR normal`() = runBlocking {
        val r = StrongIdentityRule("b", null) {
            roles += listOf("id=$userDiscordId", "name=$userDiscordName", "disc=$userDiscordDiscriminator", "email=$it")
        }.determineRoles("did", "dname", "ddisc", "mail@mail.mail")
        assertEquals(listOf("id=did", "name=dname", "disc=ddisc", "email=mail@mail.mail"), r)
    }

    @Test
    fun `Test SIR fail`(): Unit = runBlocking {
        assertFailsWith<RuleException> {
            StrongIdentityRule("b", null) { error("oof2") }.determineRoles("a", "b", "c", "d")
        }
    }
}