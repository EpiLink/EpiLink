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
