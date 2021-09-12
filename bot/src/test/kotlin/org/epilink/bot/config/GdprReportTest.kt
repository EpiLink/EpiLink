/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.config

import guru.zoroark.shedinja.dsl.put
import guru.zoroark.shedinja.environment.get
import guru.zoroark.shedinja.test.ShedinjaBaseTest
import guru.zoroark.shedinja.test.UnsafeMutableEnvironment
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.epilink.bot.ServerEnvironment
import org.epilink.bot.db.Ban
import org.epilink.bot.db.BanLogic
import org.epilink.bot.db.DatabaseFacade
import org.epilink.bot.db.GdprReport
import org.epilink.bot.db.GdprReportImpl
import org.epilink.bot.db.IdentityAccess
import org.epilink.bot.db.IdentityManager
import org.epilink.bot.db.User
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.putMock
import org.epilink.bot.stest
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class GdprReportTest : ShedinjaBaseTest<GdprReport>(
    GdprReport::class, {
        put<GdprReport>(::GdprReportImpl)
    }
) {
    @Test
    fun `Test user info report`() = stest {
        val i = Instant.now()
        val id = byteArrayOf(1, 2, 3, 4)
        val u = mockk<User> {
            every { discordId } returns "1234"
            every { creationDate } returns i
            every { idpIdHash } returns id
        }
        putMock<IdentityProviderConfiguration> {
            every { name } returns "testy"
        }
        val s = subject.getUserInfoReport(u)
        assertEquals(
            """
                ## User information

                General information stored about your account.

                - Discord ID: 1234
                - Identity Provider (testy) ID hash (Base64 URL-safe encoded): ${id.encodeBase64Url()}
                - Account creation timestamp: $i
                """.trimIndent(),
            s
        )
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test true identity report (not identifiable)`() = stest {
        val u = mockk<User>()
        putMock<DatabaseFacade> { coEvery { isUserIdentifiable(u) } returns false }
        val s = subject.getTrueIdentityReport(u, "big boi")
        assertEquals(
            """
                ## Identity information
            
                You did not enable the "Remember my identity" feature.
            
                - True identity: Not stored.
                """.trimIndent(),
            s
        )

    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test true identity report (identifiable)`() = stest {
        val u = mockk<User>()
        putMock<DatabaseFacade> { coEvery { isUserIdentifiable(u) } returns true }
        putMock<IdentityManager> {
            coEvery {
                accessIdentity(
                    u,
                    false,
                    "big boi",
                    "(via EpiLink GDPR Report Service) Your identity was retrieved in order to generate a GDPR report."
                )
            } returns "le identity"
        }
        val s = subject.getTrueIdentityReport(u, "big boi")
        assertEquals(
            """
                ## Identity information
            
                Information stored because you enabled the "Remember my identity" feature.
            
                - True identity: le identity
                """.trimIndent(),
            s
        )
    }

    @Test
    fun `Test ban report (no bans)`() = stest {
        val idHash = byteArrayOf(1, 2, 3, 4)
        val u = mockk<User> {
            every { idpIdHash } returns idHash
        }
        putMock<DatabaseFacade> {
            coEvery { getBansFor(idHash) } returns listOf()
        }
        val s = subject.getBanReport(u)
        assertEquals(
            """
                ## Ban information
                
                The following list contains all of the bans against you.
                
                No known bans.
                """.trimIndent(),
            s
        )
    }

    @Test
    fun `Test ban report (has bans)`() = stest {
        val idHash = byteArrayOf(1, 2, 3, 4)
        val u = mockk<User> {
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
        putMock<DatabaseFacade> {
            coEvery { getBansFor(idHash) } returns bans
        }
        putMock<BanLogic> {
            every { isBanActive(bans[0]) } returns false
            every { isBanActive(bans[1]) } returns false
            every { isBanActive(bans[2]) } returns true
        }
        val s = subject.getBanReport(u)
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
                """.trimIndent(),
            s
        )
    }

    @Test
    fun `Test id access report, none`() = stest {
        val u = mockk<User>()
        putMock<DatabaseFacade> {
            coEvery { getIdentityAccessesFor(u) } returns listOf()
        }
        val s = subject.getIdentityAccessesReport(u)
        assertEquals(
            """
                ## ID Accesses information
                
                List of accesses made to your identity.
                
                No known ID access.
                """.trimIndent(),
            s
        )
    }

    @Test
    fun `Test id access report, many, don't disclose identity`() = stest {
        idAccessReportTest(false)
    }

    @Test
    fun `Test id access report, many, disclose identity`() = stest {
        idAccessReportTest(true)
    }

    private suspend fun UnsafeMutableEnvironment.idAccessReportTest(identityDisclosed: Boolean) {
        val u = mockk<User>()
        val i1 = Instant.now() - Duration.ofHours(1)
        val i2 = Instant.now() - Duration.ofDays(365)
        putMock<DatabaseFacade> {
            coEvery { getIdentityAccessesFor(u) } returns listOf(
                mockIdAccess("Michel", "get rekt", i1, false),
                mockIdAccess("JacquelinBot", "g3t r3kt", i2, true)
            )
        }
        putMock<PrivacyConfiguration> {
            every { shouldDiscloseIdentity(any()) } returns identityDisclosed
        }
        val s = subject.getIdentityAccessesReport(u)
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
                """.trimIndent(),
            s
        )
    }

    @Test
    fun `Test language preferences report, none`() = stest {
        putMock<DatabaseFacade> {
            coEvery { getLanguagePreference("le id") } returns null
        }
        val s = subject.getLanguagePreferencesReport("le id")
        assertEquals(
            """
                ## Language preferences
                
                You can set and clear your language preferences using the "e!lang" command in Discord.
                
                Language preference: None
                """.trimIndent(),
            s
        )
    }

    @Test
    fun `Test language preferences report, has one`() = stest {
        putMock<DatabaseFacade> {
            coEvery { getLanguagePreference("le id") } returns "howdy"
        }
        val s = subject.getLanguagePreferencesReport("le id")
        assertEquals(
            """
                ## Language preferences
                
                You can set and clear your language preferences using the "e!lang" command in Discord.
                
                Language preference: howdy
                """.trimIndent(),
            s
        )
    }

    @Test
    fun `Test entire generation`() = stest {
        val u = mockk<User> { every { discordId } returns "le id" }
        put {
            spyk(GdprReportImpl(scope)) {
                every { getUserInfoReport(u) } returns "#1"
                coEvery { getTrueIdentityReport(u, "E") } returns "#2"
                coEvery { getBanReport(u) } returns "#3"
                coEvery { getIdentityAccessesReport(u) } returns "#4"
                coEvery { getLanguagePreferencesReport("le id") } returns "#5"
            }
        }
        val gdpr = get<GdprReportImpl>()
        putMock<ServerEnvironment> { every { name } returns "LeTest" }
        val s = gdpr.getFullReport(u, "E")

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

    private fun mockBan(revoked: Boolean, reason: String, issued: Instant, expiresOn: Instant?): Ban = mockk {
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
    ): IdentityAccess = mockk {
        every { authorName } returns author
        every { this@mockk.reason } returns reason
        every { this@mockk.timestamp } returns timestamp
        every { this@mockk.automated } returns automated
    }

    private fun ByteArray.encodeBase64Url() =
        java.util.Base64.getUrlEncoder().encodeToString(this)
}
