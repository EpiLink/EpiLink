/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.db

import guru.zoroark.shedinja.environment.InjectionScope
import guru.zoroark.shedinja.environment.invoke
import org.epilink.bot.ServerEnvironment
import org.epilink.bot.config.IdentityProviderConfiguration
import org.epilink.bot.config.PrivacyConfiguration
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant

/**
 * Component that generates human-readable reports on someone's information that is stored in the database.
 */
interface GdprReport {
    /**
     * Generate a full report that contains everything this component can generate
     */
    suspend fun getFullReport(user: User, requester: String): String

    /**
     * Generate a report on the user's information (Discord ID, Identity Provider ID hash, creation timestamp)
     */
    fun getUserInfoReport(user: User): String

    /**
     * Generate a report on the user's true identity. Generates an ID access request that is disclosed as manual, with
     * [requester] as the author.
     *
     * This should be called before [getIdentityAccessesReport] since this generates an additional ID access.
     */
    suspend fun getTrueIdentityReport(user: User, requester: String): String

    /**
     * Generate a report on the user's bans. It also indicates which ban are revoked, expired or active.
     */
    suspend fun getBanReport(user: User): String

    /**
     * Generate a report on the user's identity accesses. This includes the name of human requester following the
     * privacy config.
     */
    suspend fun getIdentityAccessesReport(user: User): String

    /**
     * Generate a report on the user's language preferences
     */
    suspend fun getLanguagePreferencesReport(id: String): String
}

internal class GdprReportImpl(scope: InjectionScope) : GdprReport {
    private val dbf: DatabaseFacade by scope()
    private val idManager: IdentityManager by scope()
    private val banLogic: BanLogic by scope()
    private val privacy: PrivacyConfiguration by scope()
    private val env: ServerEnvironment by scope()
    private val idpCfg: IdentityProviderConfiguration by scope()

    @Suppress("MaxLineLength")
    override suspend fun getFullReport(user: User, requester: String): String =
        """
        |# ${env.name} GDPR report for user ${user.discordId}
        |
        |This GDPR report contains information stored about you in this instance's database(1).
        |
        |This report was made on ${Instant.now()}. For any request, please contact an instance administrator.
        |
        |${getUserInfoReport(user)}
        |
        |${getTrueIdentityReport(user, requester)}
        |
        |${getBanReport(user)}
        |
        |${getIdentityAccessesReport(user)}
        |
        |${getLanguagePreferencesReport(user.discordId)}
        |
        |------
        |(1): More information may be *temporarily* stored in caches, and is not included here. This information may contain your Discord username, avatar and roles. This information is stored temporarily and will be deleted after some time.
        """.trimMargin()

    override fun getUserInfoReport(user: User): String = with(user) {
        """
        ## User information
        
        General information stored about your account.
        
        - Discord ID: $discordId
        - Identity Provider (${idpCfg.name}) ID hash (Base64 URL-safe encoded): ${user.idpIdHash.encodeBase64Url()}
        - Account creation timestamp: $creationDate
        """.trimIndent()
    }

    private fun ByteArray.encodeBase64Url() =
        java.util.Base64.getUrlEncoder().encodeToString(this)

    @OptIn(UsesTrueIdentity::class) // Reports true identity, so huh, yeah
    override suspend fun getTrueIdentityReport(user: User, requester: String): String = with(user) {
        if (dbf.isUserIdentifiable(this)) {
            val identity = idManager.accessIdentity(
                this,
                false,
                requester,
                "(via EpiLink GDPR Report Service) Your identity was retrieved in order to generate a GDPR report."
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

    override suspend fun getBanReport(user: User): String =
        """
        |## Ban information
        |
        |The following list contains all of the bans against you.
        |
        |${makeBansList(user) ?: "No known bans."}
        """.trimMargin()

    @Suppress("MaxLineLength")
    private suspend fun makeBansList(user: User): String? {
        val bans = dbf.getBansFor(user.idpIdHash)
        return if (bans.isEmpty()) {
            null
        } else {
            bans.joinToString("\n") {
                """
                |- Ban ${if (it.revoked) "(revoked, inactive)" else if (banLogic.isBanActive(it)) "(active)" else "(expired, inactive)"}
                |  - Reason: ${it.reason}
                |  - Issued on: ${it.issued}
                |  - Expires on: ${it.expiresOn ?: "Does not expire"}
                """.trimMargin()
            }
        }
    }

    override suspend fun getIdentityAccessesReport(user: User): String =
        """
        |## ID Accesses information
        |
        |List of accesses made to your identity.
        |
        |${makeIdAccessList(user) ?: "No known ID access."}
        """.trimMargin()

    private suspend fun makeIdAccessList(user: User): String? {
        val idAccesses = dbf.getIdentityAccessesFor(user)

        return if (idAccesses.isEmpty()) {
            null
        } else {
            idAccesses.joinToString("\n") {
                val requester = if (privacy.shouldDiscloseIdentity(it.automated)) it.authorName else "(undisclosed)"
                val automatedString = if (it.automated) "automatically" else "manually"
                """
                |- ID Access made on ${it.timestamp} $automatedString
                |  - Requester: $requester
                |  - Reason: ${it.reason}
                """.trimMargin()
            }
        }
    }

    override suspend fun getLanguagePreferencesReport(id: String): String {
        val language = dbf.getLanguagePreference(id) ?: "None"
        return """
            |## Language preferences
            |
            |You can set and clear your language preferences using the "e!lang" command in Discord.
            |
            |Language preference: $language
        """.trimMargin()
    }
}
