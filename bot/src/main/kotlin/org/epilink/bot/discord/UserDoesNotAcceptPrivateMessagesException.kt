/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import discord4j.rest.http.client.ClientException
import org.epilink.bot.LinkException

/**
 * Used to signal that a user did not accept private messages
 */
class UserDoesNotAcceptPrivateMessagesException(ex: ClientException) :
    LinkException("This Discord account does not accept private messages.", ex)
