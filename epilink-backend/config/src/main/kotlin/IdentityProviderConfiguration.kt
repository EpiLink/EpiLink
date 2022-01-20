/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.config

/**
 * Configuration for the identity provider
 */
data class IdentityProviderConfiguration(
    /**
     * Url of the issuer/authority (without the .well-known/openid-configuration
     */
    val url: String,
    /**
     * The name of the identity provider
     */
    val name: String,
    /**
     * Asset for the identity provider's icon, possibly null
     */
    val icon: ResourceAssetConfig?,
    /**
     * Enable backwards compat, for msft only.
     */
    val microsoftBackwardsCompatibility: Boolean = false
)
