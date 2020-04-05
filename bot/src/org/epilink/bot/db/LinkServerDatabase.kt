package org.epilink.bot.db

import org.epilink.bot.LinkEndpointException
import org.epilink.bot.StandardErrorCodes
import org.epilink.bot.discord.LinkDiscordBot
import org.epilink.bot.http.sessions.RegisterSession
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Connection
import java.time.LocalDateTime

/**
 * The class that manages the database and handles all business logic.
 *
 * Most of the functions declared here are suspending functions intended to be used
 * from within Ktor responses.
 */
class LinkServerDatabase(db: String) {
    /**
     * The Database instance managed by JetBrains Exposed used for transactions.
     */
    @OptIn(UsesTrueIdentity::class) // Creation of TrueIdentities table
    private val db: Database = Database.connect("jdbc:sqlite:$db", driver = "org.sqlite.JDBC")
        .apply {
            // Required for SQLite
            transactionManager.defaultIsolationLevel =
                Connection.TRANSACTION_SERIALIZABLE

            // Create the tables if they do not already exist
            transaction(this) {
                SchemaUtils.create(Users, TrueIdentities, Bans, IdentityAccesses)
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

    /**
     * Returns the server user that has the given Discord account ID associated, or null if no such user exists.
     */
    suspend fun getUser(discordId: String): User? =
        newSuspendedTransaction(db = db) {
            User.find { Users.discordId eq discordId }.firstOrNull()
        }

    /**
     * Create a user in the database using the given registration session's information.
     *
     * @param session The session to get the information from
     * @param keepIdentity If true, a [TrueIdentity] object will be associated to the user and recorded in the
     * database
     * @throws LinkEndpointException If the session's data is invalid, the user is banned, or another erroneous scenario
     */
    @OptIn(UsesTrueIdentity::class) // Creates a user's true identity: access is expected here.
    suspend fun createUser(session: RegisterSession, keepIdentity: Boolean): User {
        // true in the exception because the end-user is at fault for trying to register with bad information.
        val safeDiscId = session.discordId ?: throw LinkEndpointException(
            StandardErrorCodes.IncompleteRegistrationRequest,
            "Missing Discord ID",
            true
        )
        val safeMsftId = session.microsoftUid ?: throw LinkEndpointException(
            StandardErrorCodes.IncompleteRegistrationRequest,
            "Missing Microsoft ID",
            true
        )
        val safeEmail = session.email ?: throw LinkEndpointException(
            StandardErrorCodes.IncompleteRegistrationRequest,
            "Missing email",
            true
        )
        return when (val adv = isAllowedToCreateAccount(safeDiscId, safeMsftId)) {
            is Disallowed -> throw LinkEndpointException(
                StandardErrorCodes.AccountCreationNotAllowed,
                adv.reason,
                true
            )
            is Allowed -> newSuspendedTransaction(db = db) {
                val u = User.new {
                    discordId = safeDiscId
                    msftIdHash = safeMsftId.hashSha256()
                    creationDate = LocalDateTime.now()
                }
                if (keepIdentity) {
                    TrueIdentity.new {
                        user = u
                        email = safeEmail
                    }
                }
                return@newSuspendedTransaction u
            }
        }
    }

    /**
     * Checks whether a ban is currently active or not
     */
    private fun Ban.isActive(): Boolean {
        val expiry = expiresOn
        return /* Ban does not expire */ expiry == null || /* Ban has not expired */ expiry.isBefore(LocalDateTime.now())
    }

    /**
     * Checks whether an account with the given Discord user ID and Microsoft user ID could be created.
     *
     * If a parameter is null, the checks for this parameter are ignored.
     */
    suspend fun isAllowedToCreateAccount(discordId: String?, microsoftId: String?): DatabaseAdvisory {
        // Check Microsoft account if provided
        if (microsoftId != null) {
            val hash = microsoftId.hashSha256()
            val b = getBansFor(hash)
            if (b.any { it.isActive() }) {
                return Disallowed("This Microsoft account is banned")
            }
            if (countUserWithHash(hash) > 0)
                return Disallowed("This Microsoft account is already linked to another account")
        }
        // Check Discord account if provided
        if (discordId != null) {
            val u = getUser(discordId)
            if (u != null)
                return Disallowed("This Discord account already exists")
        }
        // We have not returned yet: no problems found
        return Allowed
    }

    /**
     * Counts how many users have the given hash as their Microsoft ID hash in the database
     */
    private suspend fun countUserWithHash(hash: ByteArray): Int =
        newSuspendedTransaction(db = db) { User.count(Users.msftIdHash eq hash) }

    /**
     * Returns a list of all the bans in the database for the given Microsoft ID hash as a list.
     */
    private suspend fun getBansFor(hash: ByteArray): List<Ban> =
        newSuspendedTransaction(db = db) { Ban.find { Bans.msftIdHash eq hash }.toList() }

    /**
     * Checks whether a user should be able to join a server (i.e. not banned, no irregularities)
     *
     * @return a database advisory with a end-user friendly reason.
     */
    suspend fun canUserJoinServers(dbUser: User): DatabaseAdvisory {
        if (getBansFor(dbUser.msftIdHash).any { it.isActive() }) {
            return Disallowed("You are banned from joining any server at the moment.")
        }
        return Allowed
    }

    /**
     * Checks whether the user has his true identity recorded within the system.
     */
    @UsesTrueIdentity
    suspend fun isUserIdentifiable(dbUser: User): Boolean {
        return newSuspendedTransaction(db = db) { dbUser.trueIdentity != null }
    }

    /**
     * Retrieve the identity of a user. This access is logged within the system and the user is notified.
     */
    @UsesTrueIdentity
    suspend fun accessIdentity(
        dbUser: User,
        automated: Boolean,
        author: String,
        reason: String,
        discord: LinkDiscordBot
    ): String {
        val identity = newSuspendedTransaction {
            val identity = dbUser.trueIdentity?.email ?: error("Cannot get true identity of user")
            // Record the identity access
            IdentityAccess.new {
                target = dbUser.id
                authorName = author
                this.automated = automated
                this.reason = reason
                timestamp = LocalDateTime.now()
            }
            identity
        }
        discord.sendIdentityAccessNotification(dbUser.discordId, automated, author, reason)
        return identity
    }
}

/**
 * Utility function for hashing a String using the SHA-256 algorithm. The String is first converted to a byte array
 * using the UTF-8 charset.
 */
private fun String.hashSha256(): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(this.toByteArray(StandardCharsets.UTF_8))