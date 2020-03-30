package org.epilink.bot.db

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.`java-time`.datetime

/**
 * Database table for identity accesses
 */
object IdentityAccesses : IntIdTable() {
    /**
     * The "target" of the identity access (i.e. the person whose identity was access)
     */
    val target = reference("target", Users)

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
    val timestamp = datetime("timestamp")
}

/**
 * The DAO class for a single IdentityAccess
 */
class IdentityAccess(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<IdentityAccess>(IdentityAccesses)

    /**
     * The target of the identity access
     */
    var target by IdentityAccesses.target

    /**
     * The author (requester) of the identity access
     */
    var authorName by IdentityAccesses.authorName

    /**
     * Whether the identity access was done by a robot (true) or not (false)
     */
    var automated by IdentityAccesses.automated

    /**
     * The reason given by the requester for the identity access
     */
    var reason by IdentityAccesses.reason

    /**
     * The time at which the identity access happened.
     */
    var timestamp by IdentityAccesses.timestamp
}