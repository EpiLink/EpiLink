package org.epilink.bot.config

/**
 * Configuration for the identity provider
 */
data class LinkIdProviderConfiguration(
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