package org.epilink.bot.config.rulebook

import org.epilink.bot.db.UsesTrueIdentity
import kotlin.script.experimental.annotations.KotlinScript

/*
 * Implementation of the Rulebook Kotlin DSL + script system
 */

@KotlinScript(
    displayName = "EpiLink Rulebook",
    fileExtension = "rules.kts"
)
abstract class RulebookScript

data class RuleContext(
    val userDiscordId: String,
    val userDiscordName: String,
    val userDiscordDiscriminator: String,
    val roles: MutableList<String>
)

class Rulebook(
    val rules: Map<String, Rule>
)

sealed class Rule(val name: String)

class WeakIdentityRule(
    name: String,
    private val roles: RuleDeterminer
) : Rule(name) {
    suspend fun determineRoles(
        discordId: String,
        discordName: String,
        discordDiscriminator: String
    ): List<String> {
        val ctx = RuleContext(discordId, discordName, discordDiscriminator, mutableListOf())
        ctx.roles()
        return ctx.roles
    }
}

class StrongIdentityRule(
    name: String,
    private val roles: RuleDeterminerWithIdentity
) : Rule(name) {
    suspend fun determineRoles(
        discordId: String,
        discordName: String,
        discordDiscriminator: String,
        email: String
    ): List<String> {
        val ctx = RuleContext(discordId, discordName, discordDiscriminator, mutableListOf())
        ctx.roles(email)
        return ctx.roles
    }
}

@RulebookDsl
typealias RuleDeterminer = suspend RuleContext.() -> Unit

@RulebookDsl
typealias RuleDeterminerWithIdentity = suspend RuleContext.(String) -> Unit

// ******************** DSL ******************** //

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
annotation class RulebookDsl

@RulebookDsl
class RulebookBuilder {
    private val builtRules = mutableMapOf<String, Rule>()

    @RulebookDsl
    operator fun String.invoke(ruleDeterminer: RuleDeterminer) {
        builtRules[this] = WeakIdentityRule(this, ruleDeterminer)
    }

    @RulebookDsl
    operator fun String.rem(ruleDeterminerWithIdentity: RuleDeterminerWithIdentity) {
        builtRules[this] =
            StrongIdentityRule(this, ruleDeterminerWithIdentity)
    }

    internal fun buildRulebook(): Rulebook {
        return Rulebook(builtRules)
    }
}