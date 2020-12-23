package org.epilink.bot

import io.mockk.every
import io.mockk.mockk
import org.epilink.bot.db.LinkUser
import java.time.Instant

// TODO doc
fun mockUser(
    id: String? = null,
    idpIdHash: String,
    creationDate: Instant? = null
) = mockUser(id, idpIdHash.sha256(), creationDate)

// TODO doc also
fun mockUser(
    id: String? = null,
    idpIdHash: ByteArray? = null,
    creationDate: Instant? = null
): LinkUser = mockk {
    if (id != null)
        every { this@mockk.discordId } returns id
    if (idpIdHash != null)
        every { this@mockk.idpIdHash } returns idpIdHash
    if (creationDate != null)
        every { this@mockk.creationDate } returns creationDate
}