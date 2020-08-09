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
import org.epilink.bot.config.LinkIdProviderConfiguration
import org.epilink.bot.config.LinkWebServerConfiguration
import org.epilink.bot.config.ResourceAssetConfig
import java.nio.file.Files
import java.nio.file.Path

class LinkAssets(val logo: ResourceAsset, val background: ResourceAsset, val idpLogo: ResourceAsset)

sealed class ResourceAsset {
    object None : ResourceAsset()
    class Url(val url: String) : ResourceAsset()
    class File(val contents: ByteArray, val contentType: ContentType?) : ResourceAsset()
}

fun ResourceAsset.asUrl(name: String): String? = when (this) {
    ResourceAsset.None -> null
    is ResourceAsset.Url -> url
    is ResourceAsset.File -> "/api/v1/meta/$name"
}

suspend fun loadAssets(wsCfg: LinkWebServerConfiguration, idpCfg: LinkIdProviderConfiguration, root: Path): LinkAssets =
    coroutineScope {
        val logo = async { loadAsset(wsCfg.logo ?: ResourceAssetConfig(), "logo", root) }
        val background = async { loadAsset(wsCfg.background ?: ResourceAssetConfig(), "background", root) }
        val idpLogo = async { loadAsset(idpCfg.icon ?: ResourceAssetConfig(), "idpLogo", root) }
        LinkAssets(logo.await(), background.await(), idpLogo.await())
    }

suspend fun loadAsset(asset: ResourceAssetConfig, name: String, root: Path): ResourceAsset {
    if (asset.file != null) {
        if (asset.url != null) error("Cannot define both a file and a url for the $name asset")
        return ResourceAsset.File(
            withContext(Dispatchers.IO) { Files.readAllBytes(root.resolve(asset.file)) },
            asset.contentType?.let { ContentType.parse(it) })
    }
    if (asset.url != null)
        return ResourceAsset.Url(asset.url)
    return ResourceAsset.None
}