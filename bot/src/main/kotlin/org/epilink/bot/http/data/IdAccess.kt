/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
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

data class IdRequest(
    val target: String,
    val reason: String
)

data class IdRequestResult(
    val target: String,
    val identity: String
)