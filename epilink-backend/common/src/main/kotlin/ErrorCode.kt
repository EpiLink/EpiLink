/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.common

/**
 * Represents an error code that can be sent by the API, which has an integer code and a string description. The
 * description for a given code should always be the same. If more information needs to be sent, it must be sent as part
 * of the API response's message.
 *
 * @property code The integer code
 * @property description The human-readable description of this error code
 */
interface ErrorCode {
    val code: Int
    val description: String
}
