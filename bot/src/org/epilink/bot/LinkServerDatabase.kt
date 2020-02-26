package org.epilink.bot

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.`java-time`.datetime
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import java.sql.Connection

/**
 * The class that manages the database and handles all business logic.
 *
 * Most of the functions declared here are suspending functions intended to be used
 * from within Ktor responses.
 */
class LinkServerDatabase(cfg: LinkConfiguration) {
    /**
     * The Database instance managed by JetBrains Exposed used for transactions.
     */
    private val db: Database =
        Database.connect("jdbc:sqlite:${cfg.db}", driver = "org.sqlite.JDBC")
            .apply {
                // Required for SQLite
                transactionManager.defaultIsolationLevel =
                    Connection.TRANSACTION_SERIALIZABLE

                // Create the tables if they do not already exist
                transaction(this) {
                    SchemaUtils.create(Users, TrueIdentities)
                }
            }

    /*
     * All functions regarding user creation, deletion, retrieval etc., will be
     * added here as more high level functions. The business logic (e.g.
     * checking for bans, checking account validity...) will be done here. Only
     * code within this class is allowed to directly interact with the database.
     *
     * Most functions will be suspending functions, as Ktor uses coroutines.
     * (newSuspendedTransaction will be used to call JB Exposed stuff).
     */
    suspend fun countUsers(): Int =
        newSuspendedTransaction {
            User.count()
        }
}

// Representation of DB tables and their associated DAO classes

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
    companion object : IntEntityClass<User>(Users)

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
class TrueIdentity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TrueIdentity>(TrueIdentities)

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
    companion object : IntEntityClass<Ban>(Bans)

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
