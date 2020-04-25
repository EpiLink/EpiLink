/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.db

/**
 * The database's opinion on an action. The exact semantics of what this means depend on the context.
 */
sealed class DatabaseAdvisory

/**
 * The action that is checked would be semantically allowed.
 */
object Allowed : DatabaseAdvisory()

/**
 * The action that is checked would be semantically disallowed for the given reason. The reason must be a end-user
 * friendly string.
 *
 * @property reason The explanation behind the disallowed advice. User-friendly.
 */
class Disallowed(val reason: String): DatabaseAdvisory()
