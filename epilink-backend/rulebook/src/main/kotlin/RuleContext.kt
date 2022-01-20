/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.rulebook

/**
 * The context object that is passed to rule determiners both to provide information about the request
 * and provide a way to add roles
 */
data class RuleContext(
    /**
     * The ID of the Discord user
     */
    val userDiscordId: String,
    /**
     * The name of the Discord user (e.g. the username in username#1234)
     */
    val userDiscordName: String,
    /**
     * The discriminator of the Discord user (e.g. the numbers in username#1234)
     */
    val userDiscordDiscriminator: String,
    /**
     * The list of roles that should be filled by this rule
     */
    val roles: MutableList<String>
)
