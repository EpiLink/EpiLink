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

class LinkServerDatabase(cfg: LinkConfiguration) {

    private val db: Database =
        Database.connect("jdbc:sqlite:${cfg.db}", driver = "org.sqlite.JDBC")
            .apply {
                transactionManager.defaultIsolationLevel =
                    Connection.TRANSACTION_SERIALIZABLE
            }

    init {
        // Create the tables if they do not already exist
        transaction(db) {
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

object Users : IntIdTable() {
    val discordId = varchar("discordId", 32)
    val msftIdHash = binary("msftIdHash", 64)
    val creationDate = datetime("creationDate")
}

class User(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<User>(Users)
    var discordId by Users.discordId
    var msftIdHash by Users.msftIdHash
    var creationDate by Users.creationDate
}

object TrueIdentities : IntIdTable() {
    val user = reference("user", Users).uniqueIndex()
    // Maximum size of a valid email address is 254 characters, see
    // https://www.rfc-editor.org/errata_search.php?rfc=3696&eid=1690
    val email = varchar("email", 254)
}

class TrueIdentity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TrueIdentity>(TrueIdentities)

    var user by User referencedOn TrueIdentities.user
    var email by TrueIdentities.email
}