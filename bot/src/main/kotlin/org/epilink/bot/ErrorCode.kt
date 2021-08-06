/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import org.epilink.bot.http.ApiErrorData
import org.epilink.bot.http.ApiErrorResponse

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

/**
 * Standard error codes of the EpiLink API.
 *
 * @property code The integer code associated with this error code
 * @property description A human-readable description of this error code.
 */
enum class StandardErrorCodes(override val code: Int, override val description: String) : ErrorCode {
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
     * Sent when the authorization code is not valid (/register/authcode and /user/identity endpoints)
     */
    InvalidAuthCode(102, "Invalid authorization code"),

    /**
     * Sent when an Identity Provider account does not have any attached email address
     */
    AccountHasNoEmailAddress(103, "This account does not have any attached email address"),

    /**
     * Sent when an Identity Provider account does not have any attached ID
     */
    AccountHasNoId(104, "This account does not have any ID"),

    /**
     * Sent when /register/authcode/service is used with an invalid service.
     */
    UnknownService(105, "This service is not known or does not exist"),

    /**
     * This account already has its identity recorded in the database (for the `/user/identity` endpoint)
     */
    IdentityAlreadyKnown(110, "The identity of this account is already registered in the database"),

    /**
     *  This account's identity cannot be removed, because it is not present in the database (for the `/user/identity` endpoint)
     */
    IdentityAlreadyUnknown(111, "The identity of this account already does not exist in the database"),

    /**
     * This account's identity does not match the one retrieved via the an Identity Provider authcode (different IDs).
     */
    NewIdentityDoesNotMatch(112, "This account's identity does not match the new one"),

    /**
     * This account's identity cannot be removed because it is on cooldown to prevent abuse.
     */
    IdentityRemovalOnCooldown(113, "This account's identity cannot be removed at this time: please wait before retrying."),

    // ************ 2xx: External services errors ************
    /**
     * Sent in case of a back-end call to the Discord API that failed with no decipherable reason
     */
    DiscordApiFailure(201, "Something went wrong with a Discord API call"),

    /**
     * Sent in case of a back-end call to the an Identity Provider API that failed with no decipherable reason
     */
    IdentityProviderApiFailure(202, "Something went wrong with an identity provider API call"),

    // ************ 3xx: general errors ************
    /**
     * Sent when an API call failed because the user is not logged in at all
     */
    MissingAuthentication(300, "You need authentication to be able to access this resource"),

    /**
     * Sent when an API call failed because the user is logged in but does not have enough permissions to do something
     */
    InsufficientPermissions(301, "You do not have permission to do that."),

    // ************ 4xx: Admin API errors ************
    /**
     * Sent when an API call made to the admin endpoints is invalid
     */
    @Suppress("unused")
    InvalidAdminRequest(400, "Invalid administration request."),

    /**
     * Sent when an API call made to the admin endpoints is incomplete.
     *
     * These calls require additional constraints on the values, hence the existence of this error code.
     */
    IncompleteAdminRequest(401, "Incomplete administration request."),

    /**
     * Sent when an admin API call is made and targets a user who does not exist
     */
    TargetUserDoesNotExist(402, "Target user does not exist."),

    /**
     * Sent in case of an invalid or incoherent ID on an admin call
     */
    InvalidId(403, "Invalid or incoherent ID."),

    /**
     * Sent in case of an invalid or incoherent ID on an admin call
     */
    InvalidInstant(404, "Invalid instant (date/hour) format, expecting an ISO-8601 instant format."),

    /**
     * Sent on an ID request when the ID just does not exist.
     */
    TargetIsNotIdentifiable(430, "The targeted user does not have their identity in the database.")

    ;

    /**
     * Returns a string representation of this error code
     */
    override fun toString(): String = "$code $name: $description"
}
