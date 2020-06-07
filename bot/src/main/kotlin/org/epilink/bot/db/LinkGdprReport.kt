/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.db

import org.epilink.bot.LinkServerEnvironment
import org.epilink.bot.config.LinkPrivacy
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.Instant

interface LinkGdprReport {
    suspend fun getFullReport(user: LinkUser): String
    fun getUserInfoReport(user: LinkUser): String
    suspend fun getTrueIdentityReport(user: LinkUser): String
    suspend fun getBanReport(user: LinkUser): String
    suspend fun getIdentityAccessesReport(user: LinkUser): String
}

internal class LinkGdprReportImpl : LinkGdprReport, KoinComponent {
    private val dbf: LinkDatabaseFacade by inject()
    private val idManager: LinkIdManager by inject()
    private val banLogic: LinkBanLogic by inject()
    private val privacy: LinkPrivacy by inject()
    private val env: LinkServerEnvironment by inject()

    override suspend fun getFullReport(user: LinkUser): String =
        """
        |# ${env.name} GDPR report for user ${user.discordId}
        |
        |This GDPR report contains information stored about you in this instance's database(1).
        |
        |This report was made on ${Instant.now()}. For any request, please contact an instance administrator.
        |
        |${getUserInfoReport(user)}
        |
        |${getTrueIdentityReport(user)}
        |
        |${getBanReport(user)}
        |
        |${getIdentityAccessesReport(user)}
        |
        |(end of report)
        |   
        |(1): More information may be *temporarily* stored in caches, and is not included here. This information may contain your Discord username, avatar and roles. This information is stored temporarily and will be deleted after some time.
        """.trimMargin()

    override fun getUserInfoReport(user: LinkUser): String = with(user) {
        """
        ## User information
        
        General information stored about your account.
        
        - Discord ID: $discordId
        - Microsoft ID hash (Base64 URL-safe encoded): ${user.msftIdHash.encodeBase64Url()}
        - Account creation timestamp: $creationDate
        """.trimIndent()
    }

    private fun ByteArray.encodeBase64Url() =
        java.util.Base64.getUrlEncoder().encodeToString(this)

    @OptIn(UsesTrueIdentity::class) // Reports true identity, so huh, yeah
    override suspend fun getTrueIdentityReport(user: LinkUser): String = with(user) {
        if (dbf.isUserIdentifiable(this)) {
            val identity = idManager.accessIdentity(
                this,
                true,
                "EpiLink GDPR Report Service",
                "Your identity was retrieved in order to generate a GDPR report."
            )
            """
            ## Identity information
            
            Information stored because you enabled the "Remember my identity" feature.
            
            - True identity: $identity
            """.trimIndent()
        } else {
            """
            ## Identity information
            
            You did not enable the "Remember my identity" feature.
            
            - True identity: Not stored.
            """.trimIndent()
        }
    }

    override suspend fun getBanReport(user: LinkUser): String =
        """
        |## Ban information
        |
        |The following list contains all of the bans against you.
        |
        |${makeBansList(user) ?: "No known bans."}
        """.trimMargin()

    private suspend fun makeBansList(user: LinkUser): String? {
        val bans = dbf.getBansFor(user.msftIdHash)
        return if (bans.isEmpty()) {
            null
        } else {
            bans.joinToString("\n") {
                """
                |- Ban ${if (it.revoked) "(revoked, inactive)" else if (banLogic.isBanActive(it)) "(active)" else "(expired/inactive)"}
                |  - Reason: ${it.reason}
                |  - Issued on: ${it.issued}
                |  - Expires on: ${it.expiresOn ?: "Does not expire"}
                """.trimMargin()
            }
        }
    }

    override suspend fun getIdentityAccessesReport(user: LinkUser): String =
        """
        |## ID Accesses information
        |
        |List of accesses made to your identity.
        |
        |${makeIdAccessList(user) ?: "No known ID access."}
        """.trimMargin()

    private suspend fun makeIdAccessList(user: LinkUser): String? {
        val idAccesses = dbf.getIdentityAccessesFor(user)
        return if (idAccesses.isEmpty()) {
            null
        } else {
            idAccesses.joinToString("\n") {
                """
                |- ID Access made on ${it.timestamp} ${if (it.automated) "automatically" else "manually"}
                |  - Requester: ${if (privacy.shouldDiscloseIdentity(it.automated)) it.authorName else "(undisclosed)"}
                |  - Reason: ${it.reason}
                """.trimMargin()
            }
        }
    }

}