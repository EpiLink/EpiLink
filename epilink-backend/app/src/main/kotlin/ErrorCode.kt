/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend

import org.epilink.backend.common.ErrorCode
import org.epilink.backend.http.ApiErrorData
import org.epilink.backend.http.ApiErrorResponse

/**
 * Utility function for dumping the information of an error code into an ApiErrorData object
 */
fun ErrorCode.toErrorData(): ApiErrorData = ApiErrorData(code, description)

/**
 * Utility function for turning an error code into a proper API Error response with the given description, filling
 * the error information with the error code.
 */
fun ErrorCode.toResponse() = ApiErrorResponse(description, "err.$code", mapOf(), toErrorData())

/**
 * Create a response from this error code with the given description data.
 */
fun ErrorCode.toResponse(
    description: String,
    descriptionI18n: String,
    descriptionI18nData: Map<String, String> = mapOf()
) = ApiErrorResponse(description, descriptionI18n, descriptionI18nData, toErrorData())
