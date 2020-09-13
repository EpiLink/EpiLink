/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.http.data

import org.epilink.bot.db.UsesTrueIdentity

// See the Api.md documentation file for more information
@Suppress("KDocMissingDocumentation")
data class UserInformation(
    val discordId: String,
    val username: String,
    val avatarUrl: String?,
    @UsesTrueIdentity
    val identifiable: Boolean,
    val privileged: Boolean
)