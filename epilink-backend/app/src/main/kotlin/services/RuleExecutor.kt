/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.services

import org.epilink.backend.cache.CacheResult
import org.epilink.backend.cache.RuleCache
import org.epilink.backend.common.debug
import org.epilink.backend.rulebook.Rule
import org.epilink.backend.rulebook.run
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

interface RuleExecutor {
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
    suspend fun executeRule(
        rule: Rule,
        discordId: String,
        discordName: String,
        discordDisc: String,
        identity: String?
    ): RuleResult
}

/**
 * Represents the result of a rule execution
 */
sealed class RuleResult {
    /**
     * Returned if the rule execution succeeded
     *
     * @param roles The roles computed by the rule
     */
    data class Success(val roles: List<String>) : RuleResult()

    /**
     * Returned if the rule execution failed
     *
     * @param exception The exception that stopped the rule execution
     */
    class Failure(val exception: Throwable) : RuleResult()
}

@OptIn(KoinApiExtension::class)
internal class RuleExecutorImpl : RuleExecutor, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.ruleexecutor")
    private val ruleCache: RuleCache by inject()

    override suspend fun executeRule(
        rule: Rule,
        discordId: String,
        discordName: String,
        discordDisc: String,
        identity: String?
    ): RuleResult {
        return when (val cached = getCachedRoles(rule, discordId)) {
            null -> {
                logger.debug { "No cached results or cache disabled for rule $rule (ID $discordId): running rule" }
                val result = runCatching { rule.run(discordId, discordName, discordDisc, identity) }.getOrElse {
                    logger.error("Encountered error on running rule ${rule.name}", it)
                    return RuleResult.Failure(it)
                }
                rule.cacheDuration?.let { ruleCache.putCache(rule.name, it, discordId, result) }
                RuleResult.Success(result)
            }
            else -> {
                logger.debug { "Returning cached results for ${rule.name} on $discordId: $cached" }
                RuleResult.Success(cached)
            }
        }
    }

    private suspend fun getCachedRoles(rule: Rule, discordId: String): List<String>? {
        if (rule.cacheDuration == null) return null
        return when (val cached = ruleCache.tryCache(rule.name, discordId)) {
            is CacheResult.Hit -> cached.roles
            CacheResult.NotFound -> null
        }
    }
}
