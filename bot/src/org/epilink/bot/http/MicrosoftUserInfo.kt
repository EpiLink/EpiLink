package org.epilink.bot.http

/**
 * Represents Microsoft user information retrieved from the Microsoft API. This is manually filled in, so it does not
 * *actually* correspond do any real Microsoft Graph endpoint.
 *
 * @property guid The ID of the user
 * @property email The e-mail address of the user
 */
data class MicrosoftUserInfo(val guid: String, val email: String)
