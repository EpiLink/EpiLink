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
     * Opaque identifier for this ban
     */
    val banId: Int

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

    /**
     * True if the ban is revoked and should be ignored, false otherwise
     */
    val revoked: Boolean

    /**
     * The name (or email address) of the person who created the ban.
     *
     * This value is only here for administrative purposes and is not displayed to the user
     */
    val author: String

    /**
     * The human-readable reason for the ban. May be displayed to the user.
     */
    val reason: String
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

/**
 * A language preference for a specific Discord user
 */
interface LinkDiscordLanguage {
    /**
     * The Discord ID of the user this language preference is referring to.
     */
    val discordId: String

    /**
     * The preferred language ID. This should be checked for validity when retrieved.
     */
    val language: String
}