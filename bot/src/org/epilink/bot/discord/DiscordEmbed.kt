package org.epilink.bot.discord

import discord4j.core.spec.EmbedCreateSpec
import java.awt.Color

data class DiscordEmbed(
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    val color: Int? = null,
    val footer: DiscordEmbedFooter? = null,
    val image: String? = null,
    val thumbnail: String? = null,
    val author: DiscordEmbedAuthor? = null,
    val fields: List<DiscordEmbedField> = listOf()
)

fun EmbedCreateSpec.from(e: DiscordEmbed) {
    if(e.title != null)
        setTitle(e.title)
    if(e.description != null)
        setDescription(e.description)
    if(e.url != null)
        setUrl(e.url)
    if(e.color != null)
        setColor(Color(e.color))
    if(e.footer != null)
        setFooter(e.footer.text, e.footer.iconUrl)
    if(e.image != null)
        setImage(e.image)
    if(e.thumbnail != null)
        setThumbnail(e.thumbnail)
    if(e.author != null)
        setAuthor(e.author.name, e.author.url, e.author.iconUrl)
    e.fields.forEach {
        addField(it.name, it.value, it.inline)
    }
}

data class DiscordEmbedFooter(
    val text: String,
    val iconUrl: String? = null
)

data class DiscordEmbedAuthor(
    val name: String,
    val url: String? = null,
    val iconUrl: String? = null
)

data class DiscordEmbedField(
    val name: String,
    val value: String,
    val inline: Boolean = true
)