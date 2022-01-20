/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.discord

import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Color
import org.epilink.backend.common.EpiLinkException
import org.epilink.backend.config.DiscordEmbed

private const val HEXADECIMAL = 16

/**
 * The [color] field in the form of a [java.awt.Color] object
 *
 * @throws EpiLinkException if the color is in an unrecognized format
 */
val DiscordEmbed.d4jColor: Color?
    get() {
        val color = this.color
        return when {
            color == null -> null
            // Color is guaranteed to not be null at this point, so we can just !! it
            color.startsWith("#") -> runCatching {
                @Suppress("UnsafeCallOnNullableType")
                Color.of(Integer.parseInt(color.substring(1), HEXADECIMAL))
            }.getOrElse { throw EpiLinkException("Invalid hexadecimal color format: $color", it) }
            else -> {
                // Try and parse a Color static field
                runCatching {
                    @Suppress("UnsafeCallOnNullableType")
                    Color::class.java.getField(color.uppercase()).get(null) as? Color
                }.getOrElse { throw EpiLinkException("Unrecognized color: $color", it) }
            }
        }
    }

/**
 * Apply the content of a [DiscordEmbed] into the passed [EmbedCreateSpec] object.
 */
fun DiscordEmbed.toDiscord4J() =
    EmbedCreateSpec.builder().apply {
        title?.let { title(it) }
        description?.let { description(it) }
        url?.let { url(it) }
        d4jColor?.let { color(it) }
        footer?.let { footer(it.text, it.iconUrl) }
        image?.let { image(it) }
        thumbnail?.let { thumbnail(it) }
        author?.let { author(it.name, it.url, it.iconUrl) }
        fields.forEach { addField(it.name, it.value, it.inline) }
    }.build()
