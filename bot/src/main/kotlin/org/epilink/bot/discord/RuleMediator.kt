/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import org.epilink.bot.rulebook.Rule
import org.epilink.bot.rulebook.StrongIdentityRule
import org.epilink.bot.rulebook.run
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("epilink.rulemediator")

/**
 * A rule mediator manages the execution of rules, and may cache their results according to each rule's settings.
 *
 * Whether the rules are actually cached or not depends on the implementation
 */
interface RuleMediator {
    /**
     * Runs the given rules with the given parameters.
     *
     * If the rule is a [StrongIdentityRule] and the identity is null, returns an empty list.
     *
     * The results may be returned from a cache.
     *
     * This method returns an empty list when an error is encountered when executing the rule (i.e. the rule is
     * ignored).
     *
     * @param rule The rule to run
     * @param discordId The Discord ID of the user to update
     * @param discordName The Discord username of the user (without the discriminator)
     * @param discordDisc The Discord discriminator of the user
     * @param identity The identity of the user, or null if the user is not identifiable
     * @see org.epilink.bot.rulebook.run
     */
    suspend fun runRule(
        rule: Rule,
        discordId: String,
        discordName: String,
        discordDisc: String,
        identity: String?
    ): RuleResult

    /**
     * Attempts to hit the cache with the given rule and Discord ID.
     *
     * - On success, returns a [CacheResult.Hit] with the cached result
     * - On failure, returns [CacheResult.NotFound].
     */
    suspend fun tryCache(rule: Rule, discordId: String): CacheResult

    /**
     * Invalidates all cached rule results for the given discordId, effectively "forgetting" about all of them.
     */
    suspend fun invalidateCache(discordId: String)
}

/**
 * Represents a result of the [RuleMediator.tryCache] operation.
 */
sealed class CacheResult {
    /**
     * Represents a successful "cache hit". The cache returned a non-expired result for the given rule and role.
     *
     * @property roles The cached roles, i.e. the cached result of the rule
     */
    class Hit(val roles: List<String>) : CacheResult()

    /**
     * Represents an unsuccessful cache hit attempt.
     */
    object NotFound : CacheResult()
}

/**
 * Represents the result of a rule execution
 */
sealed class RuleResult {
    /**
     * Returned if the rule execution succeeded
     *
     * @parma roles The roles computed by the rule
     */
    data class Success(val roles: List<String>) : RuleResult()

    /**
     * Returned if the rule execution failed
     *
     * @param exception The exception that stopped the rule execution
     */
    class Failure(val exception: Throwable) : RuleResult()
}

/**
 * A rule mediator implementation that does not use a cache at all.
 */
class NoCacheRuleMediator : RuleMediator {
    override suspend fun runRule(
        rule: Rule,
        discordId: String,
        discordName: String,
        discordDisc: String,
        identity: String?
    ): RuleResult =
        rule.runCatching { RuleResult.Success(run(discordId, discordName, discordDisc, identity)) }.getOrElse { ex ->
            logger.error("Failed to apply rule ${rule.name} due to an unexpected exception.", ex)
            RuleResult.Failure(ex)
        }

    override suspend fun invalidateCache(discordId: String) {
        // Does nothing, we don't store any cache anyway
    }

    /**
     * Always fails because we do not store cache
     */
    override suspend fun tryCache(rule: Rule, discordId: String): CacheResult = CacheResult.NotFound
}
