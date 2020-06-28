/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import io.ktor.sessions.SessionStorage
import io.ktor.sessions.SessionStorageMemory
import org.epilink.bot.discord.NoCacheRuleMediator
import org.epilink.bot.discord.RuleMediator

/**
 * A general interface for implementations that have the ability to provide caches for sessions and rule storage
 */
interface CacheClient {
    /**
     * Start this client
     */
    suspend fun start()

    /**
     * Create a new rule mediator that will use this client for storage
     */
    fun newRuleMediator(prefix: String): RuleMediator

    /**
     * Create a new session storage that will use this client for storage
     */
    fun newSessionStorage(prefix: String): SessionStorage
}

/**
 * A Cache client based entirely on memory. Must only be used for development purposes.
 *
 * - Rule caching is disabled ([NoCacheRuleMediator])
 * - Sessions are stored in-memory.
 */
class MemoryCacheClient : CacheClient {
    override suspend fun start() {
        // Does nothing
    }

    override fun newRuleMediator(prefix: String): RuleMediator {
        return NoCacheRuleMediator()
    }

    override fun newSessionStorage(prefix: String): SessionStorage {
        return SessionStorageMemory()
    }

}

