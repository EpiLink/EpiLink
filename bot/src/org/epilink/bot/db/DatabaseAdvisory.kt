package org.epilink.bot.db

sealed class DatabaseAdvisory

object Allowed : DatabaseAdvisory()

class Disallowed(val reason: String): DatabaseAdvisory()
