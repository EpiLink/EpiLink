package org.epilink.bot.discord

import discord4j.rest.http.client.ClientException
import org.epilink.bot.LinkException

/**
 * Used to signal that a user did not accept private messages
 */
class UserDoesNotAcceptPrivateMessagesException(ex: ClientException) : LinkException(cause = ex)
