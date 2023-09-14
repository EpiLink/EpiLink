/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Color
import org.epilink.bot.EpiLinkException

private const val HEXADECIMAL = 16

/**
 * Represents an embed that can be sent in a Discord message. Use [EmbedCreateSpec.from] (an extension function) to
 * fill an embed create spec with the embed information contained in a DiscordEmbed object.
 *
 * The combined sum of the title, description, fields' name, fields' value, footer text and author name may not
 * exceed 6000 characters.
 */
data class DiscordEmbed(
    /**
     * The title of the embed. Max 256 characters.
     */
    val title: String? = null,
    /**
     * The description of the embed. Max 2048 characters.
     */
    val description: String? = null,
    /**
     * The URL of the embed (on title click).
     */
    val url: String? = null,
    /**
     * The color of the embed. Can be either a static field of [discord4j.rest.util.Color] or a hexadecimal value
     * preceded by a `#` (e.g.: `#12be00`)
     */
    val color: String? = null,
    /**
     * The footer of the embed, the small text that appears at the bottom.
     */
    val footer: DiscordEmbedFooter? = null,
    /**
     * The URL to the image for the embed
     */
    val image: String? = null,
    /**
     * The URL to the thumbnail image for the embed.
     */
    val thumbnail: String? = null,
    /**
     * The information about the author
     */
    val author: DiscordEmbedAuthor? = null,
    /**
     * The fields of the embed.
     */
    val fields: List<DiscordEmbedField> = listOf()
) {
    /**
     * The [color] field in the form of a [java.awt.Color] object
     *
     * @throws EpiLinkException if the color is in an unrecognized format
     */
    val d4jColor: Color? by lazy {
        when {
            this.color == null -> null
            // Color is guaranteed to not be null at this point, so we can just !! it
            // (runCatching is missing a contract to let the compiler know)
            this.color.startsWith("#") -> runCatching {
                @Suppress("UnsafeCallOnNullableType")
                Color.of(Integer.parseInt(this.color!!.substring(1), HEXADECIMAL))
            }.getOrElse { throw EpiLinkException("Invalid hexadecimal color format: $color", it) }

            else -> {
                // Try and parse a Color static field
                runCatching {
                    @Suppress("UnsafeCallOnNullableType")
                    Color::class.java.getField(this.color!!.uppercase()).get(null) as? Color
                }.getOrElse { throw EpiLinkException("Unrecognized color: $color", it) }
            }
        }
    }
}

/**
 * Apply the content of a [DiscordEmbed] into the passed [EmbedCreateSpec] object.
 */
fun DiscordEmbed.toDiscord4J(): EmbedCreateSpec =
    EmbedCreateSpec.builder().apply {
        title?.let { title(it) }
        description?.let { description(it) }
        url?.let { url(it) }
        d4jColor?.let { color(it) }
        footer?.let { footer(it.text, footer.iconUrl) }
        image?.let { image(it) }
        thumbnail?.let { thumbnail(it) }
        author?.let { author(it.name, it.url, it.iconUrl) }
        fields.forEach { addField(it.name, it.value, it.inline) }
    }.build()

/**
 * Information on the footer of an embed
 */
data class DiscordEmbedFooter(
    /**
     * The text to display in the footer
     */
    val text: String,
    /**
     * The URL to the image to display in the footer
     */
    val iconUrl: String? = null
)

/**
 * Information on the author of an embed
 */
data class DiscordEmbedAuthor(
    /**
     * The name of the author
     */
    val name: String,
    /**
     * The URL that the name of the author links to
     */
    val url: String? = null,
    /**
     * The URL to the image to display as the author's picture
     */
    val iconUrl: String? = null
)

/**
 * Information on a field in an embed
 */
data class DiscordEmbedField(
    /**
     * The name (title) of this field
     */
    val name: String,
    /**
     * The value (description/content) of this field.
     */
    val value: String,
    /**
     * Whether this field should be an inline field or not. Inline fields are displayed in a "flow", whereas non-inline
     * fields are displayed with only one on each line.
     *
     * Inline:
     * ```
     * field1       field2
     * Hello        World
     *
     * field3
     * Foo bar
     * ```
     *
     * Non-inline
     * ```
     * field1
     * Hello
     *
     * field2
     * World
     *
     * field3
     * Foo bar
     * ```
     */
    val inline: Boolean = true
)
