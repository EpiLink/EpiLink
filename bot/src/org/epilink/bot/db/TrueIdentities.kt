package org.epilink.bot.db

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

/**
 * The TrueIdentities table of the database, used to store a reference to the
 * User's information and their e-mail address.
 *
 * This is used in a separate table to ensure that access to users' email
 * address is always a special case, and never just an accidental access.
 *
 * Note that the [trueIdentity field of the User DAO][User.trueIdentity] class
 * is lazily loaded. The data is not fetched in the database unless we actually
 * explicitly ask for it (i.e. doing user.trueIdentity).
 *
 * @see TrueIdentity
 */
@UsesTrueIdentity
object TrueIdentities : IntIdTable() {
    /**
     * A reference (by ID) to the user this true identity belongs to
     */
    val user = reference("user", Users).uniqueIndex()

    /**
     * The e-mail associated to this user.
     *
     * Maximum size of a valid email address is 254 characters, see
     * https://www.rfc-editor.org/errata_search.php?rfc=3696&eid=1690
     */
    val email = varchar("email", 254)
}

/**
 * The DAO class associated to the user's identity.
 *
 * @see [TrueIdentities]
 */
@UsesTrueIdentity
class TrueIdentity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TrueIdentity>(
        TrueIdentities
    )

    /**
     * The user (lazily loaded by JB Exposed)
     *
     * @see TrueIdentities.user
     */
    var user by User referencedOn TrueIdentities.user

    /**
     * Their email address
     *
     * @see TrueIdentities.email
     */
    var email by TrueIdentities.email
}