/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

/**
 * The standard roles which are attributed by EpiLink automatically
 *
 * @property roleName The name of the role as it can be used in the configuration
 */
enum class StandardRoles(val roleName: String) {
    /**
     * A user who has his true identity stored in the database
     */
    Identified("_identified"),

    /**
     * A user who is authenticated through EpiLink
     */
    Known("_known"),

    /**
     * Used for representing a "no role" scenario when an empty collection is not appropriate. In those cases, this
     * should be the only element of the collection.
     */
    None("_none")
}