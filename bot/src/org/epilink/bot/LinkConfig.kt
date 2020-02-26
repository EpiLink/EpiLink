package org.epilink.bot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.nio.file.Files
import java.nio.file.Path

data class LinkConfiguration(
    val name: String,
    val serverPort: Int,
    val tokens: LinkTokens,
    val sessionDuration: Long
)

data class LinkTokens(
    val jwtSecret: String?,

    val discordToken: String?,
    val discordOAuthClientId: String?,
    val discordOAuthSecret: String?,

    val msftOAuthClientId: String?,
    val msftOAuthSecret: String?
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