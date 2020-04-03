package org.epilink.bot

import org.epilink.bot.http.ApiErrorData
import org.epilink.bot.http.ApiErrorResponse

/**
 * Exceptions handled within EpiLink should be using the LinkException type.
 *
 * @see LinkEndpointException
 */
open class LinkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * An exception that happens within EpiLink whose message is end-user friendly.
 */
open class LinkEndpointException(
    val errorCode: LinkErrorCode,
    message: String? = null,
    val isEndUserAtFault: Boolean = false,
    cause: Throwable? = null
) : LinkException(errorCode.description + (message?.let { " ($it)" } ?: ""), cause) {

    fun toErrorData(): ApiErrorData = errorCode.toErrorData()

    fun toApiResponse(): ApiErrorResponse = ApiErrorResponse(message ?: errorCode.description, toErrorData())
}