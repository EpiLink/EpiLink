/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.http

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.sessions.clear
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext
import org.epilink.bot.LinkException
import org.epilink.bot.StandardErrorCodes
import org.epilink.bot.db.LinkDatabaseFacade
import org.epilink.bot.db.LinkUser
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.http.sessions.ConnectedSession
import org.epilink.bot.toErrorData
import org.epilink.bot.toResponse
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.core.qualifier.named
import org.slf4j.LoggerFactory

/**
 * Session check classes validate user sessions. These are intended to be used inside pipeline interceptions.
 */
interface LinkSessionChecks {

    /**
     * Verify the user session, and finish the pipeline early if the user is not valid.
     *
     * This function responds and calls finish() if the session is invalid but does NOT call proceed() if the session is
     * valid in case the caller wants to perform additional checks.
     *
     * @return true if the call should continue, false if the pipeline has been finished early
     */
    suspend fun verifyUser(context: PipelineContext<Unit, ApplicationCall>): Boolean

    /**
     * Verify the admin session permissions and finish the pipeline early if the user is not valid. This function
     * assumes that the user is valid: call [verifyUser] before calling this function!
     *
     * This function responds and calls finish() if the session is invalid but does NOT call proceed() if the session is
     * valid in case the caller wants to perform additional checks.
     *
     * @return true if the call should continue, false if the pipeline has been finished early
     */
    suspend fun verifyAdmin(context: PipelineContext<Unit, ApplicationCall>): Boolean
}

internal class LinkSessionChecksImpl : LinkSessionChecks, KoinComponent {
    private val logger = LoggerFactory.getLogger("epilink.api.sessioncheck")
    private val admins: List<String> by inject(named("admins"))
    private val dbFacade: LinkDatabaseFacade by inject()

    override suspend fun verifyUser(context: PipelineContext<Unit, ApplicationCall>): Boolean = with(context) {
        val session = call.sessions.get<ConnectedSession>()
        val user = session?.let { dbFacade.getUser(session.discordId) }
        if (user == null) {
            call.sessions.clear<ConnectedSession>()
            logger.info("Attempted access with no or invalid SessionId (${call.request.header("SessionId")})")
            call.respond(
                HttpStatusCode.Unauthorized,
                ApiErrorResponse("You are not authenticated.", StandardErrorCodes.MissingAuthentication.toErrorData())
            )
            finish()
            false
        } else {
            call.attributes.put(userObjAttribute, user)
            true
        }
    }

    @OptIn(UsesTrueIdentity::class) // Checks if the admin is identifiable
    override suspend fun verifyAdmin(context: PipelineContext<Unit, ApplicationCall>): Boolean = with(context) {
        val user = call.attributes.getOrNull(userObjAttribute)
            ?: throw LinkException("Call verifyUser before verifyAdmin!")
        if (user.discordId !in admins) {
            // Not an admin
            call.respond(
                HttpStatusCode.Unauthorized,
                StandardErrorCodes.InsufficientPermissions.toResponse("Insufficient permissions")
            )
            finish()
            false
        } else if (!dbFacade.isUserIdentifiable(user)) {
            // Admin but not identifiable
            call.respond(
                HttpStatusCode.Unauthorized,
                StandardErrorCodes.InsufficientPermissions.toResponse(
                    "You need to have your identity recorded to perform administrative tasks"
                )
            )
            finish()
            false
        } else {
            call.attributes.put(adminObjAttribute, user)
            true
        }
    }
}

internal val userObjAttribute = AttributeKey<LinkUser>("EpiLinkUserObject")

/**
 * Retrieve the user. You must have previously called [LinkSessionChecks.verifyUser] in the pipeline.
 */
val ApplicationCall.user: LinkUser
    get() = this.attributes[userObjAttribute]

internal val adminObjAttribute = AttributeKey<LinkUser>("EpiLinkAdminObject")

/**
 * Retrieve the admin. You must have previously called [LinkSessionChecks.verifyAdmin] in the pipeline.
 */
val ApplicationCall.admin: LinkUser
    get() = this.attributes[adminObjAttribute]