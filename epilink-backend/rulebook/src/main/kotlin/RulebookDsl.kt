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
 * Marks some type or function as part of the Rulebook DSL API
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
annotation class RulebookDsl

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
@RulebookDsl
typealias EmailValidator = suspend (String) -> Boolean

/**
 * A rule name with a cache duration, only intended for use in the DSL
 */
data class NameWithDuration(
    /**
     * The rule name
     */
    val name: String,
    /**
     * The cache duration
     */
    val duration: Duration
)

/**
 * Create a name with a duration couple that can be used to create cache-able rules
 */
infix fun String.cachedFor(duration: Duration) = NameWithDuration(this, duration)

/**
 * A function for creating rulebooks within Kotlin code. Not for use within rulebook files.
 */
@RulebookDsl
fun rulebook(block: RulebookBuilder.() -> Unit): Rulebook =
    RulebookBuilder().apply(block).buildRulebook()
