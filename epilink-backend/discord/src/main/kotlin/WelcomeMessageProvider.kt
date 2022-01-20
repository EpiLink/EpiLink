/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.discord

import org.epilink.backend.config.DiscordEmbed

interface WelcomeMessageProvider {
    /**
     * Get the "welcome please choose a language" embed in the default language of the instance.
     */
    fun getWelcomeChooseLanguageEmbed(): DiscordEmbed
}
