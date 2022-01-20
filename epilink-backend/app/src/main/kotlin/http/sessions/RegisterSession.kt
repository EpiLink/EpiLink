/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.http.sessions

/**
 * Session object that contains information about a registration process
 */
data class RegisterSession(
    /**
     * The Identity Provider ID to be used
     */
    val idpId: String? = null,
    /**
     * The Identity Provider email to be used
     */
    val email: String? = null,
    /**
     * The Discord ID to be used
     */
    val discordId: String? = null,
    /**
     * The Discord username to be used
     */
    val discordUsername: String? = null,
    /**
     * The Discord avatar url to be used
     */
    val discordAvatarUrl: String? = null
)
