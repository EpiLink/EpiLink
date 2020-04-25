/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.http

/**
 * Represents Microsoft user information retrieved from the Microsoft API. This is manually filled in, so it does not
 * *actually* correspond do any real Microsoft Graph endpoint.
 *
 * @property guid The ID of the user
 * @property email The e-mail address of the user
 */
data class MicrosoftUserInfo(val guid: String, val email: String)
