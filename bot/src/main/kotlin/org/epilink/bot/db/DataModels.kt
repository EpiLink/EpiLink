package org.epilink.bot.db

import java.time.LocalDateTime

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
    val creationDate: LocalDateTime
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
    val expiresOn: LocalDateTime?
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
    val timestamp: LocalDateTime

    /**
     * True if the identity access was conducted by a bot, false otherwise
     */
    val automated: Boolean
}