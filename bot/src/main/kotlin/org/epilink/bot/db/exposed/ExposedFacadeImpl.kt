/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.db.exposed

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.codec.binary.Hex
import org.apache.commons.dbcp2.DataSourceConnectionFactory
import org.apache.commons.dbcp2.PoolableConnection
import org.apache.commons.dbcp2.PoolableConnectionFactory
import org.apache.commons.dbcp2.PoolingDataSource
import org.apache.commons.pool2.impl.GenericObjectPool
import org.epilink.bot.EpiLinkException
import org.epilink.bot.db.*
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.`java-time`.timestamp
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import javax.sql.DataSource

// This file is rather long because all of the DAO classes are private in order to make sure that only the facade
// has access to them

/**
 * Implementation of an EpiLink database facade using the JetBrains Exposed library
 */
abstract class ExposedDatabaseFacade : DatabaseFacade {
    /**
     * The Database instance managed by JetBrains Exposed used for transactions.
     */
    internal abstract val db: Database

    internal abstract val useMutex: Boolean
    private val mutex = Mutex()

    @OptIn(UsesTrueIdentity::class) // Creation of TrueIdentities table
    override suspend fun start() {
        t {
            SchemaUtils.create(
                ExposedUsers,
                ExposedTrueIdentities,
                ExposedBans,
                ExposedIdentityAccesses,
                ExposedDiscordLanguages
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

    override suspend fun getUser(discordId: String): User? = t {
        ExposedUser.find(ExposedUsers.discordId eq discordId).firstOrNull()
    }

    override suspend fun getUserFromIdpIdHash(idpIdHash: ByteArray): User? = t {
        ExposedUser.find(ExposedUsers.idpIdHash eq idpIdHash).firstOrNull()
    }

    override suspend fun doesUserExist(discordId: String): Boolean = t {
        ExposedUser.count(ExposedUsers.discordId eq discordId) > 0
    }

    override suspend fun isIdentityAccountAlreadyLinked(hash: ByteArray): Boolean = t {
        ExposedUser.count(ExposedUsers.idpIdHash eq hash) > 0
    }

    override suspend fun recordBan(
        target: ByteArray,
        until: Instant?,
        author: String,
        reason: String
    ): Ban = t {
        ExposedBan.new {
            idpIdHash = target
            expiresOn = until
            issued = Instant.now()
            this.author = author
            this.reason = reason
            revoked = false
        }
    }

    override suspend fun getBansFor(hash: ByteArray): List<Ban> = t {
        ExposedBan.find(ExposedBans.idpIdHash eq hash).toList()
    }

    override suspend fun getBan(banId: Int): Ban? = t {
        ExposedBan.findById(banId)
    }

    @OptIn(UsesTrueIdentity::class)
    override suspend fun recordNewUser(
        newDiscordId: String,
        newIdpIdHash: ByteArray,
        newEmail: String,
        keepIdentity: Boolean,
        timestamp: Instant
    ): User = t {
        val u = ExposedUser.new {
            discordId = newDiscordId
            idpIdHash = newIdpIdHash
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

    override suspend fun revokeBan(banId: Int) = t {
        val ban = ExposedBan[banId]
        ban.revoked = true
    }

    @UsesTrueIdentity
    override suspend fun isUserIdentifiable(user: User): Boolean {
        val exUser = user.asExposed()
        return t {
            exUser.trueIdentity != null
        }
    }

    @UsesTrueIdentity
    override suspend fun getUserEmailWithAccessLog(
        user: User,
        automated: Boolean,
        author: String,
        reason: String
    ): String {
        val exUser = user.asExposed()
        return t {
            val identity =
                exUser.trueIdentity?.email
                    ?: throw EpiLinkException("Cannot get true identity of user ${exUser.discordId}")
            // Record the identity access
            ExposedIdentityAccess.new {
                target = exUser.id
                authorName = author
                this.automated = automated
                this.reason = reason
                timestamp = Instant.now()
            }
            identity
        }
    }

    override suspend fun getIdentityAccessesFor(user: User): Collection<IdentityAccess> {
        val exUser = user.asExposed()
        return t {
            ExposedIdentityAccess.find(ExposedIdentityAccesses.target eq exUser.id).toList()
        }
    }

    @UsesTrueIdentity
    override suspend fun recordNewIdentity(user: User, newEmail: String) {
        val exUser = user.asExposed()
        t {
            ExposedTrueIdentity.new {
                this.user = exUser
                email = newEmail
            }
        }
    }

    @UsesTrueIdentity
    override suspend fun eraseIdentity(user: User) {
        val exUser = user.asExposed()
        t {
            exUser.trueIdentity?.delete()
        }
    }

    override suspend fun getLanguagePreference(discordId: String): String? = t {
        ExposedDiscordLanguagePreference.find(ExposedDiscordLanguages.discordId eq discordId).firstOrNull()?.language
    }

    override suspend fun recordLanguagePreference(discordId: String, preference: String): Unit = t {
        val x = ExposedDiscordLanguagePreference.find(ExposedDiscordLanguages.discordId eq discordId).firstOrNull()
        if (x != null) {
            x.language = preference
        } else {
            ExposedDiscordLanguagePreference.new {
                this.discordId = discordId
                language = preference
            }
        }
    }

    override suspend fun clearLanguagePreference(discordId: String): Unit = t {
        val x = ExposedDiscordLanguagePreference.find(ExposedDiscordLanguages.discordId eq discordId).firstOrNull()
        x?.delete()
    }

    override suspend fun updateIdpId(user: User, newIdHash: ByteArray) {
        val u = user.asExposed()
        t {
            u.idpIdHash = newIdHash
        }
    }

    @OptIn(UsesTrueIdentity::class)
    override suspend fun deleteUser(user: User) {
        val u = user.asExposed()
        t {
            // True identity
            u.trueIdentity?.delete()
            // Language
            ExposedDiscordLanguages.deleteWhere { ExposedDiscordLanguages.discordId eq u.discordId }
            // Delete bans
            ExposedBans.deleteWhere { ExposedBans.idpIdHash eq u.idpIdHash }
            u.delete()
        }
    }

    override suspend fun searchUserByPartialHash(partialHashHex: String): List<User> = t {
        ExposedUser.all().filter { Hex.encodeHexString(it.idpIdHash).contains(partialHashHex) }
    }.toList()

    /**
     * Convenience method for transactions, automatically applying locks and using the correct database
     */
    private suspend fun <R> t(transaction: suspend Transaction.() -> R): R {
        return if (useMutex) {
            mutex.withLock { newSuspendedTransaction(db = db, statement = transaction) }
        } else {
            newSuspendedTransaction(db = db, statement = transaction)
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
     * The hashed (SHA256) Identity Provider ID of the banned user.
     *
     * We do not directly link to the user here to ensure that a user can delete
     * their account (i.e. delete the row of that user in the [ExposedUsers] table)
     * while still maintaining the information that this Identity Provider ID is banned.
     */
    val idpIdHash = binary("msftIdHash", 64)

    /**
     * Null if this is a definitive ban, the expiration date if this is a
     * temporary ban.
     */
    val expiresOn = timestamp("expiresOn").nullable()

    /**
     * The time at which the ban was issued
     */
    val issued = timestamp("issued")

    /*
     * True if the ban is revoked and should be ignored, false otherwise
     */
    val revoked = bool("revoked")

    val author = varchar("author", 40)

    val reason = varchar("reason", 2000)
}

/**
 * The DAO class for [ExposedBans]
 *
 * @see ExposedBans
 */
class ExposedBan(id: EntityID<Int>) : IntEntity(id), Ban {
    companion object : IntEntityClass<ExposedBan>(
        ExposedBans
    )

    override val banId
        get() = id.value

    /**
     * The SHA256 hash of the Identity Provider ID of the banned user.
     *
     * @see ExposedBans.idpIdHash
     */
    override var idpIdHash by ExposedBans.idpIdHash

    /**
     * The expiration date of a temporary ban, or null for a definitive ban.
     *
     * @see ExposedBans.expiresOn
     */
    override var expiresOn by ExposedBans.expiresOn

    /**
     * The time at which the ban was created
     */
    override var issued by ExposedBans.issued

    override var revoked by ExposedBans.revoked

    override var author by ExposedBans.author

    override var reason by ExposedBans.reason
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
     * The SHA256 hash of the User's Identity Provider ID
     */
    val idpIdHash = binary("msftIdHash", 64).uniqueIndex()

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
class ExposedUser(id: EntityID<Int>) : IntEntity(id), User {
    companion object : IntEntityClass<ExposedUser>(
        ExposedUsers
    )

    /**
     * The Discord ID of the user
     */
    override var discordId by ExposedUsers.discordId

    /**
     * The SHA256 hash of the User's Identity Provider ID
     */
    override var idpIdHash by ExposedUsers.idpIdHash

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

private fun User.asExposed(): ExposedUser =
    this as? ExposedUser ?: error("Unexpected User is not from the exposed facade")

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
class ExposedIdentityAccess(id: EntityID<Int>) : IntEntity(id), IdentityAccess {
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

private object ExposedDiscordLanguages : IntIdTable("DiscordLanguages") {
    val discordId = varchar("discordId", 32).uniqueIndex()

    val language = varchar("language", 16)
}

/**
 * The DAO class for language preferences
 */
class ExposedDiscordLanguagePreference(id: EntityID<Int>) : IntEntity(id), DiscordLanguagePreference {
    companion object : IntEntityClass<ExposedDiscordLanguagePreference>(ExposedDiscordLanguages)

    override var discordId by ExposedDiscordLanguages.discordId
    override var language by ExposedDiscordLanguages.language
}

internal fun pooled(dataSource: DataSource): PoolingDataSource<PoolableConnection> {
    val factory = PoolableConnectionFactory(DataSourceConnectionFactory(dataSource), null)
    val pool = GenericObjectPool(factory)
    factory.pool = pool
    return PoolingDataSource(pool)
}
