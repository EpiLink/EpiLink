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
 * Something that can be sent by the EpiLink API. Anything served from
 * /api/ is almost always using this class in serialized JSON form.
 *
 * @param T The type of the data, or `Nothing?` if no data is expected.
 */
@Suppress("ConstructorParameterNaming")
sealed class ApiResponse<T>(
    /**
     * A message (in English) explaining the success or failure. If success is false, this should be non-null.
     */
    val message: String?,
    /**
     * The I18n key for the message
     */
    val message_i18n: String?,
    /**
     * The I18n replacement dictionary, with the replacement key on the left and the replacement value on the right
     */
    val message_i18n_data: Map<String, String> = mapOf(),
    /**
     * Data attached to this response
     */
    val data: T?
) {
    /**
     * True if the operation was successful, false if there was an error.
     *
     * If the operation was successful and data is expected, data will be equal
     * to something other than null.
     *
     * If the operation was unsuccessful, the message should be non-null.
     */
    abstract val success: Boolean
}

/**
 * Sent upon a successful operation, with optionally a message and attached data.
 *
 * Create instances of this by using the [of] functions
 */
@Suppress("ConstructorParameterNaming")
class ApiSuccessResponse<T> private constructor(
    message: String? = null,
    message_i18n: String? = null,
    message_i18n_data: Map<String, String> = mapOf(),
    data: T
) : ApiResponse<T>(message, message_i18n, message_i18n_data, data) {

    companion object {
        /**
         * Create a success response from only the data object. The message fields will be null and the i18n data
         * field will be an empty map.
         */
        fun <T> of(data: T) =
            ApiSuccessResponse(null, null, mapOf(), data)

        /**
         * Create a success response from all of the provided values. See [ApiResponse] for details.
         */
        fun <T> of(message: String, messageI18n: String, messageI18nData: Map<String, String> = mapOf(), data: T) =
            ApiSuccessResponse(message, messageI18n, messageI18nData, data)
    }

    override val success: Boolean
        get() = true
}

/**
 * Sent when something wrong happens. A message must be provided.
 */
@Suppress("ConstructorParameterNaming")
class ApiErrorResponse(
    message: String,
    message_i18n: String,
    message_i18n_data: Map<String, String> = mapOf(),
    errorInfo: ApiErrorData
) : ApiResponse<ApiErrorData>(message, message_i18n, message_i18n_data, errorInfo) {
    override val success: Boolean
        get() = false
}

/**
 * The data object that is sent in error responses, with an error code and a description
 *
 * @property code the error code associated with this error
 * @property description the description of the error code
 */
data class ApiErrorData(val code: Int, val description: String)

/**
 * Utility function for building an [ApiResponse] object with null data.
 */
fun apiSuccess(
    message: String,
    messageI18n: String,
    messageI18nData: Map<String, String> = mapOf()
): ApiResponse<Nothing?> =
    ApiSuccessResponse.of(message, messageI18n, messageI18nData, null)
