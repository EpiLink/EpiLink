package org.epilink.bot.db

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.`java-time`.datetime

/**
 * The table for all of the bans.
 *
 * @see Ban
 */
object Bans : IntIdTable() {
    /**
     * The hashed (SHA256) Microsoft ID of the banned user.
     *
     * We do not directly link to the user here to ensure that a user can delete
     * their account (i.e. delete the row of that user in the [Users] table)
     * while still maintaining the information that this Microsoft ID is banned.
     */
    var msftIdHash = binary("msftIdHash", 64)

    /**
     * Null if this is a definitive ban, the expiration date if this is a
     * temporary ban.
     */
    var expiresOn = datetime("expiresOn").nullable()
}

/**
 * The DAO class for [Bans]
 *
 * @see Bans
 */
class Ban(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Ban>(
        Bans
    )

    /**
     * The SHA256 hash of the Microsoft ID of the banned user.
     *
     * @see Bans.msftIdHash
     */
    var msftIdHash by Bans.msftIdHash

    /**
     * The expiration date of a temporary ban, or null for a definitive ban.
     *
     * @see Bans.expiresOn
     */
    var expiresOn by Bans.expiresOn
}
