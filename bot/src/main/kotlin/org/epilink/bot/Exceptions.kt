/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import org.epilink.bot.http.ApiErrorResponse

/**
 * Exceptions handled within EpiLink should be using the LinkException type.
 *
 * @see LinkEndpointException
 */
open class LinkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * An exception that happens within EpiLink whose message is end-user friendly.
 *
 * @property errorCode The error code information associated with this exception
 * @param message The exception message, which may also be sent as part of an API response.
 * @property isEndUserAtFault True if the error is caused by the user (400 HTTP status code), false if the error
 * comes from EpiLink itself (500 HTTP status code).
 * @param cause Optional exception that caused this exception to be thrown
 */
open class LinkEndpointException(
    val errorCode: LinkErrorCode,
    message: String? = null,
    val isEndUserAtFault: Boolean = false,
    cause: Throwable? = null
) : LinkException(errorCode.description + (message?.let { " ($it)" } ?: ""), cause)

/**
 * Turn the information of this exception into a proper ApiErrorResponse. If this exception does not have a message,
 * uses the error code's description instead.
 */
fun LinkEndpointException.toApiResponse(): ApiErrorResponse =
    ApiErrorResponse(message ?: errorCode.description, errorCode.toErrorData())