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
import org.epilink.bot.rulebook.WeakIdentityRule
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
     * This method returns an empty list when an error is encountered when executing the rule (i.e. the rule is ignored).
     *
     * @param rule The rule to run
     * @param discordId The Discord ID of the user to update
     * @param discordName The Discord username of the user (without the discriminator)
     * @param discordDisc The Discord discriminator of the user
     * @param identity The identity of the user, or null if the user is not identifiable
     */
    suspend fun runRule(
        rule: Rule,
        discordId: String,
        discordName: String,
        discordDisc: String,
        identity: String?
    ): List<String>

    /**
     * Invalidates all cached rule results for the given discordId, effectively "forgetting" about all of them.
     */
    suspend fun invalidateCache(discordId: String)
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
    ): List<String> =
        rule.runCatching {
            when {
                this is WeakIdentityRule ->
                    determineRoles(discordId, discordName, discordDisc)
                this is StrongIdentityRule && identity != null ->
                    determineRoles(discordId, discordName, discordDisc, identity)
                else ->
                    listOf()
            }
        }.getOrElse { ex ->
            logger.error("Failed to apply rule ${rule.name} due to an unexpected exception.", ex)
            listOf()
        }

    override suspend fun invalidateCache(discordId: String) {
        // Does nothing, we don't store any cache anyway
    }
}