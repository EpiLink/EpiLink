package org.epilink.bot.db

/**
 * The database's opinion on an action. The exact semantics of what this means depend on the context.
 */
sealed class DatabaseAdvisory

/**
 * The action that is checked would be semantically allowed.
 */
object Allowed : DatabaseAdvisory()

/**
 * The action that is checked would be semantically disallowed for the given reason. The reason must be a end-user
 * friendly string.
 */
class Disallowed(val reason: String): DatabaseAdvisory()
