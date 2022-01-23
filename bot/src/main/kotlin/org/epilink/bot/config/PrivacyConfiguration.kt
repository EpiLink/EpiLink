/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.config

/**
 * Privacy options for EpiLink, related to how some notifications should be handled.
 *
 * @see Configuration.privacy
 */
data class PrivacyConfiguration(
    /**
     * True if automated accesses should send a notification
     */
    val notifyAutomatedAccess: Boolean = false,
    /**
     * True if human accesses should send a notification
     */
    val notifyHumanAccess: Boolean = true,
    /**
     * True if the name of the person who requested the identity access should be disclosed.
     */
    val discloseHumanRequesterIdentity: Boolean = false,
    /**
     * True if a ban should trigger a notification, false otherwise.
     *
     * Bans will not send a notification if banning someone who is not known.
     */
    val notifyBans: Boolean = true
) {
    /**
     * Whether an access should be notified, based on this object's configuration
     *
     * @param automated True if the access was automated
     */
    fun shouldNotify(automated: Boolean): Boolean =
        if (automated) notifyAutomatedAccess else notifyHumanAccess

    /**
     * Whether the identity of the requester should be disclosed, based on this object's configuration.
     */
    fun shouldDiscloseIdentity(automated: Boolean): Boolean =
        if (automated) true else discloseHumanRequesterIdentity
}
