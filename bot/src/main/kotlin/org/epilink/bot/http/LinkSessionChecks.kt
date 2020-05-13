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
import io.ktor.util.pipeline.PipelineContext
import org.epilink.bot.LinkException
import org.epilink.bot.StandardErrorCodes
import org.epilink.bot.db.LinkServerDatabase
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
    private val logger = LoggerFactory.getLogger("epilink.api.scheck")

    private val db: LinkServerDatabase by inject()

    private val admins: List<String> by inject(named("admins"))

    override suspend fun verifyUser(context: PipelineContext<Unit, ApplicationCall>): Boolean = with(context) {
        val session = call.sessions.get<ConnectedSession>()
        if (session == null || db.getUser(session.discordId) == null /* see #121 */) {
            call.sessions.clear<ConnectedSession>()
            logger.info("Attempted access with no or invalid SessionId (${call.request.header("SessionId")})")
            call.respond(
                HttpStatusCode.Unauthorized,
                ApiErrorResponse("You are not authenticated.", StandardErrorCodes.MissingAuthentication.toErrorData())
            )
            finish()
            false
        } else true
    }

    // TODO tests
    @OptIn(UsesTrueIdentity::class) // Checks if the admin is identifiable
    override suspend fun verifyAdmin(context: PipelineContext<Unit, ApplicationCall>): Boolean = with(context) {
        if (verifyUser(context)) {
            val session = call.sessions.get<ConnectedSession>()
                ?: throw LinkException("Call verifyUser before verifyAdmin!")
            if (session.discordId !in admins) {
                // Not an admin
                call.respond(
                    HttpStatusCode.Unauthorized,
                    StandardErrorCodes.InsufficientPermissions.toResponse("Insufficient permissions")
                )
                finish()
                false
            } else if (!db.isUserIdentifiable(session.discordId)) {
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
                true
            }
        } else false
    }
}