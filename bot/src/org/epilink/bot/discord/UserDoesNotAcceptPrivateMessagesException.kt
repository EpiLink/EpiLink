package org.epilink.bot.discord

import discord4j.rest.http.client.ClientException
import org.epilink.bot.LinkDisplayableException

/**
 * Used to signal that a user did not accept private messages
 */
class UserDoesNotAcceptPrivateMessagesException(ex: ClientException) :
    LinkDisplayableException("This Discord account does not accept private messages.", true, cause = ex)
