/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.rulebook

import java.time.Duration

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
        addWeakRule(this, null, ruleDeterminer)
    }

    /**
     * Create a strong-identity rule. Throws an error in case of conflicting rule names.
     */
    @RulebookDsl
    operator fun String.rem(ruleDeterminerWithIdentity: RuleDeterminerWithIdentity) {
        addStrongRule(this, null, ruleDeterminerWithIdentity)
    }

    /**
     * Create a cache-able weak-identity rule. Throws an error in case of conflicting rule names.
     */
    @RulebookDsl
    operator fun NameWithDuration.invoke(ruleDeterminer: RuleDeterminer) {
        addWeakRule(this.name, this.duration, ruleDeterminer)
    }

    /**
     * Create a cache-able strong-identity rule. Throws an error in case of conflicting rule names.
     */
    @RulebookDsl
    operator fun NameWithDuration.rem(ruleDeterminerWithIdentity: RuleDeterminerWithIdentity) {
        addStrongRule(this.name, this.duration, ruleDeterminerWithIdentity)
    }

    private fun addWeakRule(name: String, cacheDuration: Duration?, ruleDeterminer: RuleDeterminer) {
        if (builtRules.containsKey(name)) {
            error("Duplicate rule names: $name was defined more than once")
        }
        builtRules[name] = WeakIdentityRule(name, cacheDuration, ruleDeterminer)
    }

    private fun addStrongRule(name: String, cacheDuration: Duration?, ruleDeterminer: RuleDeterminerWithIdentity) {
        if (builtRules.containsKey(name)) {
            error("Duplicate rule names: $name was defined more than once")
        }
        builtRules[name] =
            StrongIdentityRule(name, cacheDuration, ruleDeterminer)
    }

    /**
     * Define an e-mail validator. The lambda will be called for every e-mail address and, if it returns false, the
     * Identity Provider account will be rejected.
     */
    @RulebookDsl
    @Suppress("unused")
    fun emailValidator(v: EmailValidator) {
        if (validator == null) {
            validator = v
        } else {
            error("The e-mail validator was already defined elsewhere")
        }
    }

    /**
     * Actually build a rulebook from this builder and return it.
     */
    internal fun buildRulebook(): Rulebook {
        return Rulebook(builtRules, validator)
    }
}
