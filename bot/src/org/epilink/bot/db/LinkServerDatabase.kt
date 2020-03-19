package org.epilink.bot.db

import org.apache.commons.codec.binary.Base64
import org.epilink.bot.config.LinkConfiguration
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
import javax.naming.LinkException

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
    @OptIn(UsesTrueIdentity::class) // Creation of TrueIdentities table
    private val db: Database =
        Database.connect("jdbc:sqlite:${cfg.db}", driver = "org.sqlite.JDBC")
            .apply {
                // Required for SQLite
                transactionManager.defaultIsolationLevel =
                    Connection.TRANSACTION_SERIALIZABLE

                // Create the tables if they do not already exist
                transaction(this) {
                    SchemaUtils.create(
                        Users,
                        TrueIdentities
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
     */
    suspend fun countUsers(): Int =
        newSuspendedTransaction {
            User.count()
        }

    suspend fun doesMicrosoftUserExist(microsoftUid: String?): Boolean {
        if (microsoftUid == null)
            return false
        // Hash id
        val hash = microsoftUid.hashSha256()
        return newSuspendedTransaction {
            User.count(Users.msftIdHash eq hash) > 0
        }
    }

    suspend fun getUser(discordId: String): User? =
        newSuspendedTransaction {
            User.find { Users.discordId eq discordId }.firstOrNull()
        }

    @OptIn(UsesTrueIdentity::class)
    suspend fun createUser(
        session: RegisterSession,
        keepIdentity: Boolean
    ): User {
        val safeDiscId =
            session.discordId ?: throw LinkException("Missing Discord ID")
        val safeMsftId =
            session.microsoftUid ?: throw LinkException("Missing Microsoft ID")
        val safeEmail = session.email ?: throw LinkException("Missing email")
        val adv = isAllowedToCreateAccount(safeDiscId, safeMsftId)
        if (adv is Disallowed) {
            throw LinkException(adv.reason)
        }
        return newSuspendedTransaction {
            User.new {
                discordId = safeDiscId
                msftIdHash = safeMsftId.hashSha256()
                creationDate = LocalDateTime.now()
            }.also { u ->
                if (keepIdentity) {
                    TrueIdentity.new {
                        user = u
                        email = safeEmail
                    }
                }
            }
        }
    }

    private fun Ban.isActive(): Boolean {
        val expiry = expiresOn
        return expiry == null || expiry.isBefore(LocalDateTime.now())
    }

    suspend fun isAllowedToCreateAccount(
        discordId: String?,
        microsoftId: String?
    ): DatabaseAdvisory {
        if (microsoftId != null) {
            val hash = microsoftId.hashSha256()
            val b = getBansFor(hash)
            if (b.any { it.isActive() }) {
                return Disallowed("This Microsoft account is banned")
            }
            if (countUserWithHash(hash) > 0)
                return Disallowed("This Microsoft account is already linked to another account")
        }
        if (discordId != null) {
            val u = getUser(discordId)
            if (u != null)
                return Disallowed("This Discord account already exists")
        }
        return Allowed
    }

    private suspend fun countUserWithHash(hash: ByteArray): Int =
        newSuspendedTransaction {
            User.count(Users.msftIdHash eq hash)
        }

    private suspend fun getBansFor(hash: ByteArray): List<Ban> =
        newSuspendedTransaction {
            Ban.find { Bans.msftIdHash eq hash }.toList()
        }
}

private fun String.hashSha256() =
    MessageDigest.getInstance("SHA-256").digest(
        this.toByteArray(StandardCharsets.UTF_8)
    )