package org.epilink.bot.http

/**
 * Something that can be sent by the EpiLink API. Anything served from
 * /api/ is almost always using this class in serialized JSON form.
 *
 * @param T The type of the data, or `Nothing?` if no data is expected.
 */
data class ApiResponse<T>(
    /**
     * True if the operation was successful, false if there was an error.
     *
     * If the operation was successful and data is expected, data will be equal
     * to something other than null.
     *
     * If the operation was unsuccessful, the message should be non-null.
     */
    val success: Boolean,
    /**
     * An optional message explaining the success or failure.
     */
    val message: String? = null,
    /**
     * Data attached to this response
     */
    val data: T?
)

/**
 * Utility function for build an [ApiResponse] object with null data.
 */
fun ApiResponse(success: Boolean, message: String?): ApiResponse<Nothing?> =
    ApiResponse(success, message, null)