package org.epilink.bot.web

// TODO document
@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.getListOfMaps(key: String): List<Map<String, Any?>> =
    this.getValue(key) as List<Map<String, Any?>>
