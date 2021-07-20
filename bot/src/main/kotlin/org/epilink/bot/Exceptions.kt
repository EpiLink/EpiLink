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
 * Exceptions handled within EpiLink should be using the EpiLinkException type.
 *
 * @see EndpointException
 */
open class EpiLinkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * An exception that happens within EpiLink and that has information on how routes should display that error to the
 * user.
 *
 * What is actually sent in an API response triggered by an exception of this type depends on the exact exception type
 * used.
 *
 * @see UserEndpointException
 * @see InternalEndpointException
 * @property errorCode The error code information associated with this exception
 * @param message The exception message, which must not be sent as part of an API response.
 * @param cause Optional exception that caused this exception to be thrown
 */
sealed class EndpointException(
    val errorCode: ErrorCode,
    message: String,
    cause: Throwable? = null
) : EpiLinkException(message, cause)

/**
 * An exception that is internal (HTTP 500 code). Only the error code's description is given to the user.
 */
class InternalEndpointException(
    errorCode: ErrorCode,
    message: String,
    cause: Throwable? = null
) : EndpointException(errorCode, message, cause)

/**
 * An exception that is caused by the user (HTTP 4xx code). The details are given to the user.
 *
 * @property details Optional details on the exception. Null if no detail is available and the error code is sufficient.
 * If this is null, [detailsI18n] must be null and [detailsI18nData] should be empty.
 * @property detailsI18n An I18n key that corresponds to the details. Null if and only if [details] is null.
 * @property detailsI18nData A map from a replacement key to the actual value it should be replaced with.
 */
class UserEndpointException(
    errorCode: ErrorCode,
    val details: String? = null,
    val detailsI18n: String? = null,
    val detailsI18nData: Map<String, String> = mapOf(),
    cause: Throwable? = null
) : EndpointException(errorCode, errorCode.description + (details?.let { " ($it)" } ?: ""), cause)

/**
 * Turn the information of this exception into a proper ApiErrorResponse. If this exception does not have a message,
 * uses the error code's description instead.
 */
fun EndpointException.toApiResponse(): ApiErrorResponse = when (this) {
    is InternalEndpointException ->
        ApiErrorResponse(errorCode.description, "err.${errorCode.code}", errorInfo = errorCode.toErrorData())
    is UserEndpointException ->
        ApiErrorResponse(
            details ?: errorCode.description,
            detailsI18n ?: "err.${errorCode.code}",
            detailsI18nData,
            errorCode.toErrorData()
        )
}
