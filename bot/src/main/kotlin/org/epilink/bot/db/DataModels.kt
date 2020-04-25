/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.db

import java.time.Instant

/**
 * Represents a user of EpiLink
 */
interface LinkUser {
    /**
     * The user's Discord ID
     */
    val discordId: String

    /**
     * The SHA-256 hash of the user's Microsoft ID
     */
    val msftIdHash: ByteArray

    /**
     * The time at which the user's account was created
     */
    val creationDate: Instant
}

/**
 * Represents a ban
 */
interface LinkBan {
    /**
     * The banned user's Microsoft ID SHA-256 hash
     */
    val msftIdHash: ByteArray

    /**
     * The time at which the ban expires, or null if the ban does not expire
     */
    val expiresOn: Instant?

    /**
     * The time at which the ban was created
     */
    val issued: Instant
}

/**
 * Represents a logged identity access
 */
interface LinkIdentityAccess {
    /**
     * The name of the author
     */
    val authorName: String

    /**
     * The reason for the identity access
     */
    val reason: String

    /**
     * The time at which the identity access happened
     */
    val timestamp: Instant

    /**
     * True if the identity access was conducted by a bot, false otherwise
     */
    val automated: Boolean
}