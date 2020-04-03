package org.epilink.bot

import org.epilink.bot.http.ApiErrorData

interface LinkErrorCode {
    val code: Int
    val description: String
}

fun LinkErrorCode.toErrorData(): ApiErrorData = ApiErrorData(code, description)

enum class StandardErrorCodes(override val code: Int, override val description: String) : LinkErrorCode {
    // Special
    UnknownError(999, "An unknown error occurred"),

    // 1xx: registration process errors
    IncompleteRegistrationRequest(100, "The registration request is missing some elements"),
    AccountCreationNotAllowed(101, "Account creation is not allowed"),
    InvalidAuthCode(102, "Invalid authorization code"),
    AccountHasNoEmailAddress(103, "This account does not have any attached email address"),
    AccountHasNoId(104, "This account does not have any ID"),
    UnknownService(105, "This service is not known or does not exist"),

    // 2xx: External services errors
    DiscordApiFailure(201, "Something went wrong with a Discord API call"),
    MicrosoftApiFailure(202, "Something went wrong with a Microsoft API call"),

    // 3xx: general errors
    MissingAuthentication(300, "You need authentication to be able to access this resource")
}