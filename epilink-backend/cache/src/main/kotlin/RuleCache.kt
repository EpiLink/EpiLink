/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.cache

import java.time.Duration

interface RuleCache {
    /**
     * Attempts to hit the cache with the given rule and Discord ID.
     *
     * - On success, returns a [CacheResult.Hit] with the cached result
     * - On failure, returns [CacheResult.NotFound].
     */
    suspend fun tryCache(ruleName: String, discordId: String): CacheResult

    /**
     * Invalidates all cached rule results for the given discordId, effectively "forgetting" about all of them.
     */
    suspend fun invalidateCache(discordId: String)

    suspend fun putCache(ruleName: String, cacheDuration: Duration, discordId: String, roles: List<String>)
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

class NoCacheRuleCache : RuleCache {

    override suspend fun invalidateCache(discordId: String) {
        // Does nothing, we don't store any cache anyway
    }

    override suspend fun putCache(ruleName: String, cacheDuration: Duration, discordId: String, roles: List<String>) {
        // Does nothing, we don't store any cache
    }

    /**
     * Always fails because we do not store cache
     */
    override suspend fun tryCache(ruleName: String, discordId: String): CacheResult = CacheResult.NotFound
}
