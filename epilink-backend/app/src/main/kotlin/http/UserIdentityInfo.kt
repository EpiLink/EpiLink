/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.http

/**
 * Represents the identity provider's user information retrieved through the OIDC protocol.
 *
 * @property guid The ID of the user (sub, possibly oid for backwards compatibility purposes)
 * @property email The e-mail address of the user
 */
data class UserIdentityInfo(val guid: String, val email: String)
