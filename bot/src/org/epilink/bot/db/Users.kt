package org.epilink.bot.db

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.`java-time`.datetime

/**
 * The Users table of the database, that contains basic identity information
 * on each user.
 *
 * @see User
 */
object Users : IntIdTable() {
    /**
     * The Discord ID of the user
     */
    val discordId = varchar("discordId", 32).uniqueIndex()

    /**
     * The SHA256 hash of the User's Microsoft ID
     */
    val msftIdHash = binary("msftIdHash", 64)

    /**
     * The creation date of the user's account
     */
    val creationDate = datetime("creationDate")
}

/**
 * The User DAO class that is used as a view on a User row. Also contains an
 * optional back reference to the true identity of the user (or null if that is
 * not there).
 *
 * @see Users
 */
class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(
        Users
    )

    /**
     * The Discord ID of the user
     */
    var discordId by Users.discordId

    /**
     * The SHA256 hash of the User's Microsoft ID
     */
    var msftIdHash by Users.msftIdHash

    /**
     * The date on which the account was created
     */
    var creationDate by Users.creationDate

    /**
     * An [optional back reference][optionalBackReferencedOn] to the user's true
     * identity.
     *
     * This is null if the user does not have their true identity stored, or
     * non-null if they have a true identity recorded in the system.
     *
     * The value is lazily-loaded.
     *
     * @see TrueIdentities
     */
    val trueIdentity by TrueIdentity optionalBackReferencedOn TrueIdentities.user
}