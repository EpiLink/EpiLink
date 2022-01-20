/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.rulebook

import org.epilink.backend.common.debug
import org.slf4j.LoggerFactory
import java.time.Duration

private val logger = LoggerFactory.getLogger("epilink.rules")

/**
 * Root class for rules.
 */
sealed class Rule(
    /**
     * The name of this rule
     */
    val name: String,
    /**
     * The amount of time the results returned by this rule should be cached for, or null if the results
     * should not be cached.
     */
    val cacheDuration: Duration?
)

/**
 * Class for rules that require access to the identity of the user
 */
// The entire point here is to wrap generic exceptions into proper rule exceptions
@Suppress("TooGenericExceptionCaught")
class StrongIdentityRule(
    name: String,
    cacheDuration: Duration?,
    private val roles: RuleDeterminerWithIdentity
) : Rule(name, cacheDuration) {
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
        } catch (ex: RuleException) {
            @Suppress("RethrowCaughtException")
            throw ex
        } catch (ex: Exception) {
            throw RuleException("Encountered an exception inside a rule", ex)
        }
    }
}

/**
 * Class for rules that do not require access to the identity of the user
 */
// The entire point here is to wrap generic exceptions into proper rule exceptions
@Suppress("TooGenericExceptionCaught")
class WeakIdentityRule(
    name: String,
    cacheDuration: Duration?,
    private val roles: RuleDeterminer
) : Rule(name, cacheDuration) {
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
        } catch (ex: RuleException) {
            @Suppress("RethrowCaughtException")
            throw ex
        } catch (ex: Exception) {
            throw RuleException("Encountered an exception inside a rule", ex)
        }
    }
}

/**
 * Run a rule directly. Always runs the rule if the rule is a weak identity rule. If the rule is a strong identity rule,
 * it is only ran when identity is not null, otherwise it is not ran and an empty list is returned. May throw any
 * exception, you should use this with runCatching.
 */
suspend fun Rule.run(discordId: String, discordName: String, discordDisc: String, identity: String?): List<String> =
    when {
        this is WeakIdentityRule ->
            determineRoles(discordId, discordName, discordDisc)
        this is StrongIdentityRule && identity != null ->
            determineRoles(discordId, discordName, discordDisc, identity)
        else -> listOf()
    }
