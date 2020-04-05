package org.epilink.bot.http

/**
 * Something that can be sent by the EpiLink API. Anything served from
 * /api/ is almost always using this class in serialized JSON form.
 *
 * @param T The type of the data, or `Nothing?` if no data is expected.
 */
sealed class ApiResponse<T>(
    /**
     * A message explaining the success or failure. If success is false, this should be non-null.
     */
    val message: String?,
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
 * Sent upon a successful operation, with optionally a message and attached data
 */
class ApiSuccessResponse<T>(
    message: String? = null,
    data: T
) : ApiResponse<T>(message, data) {
    override val success: Boolean
        get() = true
}

/**
 * Sent when something wrong happens. A message must be provided.
 */
class ApiErrorResponse(
    message: String,
    errorInfo: ApiErrorData
): ApiResponse<ApiErrorData>(message, errorInfo) {
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
fun apiSuccess(message: String): ApiResponse<Nothing?> =
    ApiSuccessResponse(message, null)
