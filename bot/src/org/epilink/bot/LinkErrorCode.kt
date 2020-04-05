package org.epilink.bot

import org.epilink.bot.http.ApiErrorData

/**
 * Represents an error code that can be sent by the API, which has an integer code and a string description. The
 * description for a given code should always be the same. If more information needs to be sent, it must be sent as part
 * of the API response's message.
 */
interface LinkErrorCode {
    val code: Int
    val description: String
}

/**
 * Utility function for dumping the information of an error code into an ApiErrorData object
 */
fun LinkErrorCode.toErrorData(): ApiErrorData = ApiErrorData(code, description)

/**
 * Standard error codes of the EpiLink API.
 *
 * @property code The integer code associated with this error code
 * @property description A human-readable description of this error code.
 */
enum class StandardErrorCodes(override val code: Int, override val description: String) : LinkErrorCode {
    // Special

    /**
     * Sent when something wrong happened.
     */
    UnknownError(999, "An unknown error occurred"),

    // ************ 1xx: registration process errors ************

    /**
     * Sent when a registration request is missing elements
     */
    IncompleteRegistrationRequest(100, "The registration request is missing some elements"),

    /**
     * Sent when the creation of an account is not allowed
     */
    AccountCreationNotAllowed(101, "Account creation is not allowed"),

    /**
     * Sent when the authorization code is not valid (/register/authcode endpoints)
     */
    InvalidAuthCode(102, "Invalid authorization code"),

    /**
     * Sent when a Microsoft account does not have any attached email address
     */
    AccountHasNoEmailAddress(103, "This account does not have any attached email address"),

    /**
     * Sent when a Microsoft account does not have any attached ID
     */
    AccountHasNoId(104, "This account does not have any ID"),

    /**
     * Sent when /register/authcode/service is used with an invalid service.
     */
    UnknownService(105, "This service is not known or does not exist"),

    // ************ 2xx: External services errors ************
    /**
     * Sent in case of a back-end call to the Discord API that failed with no decipherable reason
     */
    DiscordApiFailure(201, "Something went wrong with a Discord API call"),

    /**
     * Sent in case of a back-end call to the Microsoft API that failed with no decipherable reason
     */
    MicrosoftApiFailure(202, "Something went wrong with a Microsoft API call"),

    // ************ 3xx: general errors ************
    /**
     * Sent when an API call failed because the user is not logged in at all
     */
    MissingAuthentication(300, "You need authentication to be able to access this resource"),

    /**
     * Sent when an API call failed because the user is logged in but does not have enough permissions to do something
     */
    InsufficientPermission(301, "You do not have permission to do that.");

    /**
     * Returns a string representation of this error code
     */
    override fun toString(): String = "$code $name: $description"
}