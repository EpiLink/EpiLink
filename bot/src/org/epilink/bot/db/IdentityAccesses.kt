package org.epilink.bot.db

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.`java-time`.datetime

object IdentityAccesses : IntIdTable() {
    val target = reference("target", Users)
    val authorName = varchar("authorName", 40)
    val automated = bool("automated")
    val reason = varchar("reason", 2000)
    val timestamp = datetime("timestamp")
}

class IdentityAccess(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<IdentityAccess>(IdentityAccesses)

    var target by IdentityAccesses.target
    var authorName by IdentityAccesses.authorName
    var automated by IdentityAccesses.automated
    var reason by IdentityAccesses.reason
    var timestamp by IdentityAccesses.timestamp
}