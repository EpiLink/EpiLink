package org.epilink.bot.config

/**
 * Configuration entry for an asset. An asset is either a URL or a local file with its path and optionally its content
 * type
 */
data class ResourceAssetConfig(
    /**
     * The URL to the asset
     */
    val url: String? = null,
    /**
     * The file for this asset
     */
    val file: String? = null,
    /**
     * The content type for the file
     */
    val contentType: String? = null
)