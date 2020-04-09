package org.epilink.bot.db.exposed

import org.epilink.bot.db.LinkDatabaseFacade
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transactionManager
import java.sql.Connection

/**
 * Implementation of [LinkDatabaseFacade] that uses Exposed with a SQLite database
 */
class SQLiteExposedFacadeImpl(db: String) : ExposedDatabaseFacade() {
    override val db = Database.connect(
        "jdbc:sqlite:$db",
        driver = "org.sqlite.JDBC"
    ).apply {
        // Required for SQLite
        transactionManager.defaultIsolationLevel =
            Connection.TRANSACTION_SERIALIZABLE
    }
}