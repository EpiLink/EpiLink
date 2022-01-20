/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.http.data

import org.epilink.backend.config.ContactInformation
import org.epilink.backend.config.FooterUrl

// See the Api.md documentation file for more information
@Suppress("KDocMissingDocumentation", "ConstructorParameterNaming")
data class InstanceInformation(
    val title: String,
    val logo: String?,
    val background: String?,
    val authorizeStub_idProvider: String,
    val authorizeStub_discord: String,
    val providerName: String,
    val providerIcon: String?,
    val idPrompt: String,
    val footerUrls: List<FooterUrl>,
    val contacts: List<ContactInformation>,
    val showFullAbout: Boolean
)
