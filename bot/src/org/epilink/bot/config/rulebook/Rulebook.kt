package org.epilink.bot.config.rulebook

import org.epilink.bot.db.UsesTrueIdentity
import kotlin.script.experimental.annotations.KotlinScript

/*
 * Implementation of the Rulebook Kotlin DSL + script system
 */

/**
 * The script class for the Kotlin script template
 */
@KotlinScript(
    displayName = "EpiLink Rulebook",
    fileExtension = "rules.kts"
)
abstract class RulebookScript

/**
 * The context object that is passed to rule determiners both to provide information about the request
 * and provide a way to add roles
 */
data class RuleContext(
    /**
     * The ID of the Discord user
     */
    val userDiscordId: String,
    /**
     * The name of the Discord user (e.g. the username in username#1234)
     */
    val userDiscordName: String,
    /**
     * The discriminator of the Discord user (e.g. the numbers in username#1234)
     */
    val userDiscordDiscriminator: String,
    /**
     * The list of roles that should be filled by this rule
     */
    val roles: MutableList<String>
)

/**
 * The rulebook: a collection of rules
 */
class Rulebook(
    /**
     * Contains the rules. Maps a rule name to the actual rule object.
     */
    val rules: Map<String, Rule>
)

/**
 * Root class for rules.
 */
sealed class Rule(
    /**
     * The name of this rule
     */
    val name: String
)

/**
 * Class for rules that do not require access to the identity of the user
 */
class WeakIdentityRule(
    name: String,
    private val roles: RuleDeterminer
) : Rule(name) {
    /**
     * Execute this rule and return the determined roles
     */
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

/**
 * Class for rules that require access to the identity of the user
 */
class StrongIdentityRule(
    name: String,
    private val roles: RuleDeterminerWithIdentity
) : Rule(name) {
    /**
     * Execute this rule and return the determined roles.
     */
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

/**
 * Type signature for the weak identity rules' lambdas
 */
@RulebookDsl
typealias RuleDeterminer = suspend RuleContext.() -> Unit

/**
 * Type signature fo rthe strong identity rules' lambdas
 */
@RulebookDsl
typealias RuleDeterminerWithIdentity = suspend RuleContext.(String) -> Unit

// ******************** DSL ******************** //

/**
 * Marks some type or function as part of the Rulebook DSL API
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
annotation class RulebookDsl

/**
 * Class that is passed as an implicit receiver to the scripts. Contains the built rules and implements the functions
 * on strings that allow us to declare rules with just their name (as a string) and a lambda.
 */
@RulebookDsl
class RulebookBuilder {
    private val builtRules = mutableMapOf<String, Rule>()

    /**
     * Create a weak-identity rule. Throws an error in case of conflicting rule names.
     */
    @RulebookDsl
    operator fun String.invoke(ruleDeterminer: RuleDeterminer) {
        if (builtRules.containsKey(this)) {
            error("Duplicate rule names: $this was defined more than once")
        }
        builtRules[this] = WeakIdentityRule(this, ruleDeterminer)
    }

    /**
     * Create a strong-identity rule. Throws an error in case of conflicting rule names.
     */
    @RulebookDsl
    operator fun String.rem(ruleDeterminerWithIdentity: RuleDeterminerWithIdentity) {
        if (builtRules.containsKey(this)) {
            error("Duplicate rule names: $this was defined more than once")
        }
        builtRules[this] =
            StrongIdentityRule(this, ruleDeterminerWithIdentity)
    }

    /**
     * Actually build a rulebook from this builder and return it.
     */
    internal fun buildRulebook(): Rulebook {
        return Rulebook(builtRules)
    }
}