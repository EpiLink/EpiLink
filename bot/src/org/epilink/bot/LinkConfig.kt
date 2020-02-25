package org.epilink.bot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.nio.file.Files
import java.nio.file.Path

data class LinkConfiguration(
    val name: String,
    val serverPort: Int,
    val db: String,
    val tokens: LinkTokens
)

data class LinkTokens(
    private val discordToken: String?,
    private val discordOAuthClientId: String?,
    private val discordOAuthSecret: String?,
    private val msftOAuthClientId: String?,
    private val msftOAuthSecret: String?
)

private val yamlKotlinMapper = ObjectMapper(YAMLFactory()).apply {
    registerModule(KotlinModule())
}

fun loadConfigFromFile(path: Path): LinkConfiguration =
    Files.newBufferedReader(path).use {
        yamlKotlinMapper.readValue(it, LinkConfiguration::class.java)
    }

fun loadConfigFromString(config: String): LinkConfiguration =
    yamlKotlinMapper.readValue(config, LinkConfiguration::class.java)