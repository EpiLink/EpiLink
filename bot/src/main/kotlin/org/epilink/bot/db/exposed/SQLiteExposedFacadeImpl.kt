/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.db.exposed

import org.epilink.bot.db.LinkDatabaseFacade
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.sqlite.SQLiteConfig
import java.sql.Connection

// Can be increased in extreme load scenarios
private const val BUSY_TIMEOUT_SQLITE = 60000

/**
 * Implementation of [LinkDatabaseFacade] that uses Exposed with a SQLite database
 */
class SQLiteExposedFacadeImpl(db: String) : ExposedDatabaseFacade() {
    override val db = Database.connect(
        // journal_mode=WAL   Prevents some locking errors. See https://www.sqlite.org/wal.html
        "jdbc:sqlite:$db",
        driver = "org.sqlite.JDBC",
        setupConnection = {
            SQLiteConfig().apply {
                // This configuration makes SQLite faster and/or less buggy in concurrent access scenarios
                setSharedCache(true)
                setJournalMode(SQLiteConfig.JournalMode.WAL)
                busyTimeout = BUSY_TIMEOUT_SQLITE
                apply(it)
            }
        }
    ).apply {
        // Required for SQLite
        transactionManager.defaultIsolationLevel =
            Connection.TRANSACTION_SERIALIZABLE
    }
}