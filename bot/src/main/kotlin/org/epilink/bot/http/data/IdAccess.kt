@file:Suppress("KDocMissingDocumentation")

package org.epilink.bot.http.data

// See the Api.md documentation file for more information
data class IdAccessLogs(
    val manualAuthorsDisclosed: Boolean,
    val accesses: List<IdAccess>
)

data class IdAccess(
    val automated: Boolean,
    val author: String?,
    val reason: String,
    val timestamp: String
)