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
 * The rulebook: a collection of rules
 */
class Rulebook(
    /**
     * Contains the rules. Maps a rule name to the actual rule object.
     */
    val rules: Map<String, Rule>,
    /**
     * Function used to validate email addresses
     */
    val validator: EmailValidator?
)
