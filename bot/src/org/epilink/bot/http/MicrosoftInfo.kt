package org.epilink.bot.http

/**
 * Represents Microsoft user information retrieved from the Microsoft API. This is manually filled in, so it does not
 * *actually* correspond do any real Microsoft Graph endpoint.
 */
data class MicrosoftInfo(val guid: String, val email: String)
