package org.epilink.bot.db.exposed

import org.epilink.bot.LinkException
import org.epilink.bot.db.*
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.`java-time`.timestamp
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant

// This file is rather long because all of the DAO classes are private in order to make sure that only the facade
// has access to them

/**
 * Implementation of an EpiLink database facade using the JetBrains Exposed library
 */
abstract class ExposedDatabaseFacade : LinkDatabaseFacade {
    /**
     * The Database instance managed by JetBrains Exposed used for transactions.
     */
    internal abstract val db: Database

    @OptIn(UsesTrueIdentity::class) // Creation of TrueIdentities table
    override suspend fun start() {
        newSuspendedTransaction(db = db) {
            SchemaUtils.create(
                ExposedUsers,
                ExposedTrueIdentities,
                ExposedBans, ExposedIdentityAccesses
            )
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
     *
     * Here's for example a function that counts the number of users in the database
     *
     *     suspend fun countUsers(): Int =
     *         newSuspendedTransaction(db = db) {
     *             User.count()
     *         }
     */

    override suspend fun getUser(discordId: String): LinkUser? =
        newSuspendedTransaction(db = db) {
            ExposedUser.find { ExposedUsers.discordId eq discordId }.firstOrNull()
        }

    override suspend fun doesUserExist(discordId: String): Boolean =
        newSuspendedTransaction(db = db) {
            ExposedUser.count(ExposedUsers.discordId eq discordId) > 0
        }

    override suspend fun isMicrosoftAccountAlreadyLinked(hash: ByteArray): Boolean =
        newSuspendedTransaction(db = db) {
            ExposedUser.count(ExposedUsers.msftIdHash eq hash) > 0
        }

    override suspend fun getBansFor(hash: ByteArray): List<LinkBan> =
        newSuspendedTransaction(db = db) {
            ExposedBan.find(ExposedBans.msftIdHash eq hash).toList()
        }

    @OptIn(UsesTrueIdentity::class)
    override suspend fun recordNewUser(
        newDiscordId: String,
        newMsftIdHash: ByteArray,
        newEmail: String,
        keepIdentity: Boolean,
        timestamp: Instant
    ): LinkUser =
        newSuspendedTransaction(db = db) {
            val u = ExposedUser.new {
                discordId = newDiscordId
                msftIdHash = newMsftIdHash
                creationDate = timestamp
            }
            if (keepIdentity) {
                ExposedTrueIdentity.new {
                    user = u
                    email = newEmail
                }
            }
            u
        }

    @UsesTrueIdentity
    override suspend fun isUserIdentifiable(discordId: String): Boolean {
        val user = getUser(discordId) as? ExposedUser ?: throw LinkException("Unknown user $discordId")
        return newSuspendedTransaction(db = db) {
            user.trueIdentity != null
        }
    }

    @UsesTrueIdentity
    override suspend fun getUserEmailWithAccessLog(
        discordId: String,
        automated: Boolean,
        author: String,
        reason: String
    ): String {
        val user = getUser(discordId) as? ExposedUser ?: throw LinkException("Unknown user $discordId")
        return newSuspendedTransaction(db = db) {
            val identity =
                user.trueIdentity?.email ?: throw LinkException("Cannot get true identity of user $discordId")
            // Record the identity access
            ExposedIdentityAccess.new {
                target = user.id
                authorName = author
                this.automated = automated
                this.reason = reason
                timestamp = Instant.now()
            }
            identity
        }
    }

    override suspend fun getIdentityAccessesFor(discordId: String): Collection<LinkIdentityAccess> {
        val user = getUser(discordId) as? ExposedUser ?: throw LinkException("Unknown user $discordId")
        return newSuspendedTransaction {
            ExposedIdentityAccess.find(ExposedIdentityAccesses.target eq user.id).toList()
        }
    }

    @UsesTrueIdentity
    override suspend fun recordNewIdentity(discordId: String, newEmail: String) {
        val u = getUser(discordId) as? ExposedUser ?: throw LinkException("Unknown user $discordId")
        newSuspendedTransaction {
            ExposedTrueIdentity.new {
                user = u
                email = newEmail
            }
        }
    }

    @UsesTrueIdentity
    override suspend fun eraseIdentity(discordId: String) {
        newSuspendedTransaction {
            ExposedUser.find(ExposedUsers.discordId eq discordId)
                .single()
                .trueIdentity!! // We don't care about error cases, callers are responsible for checks
                .delete()
        }
    }
}

/**
 * The TrueIdentities table of the database, used to store a reference to the
 * User's information and their e-mail address.
 *
 * This is used in a separate table to ensure that access to users' email
 * address is always a special case, and never just an accidental access.
 *
 * Note that the [trueIdentity field of the User DAO][ExposedUser.trueIdentity] class
 * is lazily loaded. The data is not fetched in the database unless we actually
 * explicitly ask for it (i.e. doing user.trueIdentity).
 *
 * @see ExposedTrueIdentity
 */
@UsesTrueIdentity
private object ExposedTrueIdentities : IntIdTable("TrueIdentities") {
    /**
     * A reference (by ID) to the user this true identity belongs to
     */
    val user = reference("user", ExposedUsers).uniqueIndex()

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
 * @see [ExposedTrueIdentities]
 */
@UsesTrueIdentity
class ExposedTrueIdentity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ExposedTrueIdentity>(
        ExposedTrueIdentities
    )

    /**
     * The user (lazily loaded by JB Exposed)
     *
     * @see ExposedTrueIdentities.user
     */
    var user by ExposedUser referencedOn ExposedTrueIdentities.user

    /**
     * Their email address
     *
     * @see ExposedTrueIdentities.email
     */
    var email by ExposedTrueIdentities.email
}

/**
 * The table for all of the bans.
 *
 * @see ExposedBan
 */
private object ExposedBans : IntIdTable("Bans") {
    /**
     * The hashed (SHA256) Microsoft ID of the banned user.
     *
     * We do not directly link to the user here to ensure that a user can delete
     * their account (i.e. delete the row of that user in the [ExposedUsers] table)
     * while still maintaining the information that this Microsoft ID is banned.
     */
    var msftIdHash = binary("msftIdHash", 64)

    /**
     * Null if this is a definitive ban, the expiration date if this is a
     * temporary ban.
     */
    var expiresOn = timestamp("expiresOn").nullable()
}

/**
 * The DAO class for [ExposedBans]
 *
 * @see ExposedBans
 */
class ExposedBan(id: EntityID<Int>) : IntEntity(id), LinkBan {
    companion object : IntEntityClass<ExposedBan>(
        ExposedBans
    )

    /**
     * The SHA256 hash of the Microsoft ID of the banned user.
     *
     * @see ExposedBans.msftIdHash
     */
    override var msftIdHash by ExposedBans.msftIdHash

    /**
     * The expiration date of a temporary ban, or null for a definitive ban.
     *
     * @see ExposedBans.expiresOn
     */
    override var expiresOn by ExposedBans.expiresOn
}

/**
 * The Users table of the database, that contains basic identity information
 * on each user.
 *
 * @see ExposedUser
 */
private object ExposedUsers : IntIdTable("Users") {
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
    val creationDate = timestamp("creationDate")
}

/**
 * The User DAO class that is used as a view on a User row. Also contains an
 * optional back reference to the true identity of the user (or null if that is
 * not there).
 *
 * @see ExposedUsers
 */
class ExposedUser(id: EntityID<Int>) : IntEntity(id), LinkUser {
    companion object : IntEntityClass<ExposedUser>(
        ExposedUsers
    )

    /**
     * The Discord ID of the user
     */
    override var discordId by ExposedUsers.discordId

    /**
     * The SHA256 hash of the User's Microsoft ID
     */
    override var msftIdHash by ExposedUsers.msftIdHash

    /**
     * The date on which the account was created
     */
    override var creationDate by ExposedUsers.creationDate

    /**
     * An [optional back reference][optionalBackReferencedOn] to the user's true
     * identity.
     *
     * This is null if the user does not have their true identity stored, or
     * non-null if they have a true identity recorded in the system.
     *
     * The value is lazily-loaded.
     *
     * @see ExposedTrueIdentities
     */
    @UsesTrueIdentity
    val trueIdentity by ExposedTrueIdentity optionalBackReferencedOn ExposedTrueIdentities.user
}

/**
 * Database table for identity accesses
 */
private object ExposedIdentityAccesses : IntIdTable("IdentityAccesses") {
    /**
     * The "target" of the identity access (i.e. the person whose identity was access)
     */
    val target = reference("target", ExposedUsers)

    /**
     * The requester of the identity access (i.e. the person who requested the identity access).
     */
    val authorName = varchar("authorName", 40)

    /**
     * True if this was done by a bot, false if it was done by a human.
     */
    val automated = bool("automated")

    /**
     * The justification given for the identity access.
     */
    val reason = varchar("reason", 2000)

    /**
     * The time at which the identity access was conducted.
     */
    val timestamp = timestamp("timestamp")
}

/**
 * The DAO class for a single IdentityAccess
 */
class ExposedIdentityAccess(id: EntityID<Int>) : IntEntity(id), LinkIdentityAccess {
    companion object : IntEntityClass<ExposedIdentityAccess>(ExposedIdentityAccesses)

    /**
     * The target of the identity access
     */
    var target by ExposedIdentityAccesses.target

    /**
     * The author (requester) of the identity access
     */
    override var authorName by ExposedIdentityAccesses.authorName

    /**
     * Whether the identity access was done by a robot (true) or not (false)
     */
    override var automated by ExposedIdentityAccesses.automated

    /**
     * The reason given by the requester for the identity access
     */
    override var reason by ExposedIdentityAccesses.reason

    /**
     * The time at which the identity access happened.
     */
    override var timestamp by ExposedIdentityAccesses.timestamp
}