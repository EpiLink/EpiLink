/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.epilink.bot.config.IdentityProviderConfiguration
import org.epilink.bot.config.WebServerConfiguration
import org.epilink.bot.config.ResourceAssetConfig
import java.nio.file.Files
import java.nio.file.Path

/**
 * The assets used by EpiLink. All of these are used to transmit stuff to the front-end
 */
class Assets(
    /**
     * The logo asset
     */
    val logo: ResourceAsset,
    /**
     * The background asset
     */
    val background: ResourceAsset,
    /**
     * The Identity provider's logo asset
     */
    val idpLogo: ResourceAsset)

/**
 * An asset.
 */
sealed class ResourceAsset {
    /**
     * No asset provided, a default value should be used instead
     */
    object None : ResourceAsset()

    /**
     * Asset as an URL to the actual asset
     *
     * @property url The URL
     */
    class Url(val url: String) : ResourceAsset()

    /**
     * Asset as a byte array and an optional content type
     *
     * @property contents The asset as a byte array
     * @property contentType The Content-Type value for this asset, or null if unknown
     */
    class File(val contents: ByteArray, val contentType: ContentType?) : ResourceAsset()
}

/**
 * Turn an asset into a URL, or null if there is no asset.
 */
fun ResourceAsset.asUrl(name: String): String? = when (this) {
    ResourceAsset.None -> null
    is ResourceAsset.Url -> url
    is ResourceAsset.File -> "/api/v1/meta/$name"
}

/**
 * Loads all of the assets from the configuration. Paths are derived from the given root path argument
 */
suspend fun loadAssets(wsCfg: WebServerConfiguration, idpCfg: IdentityProviderConfiguration, root: Path): Assets =
    coroutineScope {
        val logo = async { loadAsset(wsCfg.logo ?: ResourceAssetConfig(), "logo", root) }
        val background = async { loadAsset(wsCfg.background ?: ResourceAssetConfig(), "background", root) }
        val idpLogo = async { loadAsset(idpCfg.icon ?: ResourceAssetConfig(), "idpLogo", root) }
        Assets(logo.await(), background.await(), idpLogo.await())
    }

/**
 * Load a single asset
 */
suspend fun loadAsset(asset: ResourceAssetConfig, name: String, root: Path): ResourceAsset {
    if (asset.file != null) {
        if (asset.url != null) error("Cannot define both a file and a url for the $name asset")
        return ResourceAsset.File(
            withContext(Dispatchers.IO) {
                @Suppress("BlockingMethodInNonBlockingContext")
                // Blocking here is fine
                Files.readAllBytes(root.resolve(asset.file))
            },
            asset.contentType?.let { ContentType.parse(it) })
    }
    if (asset.url != null)
        return ResourceAsset.Url(asset.url)
    return ResourceAsset.None
}
