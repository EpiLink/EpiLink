/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.epilink.bot.config.LinkIdProviderConfiguration
import org.epilink.bot.config.LinkPrivacy
import org.epilink.bot.db.*
import org.koin.dsl.module
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class GdprReportTest : KoinBaseTest<LinkGdprReport>(
    LinkGdprReport::class,
    module {
        single<LinkGdprReport> { LinkGdprReportImpl() }
    }
) {
    @Test
    fun `Test user info report`() {
        val i = Instant.now()
        val id = byteArrayOf(1, 2, 3, 4)
        val u = mockk<LinkUser> {
            every { discordId } returns "1234"
            every { creationDate } returns i
            every { idpIdHash } returns id
        }
        mockHere<LinkIdProviderConfiguration> {
            every { name } returns "testy"
        }
        test {
            val s = getUserInfoReport(u)
            assertEquals(
                """
                ## User information

                General information stored about your account.

                - Discord ID: 1234
                - Identity Provider (testy) ID hash (Base64 URL-safe encoded): ${id.encodeBase64Url()}
                - Account creation timestamp: $i
                """.trimIndent(), s
            )
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test true identity report (not identifiable)`() {
        val u = mockk<LinkUser>()
        mockHere<LinkDatabaseFacade> { coEvery { isUserIdentifiable(u) } returns false }
        test {
            val s = getTrueIdentityReport(u, "big boi")
            assertEquals(
                """
                ## Identity information
            
                You did not enable the "Remember my identity" feature.
            
                - True identity: Not stored.
                """.trimIndent(), s
            )
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test true identity report (identifiable)`() {
        val u = mockk<LinkUser>()
        mockHere<LinkDatabaseFacade> { coEvery { isUserIdentifiable(u) } returns true }
        mockHere<LinkIdManager> {
            coEvery {
                accessIdentity(
                    u,
                    false,
                    "big boi",
                    "(via EpiLink GDPR Report Service) Your identity was retrieved in order to generate a GDPR report."
                )
            } returns "le identity"
        }
        test {
            val s = getTrueIdentityReport(u, "big boi")
            assertEquals(
                """
                ## Identity information
            
                Information stored because you enabled the "Remember my identity" feature.
            
                - True identity: le identity
                """.trimIndent(), s
            )
        }
    }

    @Test
    fun `Test ban report (no bans)`() {
        val idHash = byteArrayOf(1, 2, 3, 4)
        val u = mockk<LinkUser> {
            every { idpIdHash } returns idHash
        }
        mockHere<LinkDatabaseFacade> {
            coEvery { getBansFor(idHash) } returns listOf()
        }
        test {
            val s = getBanReport(u)
            assertEquals(
                """
                ## Ban information
                
                The following list contains all of the bans against you.
                
                No known bans.
                """.trimIndent(), s
            )
        }
    }

    @Test
    fun `Test ban report (has bans)`() {
        val idHash = byteArrayOf(1, 2, 3, 4)
        val u = mockk<LinkUser> {
            every { idpIdHash } returns idHash
        }
        val i1 = Instant.now()
        val i2 = Instant.now() - Duration.ofDays(2)
        val i3 = Instant.now() - Duration.ofHours(3)
        val e1 = Instant.now() - Duration.ofDays(1)
        val bans = listOf(
            mockBan(true, "cheh", i1, null),
            mockBan(false, "deuxième cheh", i2, e1),
            mockBan(false, "troisième cheh", i3, null)
        )
        mockHere<LinkDatabaseFacade> {
            coEvery { getBansFor(idHash) } returns bans
        }
        mockHere<LinkBanLogic> {
            every { isBanActive(bans[0]) } returns false
            every { isBanActive(bans[1]) } returns false
            every { isBanActive(bans[2]) } returns true
        }
        test {
            val s = getBanReport(u)
            assertEquals(
                """
                ## Ban information
                
                The following list contains all of the bans against you.
                
                - Ban (revoked, inactive)
                  - Reason: cheh
                  - Issued on: $i1
                  - Expires on: Does not expire
                - Ban (expired, inactive)
                  - Reason: deuxième cheh
                  - Issued on: $i2
                  - Expires on: $e1
                - Ban (active)
                  - Reason: troisième cheh
                  - Issued on: $i3
                  - Expires on: Does not expire
                """.trimIndent(), s
            )
        }
    }

    @Test
    fun `Test id access report, none`() {
        val u = mockk<LinkUser>()
        mockHere<LinkDatabaseFacade> {
            coEvery { getIdentityAccessesFor(u) } returns listOf()
        }
        test {
            val s = getIdentityAccessesReport(u)
            assertEquals(
                """
                ## ID Accesses information
                
                List of accesses made to your identity.
                
                No known ID access.
                """.trimIndent(), s
            )
        }
    }

    @Test
    fun `Test id access report, many, don't disclose identity`() {
        idAccessReportTest(false)
    }

    @Test
    fun `Test id access report, many, disclose identity`() {
        idAccessReportTest(true)
    }

    private fun idAccessReportTest(identityDisclosed: Boolean) {
        val u = mockk<LinkUser>()
        val i1 = Instant.now() - Duration.ofHours(1)
        val i2 = Instant.now() - Duration.ofDays(365)
        mockHere<LinkDatabaseFacade> {
            coEvery { getIdentityAccessesFor(u) } returns listOf(
                mockIdAccess("Michel", "get rekt", i1, false),
                mockIdAccess("JacquelinBot", "g3t r3kt", i2, true)
            )
        }
        mockHere<LinkPrivacy> {
            every { shouldDiscloseIdentity(any()) } returns identityDisclosed
        }
        test {
            val s = getIdentityAccessesReport(u)
            assertEquals(
                """
                ## ID Accesses information
                
                List of accesses made to your identity.
                
                - ID Access made on $i1 manually
                  - Requester: ${if (identityDisclosed) "Michel" else "(undisclosed)"}
                  - Reason: get rekt
                - ID Access made on $i2 automatically
                  - Requester: ${if (identityDisclosed) "JacquelinBot" else "(undisclosed)"}
                  - Reason: g3t r3kt
                """.trimIndent(), s
            )
        }
    }

    @Test
    fun `Test language preferences report, none`() {
        mockHere<LinkDatabaseFacade> {
            coEvery { getLanguagePreference("le id") } returns null
        }
        test {
            val s = getLanguagePreferencesReport("le id")
            assertEquals(
                """
                ## Language preferences
                
                You can set and clear your language preferences using the "e!lang" command in Discord.
                
                Language preference: None
                """.trimIndent(), s
            )
        }
    }

    @Test
    fun `Test language preferences report, has one`() {
        mockHere<LinkDatabaseFacade> {
            coEvery { getLanguagePreference("le id") } returns "howdy"
        }
        test {
            val s = getLanguagePreferencesReport("le id")
            assertEquals(
                """
                ## Language preferences
                
                You can set and clear your language preferences using the "e!lang" command in Discord.
                
                Language preference: howdy
                """.trimIndent(), s
            )
        }
    }

    @Test
    fun `Test entire generation`() {
        val u = mockk<LinkUser> { every { discordId } returns "le id" }
        val gdpr = spyk(LinkGdprReportImpl()) {
            every { getUserInfoReport(u) } returns "#1"
            coEvery { getTrueIdentityReport(u, "E") } returns "#2"
            coEvery { getBanReport(u) } returns "#3"
            coEvery { getIdentityAccessesReport(u) } returns "#4"
            coEvery { getLanguagePreferencesReport("le id") } returns "#5"
        }
        mockHere<LinkServerEnvironment> { every { name } returns "LeTest" }
        val s = runBlocking { gdpr.getFullReport(u, "E") }

        assertEquals(
            """
            # LeTest GDPR report for user le id
            
            This GDPR report contains information stored about you in this instance's database(1).
            
            
            #1
            
            #2
            
            #3
            
            #4
            
            #5
            
            ------
            (1): More information may be *temporarily* stored in caches, and is not included here. This information may contain your Discord username, avatar and roles. This information is stored temporarily and will be deleted after some time.
            """.trimIndent(),
            // Remove the "This report was made on (instant)" line.
            s.lines().filterIndexed { i, _ -> i != 4 }.joinToString("\n")
        )
    }

    private fun mockBan(revoked: Boolean, reason: String, issued: Instant, expiresOn: Instant?): LinkBan = mockk {
        every { this@mockk.revoked } returns revoked
        every { this@mockk.reason } returns reason
        every { this@mockk.issued } returns issued
        every { this@mockk.expiresOn } returns expiresOn
    }

    private fun mockIdAccess(
        author: String,
        reason: String,
        timestamp: Instant,
        automated: Boolean
    ): LinkIdentityAccess = mockk {
        every { authorName } returns author
        every { this@mockk.reason } returns reason
        every { this@mockk.timestamp } returns timestamp
        every { this@mockk.automated } returns automated
    }

    private fun ByteArray.encodeBase64Url() =
        java.util.Base64.getUrlEncoder().encodeToString(this)
}