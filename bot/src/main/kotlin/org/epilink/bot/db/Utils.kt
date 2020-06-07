package org.epilink.bot.db

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Utility function for hashing a String using the SHA-256 algorithm. The String is first converted to a byte array
 * using the UTF-8 charset.
 */
fun String.hashSha256(): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(this.toByteArray(StandardCharsets.UTF_8))