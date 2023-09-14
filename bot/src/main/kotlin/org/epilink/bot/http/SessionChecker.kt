/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.http

import guru.zoroark.tegral.di.environment.InjectionScope
import guru.zoroark.tegral.di.environment.invoke
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext
import org.epilink.bot.EpiLinkException
import org.epilink.bot.StandardErrorCodes
import org.epilink.bot.db.AdminStatus
import org.epilink.bot.db.DatabaseFacade
import org.epilink.bot.db.PermissionChecks
import org.epilink.bot.db.User
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.http.sessions.ConnectedSession
import org.epilink.bot.toResponse
import org.slf4j.LoggerFactory

/**
 * Session check classes validate user sessions. These are intended to be used inside pipeline interceptions.
 */
interface SessionChecker {

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

internal class SessionCheckerImpl(scope: InjectionScope) : SessionChecker {
    private val logger = LoggerFactory.getLogger("epilink.api.sessioncheck")
    private val dbFacade: DatabaseFacade by scope()
    private val permission: PermissionChecks by scope()

    override suspend fun verifyUser(context: PipelineContext<Unit, ApplicationCall>): Boolean = with(context) {
        val session = call.sessions.get<ConnectedSession>()
        val user = session?.let { dbFacade.getUser(session.discordId) }
        if (user == null) {
            call.sessions.clear<ConnectedSession>()
            logger.info("Attempted access with no or invalid SessionId (${call.request.header("SessionId")})")
            call.respond(
                HttpStatusCode.Unauthorized,
                StandardErrorCodes.MissingAuthentication.toResponse()
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
            ?: throw EpiLinkException("Call verifyUser before verifyAdmin!")
        when (permission.canPerformAdminActions(user)) {
            AdminStatus.NotAdmin -> {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    StandardErrorCodes.InsufficientPermissions.toResponse()
                )
                finish()
                false
            }

            AdminStatus.AdminNotIdentifiable -> {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    StandardErrorCodes.InsufficientPermissions.toResponse(
                        "You need to have your identity recorded to perform administrative tasks.",
                        "sc.ani"
                    )
                )
                finish()
                false
            }

            AdminStatus.Admin -> {
                call.attributes.put(adminObjAttribute, user)
                true
            }
        }
    }
}

internal val userObjAttribute = AttributeKey<User>("EpiLinkUserObject")

/**
 * Retrieve the user. You must have previously called [SessionChecker.verifyUser] in the pipeline.
 */
val ApplicationCall.user: User
    get() = this.attributes[userObjAttribute]

internal val adminObjAttribute = AttributeKey<User>("EpiLinkAdminObject")

/**
 * Retrieve the admin. You must have previously called [SessionChecker.verifyAdmin] in the pipeline.
 */
val ApplicationCall.admin: User
    get() = this.attributes[adminObjAttribute]
