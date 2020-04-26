/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.rulebook

import org.epilink.bot.debug
import org.slf4j.LoggerFactory
import kotlin.script.experimental.annotations.KotlinScript

/*
 * Implementation of the Rulebook Kotlin DSL + script system
 */

private val logger = LoggerFactory.getLogger("epilink.rules")

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
    val rules: Map<String, Rule>,
    /**
     * Function used to validate email addresses
     */
    val validator: EmailValidator?
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
        try {
            val ctx = RuleContext(discordId, discordName, discordDiscriminator, mutableListOf())
            logger.debug { "Running rule $name with context $ctx" }
            ctx.roles()
            return ctx.roles
        } catch (ex: Exception) {
            if (ex is RuleException)
                throw ex
            else
                throw RuleException("Encountered an exception inside a rule", ex)
        }
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
        try {
            val ctx = RuleContext(discordId, discordName, discordDiscriminator, mutableListOf())
            logger.debug { "Running rule $name with context $ctx and email $email" }
            ctx.roles(email)
            return ctx.roles
        } catch (ex: Exception) {
            if (ex is RuleException)
                throw ex
            else
                throw RuleException("Encountered an exception inside a rule", ex)
        }
    }
}

/**
 * Type signature for the weak identity rules' lambdas
 */
@RulebookDsl
typealias RuleDeterminer = suspend RuleContext.() -> Unit

/**
 * Type signature fo the strong identity rules' lambdas
 */
@RulebookDsl
typealias RuleDeterminerWithIdentity = suspend RuleContext.(String) -> Unit

/**
 * E-mail address validation type
 */
typealias EmailValidator = suspend (String) -> Boolean

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
    private var validator: EmailValidator? = null

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
     * Define an e-mail validator. The lambda will be called for every e-mail address and, if it returns false, the
     * Microsoft account will be rejected.
     */
    @RulebookDsl
    @Suppress("unused")
    fun emailValidator(v: EmailValidator) {
        if (validator == null)
            validator = v
        else
            error("The e-mail validator was already defined elsewhere")
    }

    /**
     * Actually build a rulebook from this builder and return it.
     */
    internal fun buildRulebook(): Rulebook {
        return Rulebook(builtRules, validator)
    }
}

/**
 * Class for exceptions that happen inside a rule
 */
class RuleException(message: String, cause: Throwable) : Exception(message, cause)