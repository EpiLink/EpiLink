package org.epilink.bot.http

import com.auth0.jwt.algorithms.Algorithm
import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import org.epilink.bot.LinkServerEnvironment
import org.epilink.bot.config.LinkTokens
import org.epilink.bot.http.classes.InstanceInformation

/**
 * The back-end, defining API endpoints and more
 */
class LinkBackEnd(
    /**
     * The HTTP server that created this back end
     */
    private val server: LinkHttpServer,
    /**
     * The environment the back end lives in
     */
    private val env: LinkServerEnvironment,
    /**
     * The algorithm to use for JWT
     */
    private val jwtAlgorithm: Algorithm,
    /**
     * The duration of a session (for use with [makeJwt])
     */
    private val sessionDuration: Long,

    /**
     * Tokens
     */
    private val secrets: LinkTokens
) {
    // TODO config entry for custom tenant instead of just common
    private val authStubMsft =
        "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?" +
                listOf(
                    "client_id=${secrets.msftOAuthClientId}",
                    "response_type=code",
                    "prompt=select_account",
                    "scope=User.Read"
                ).joinToString("&")

    private val authStubDiscord =
        "https://https://discordapp.com/api/oauth2/authorize?" +
                listOf(
                    "client_id=${secrets.discordOAuthClientId}",
                    "reponse_type=code",
                    // Allows access to user information (w/o email address)
                    "scope=identify",
                    // Dodge authorization screen if user is already connected
                    "prompt=none"
                ).joinToString("&")

    /**
     * Defines the API endpoints. Served under /api/v1
     *
     * Anything responded in here SHOULD use [ApiResponse] in JSON form.
     */
    fun Route.epilinkApiV1() {
        route("meta") {
            @ApiEndpoint("GET /api/v1/meta/info")
            get("info") {
                call.respond(ApiResponse(true, data = getInstanceInformation()))
            }
        }

        /*
         * Just a hello world for now, answering in JSON
         */
        get("hello") {
            call.respond(
                ApiResponse(true, "Hello World", null)
            )
        }

        route("register") {
            @ApiEndpoint("GET /api/v1/register/info")
            get("info") {
                val session =
                    call.sessions.getOrSet { RegisterSession() }
                call.respondRegistrationStatus(session)
            }

            @ApiEndpoint("DELETE /api/v1/register")
            delete {
                call.sessions.clear<RegisterSession>()
                call.respond(HttpStatusCode.OK)
            }
        }
    }

    private fun getInstanceInformation(): InstanceInformation =
        InstanceInformation(
            title = env.name,
            logo = null, // TODO add a cfg entry for the logo
            authorizeStub_msft = authStubMsft,
            authorizeStub_discord = authStubDiscord
        )

    private suspend fun ApplicationCall.respondRegistrationStatus(
        session: RegisterSession
    ) {
        respond(
            ApiResponse(
                success = true,
                data = RegistrationInformation(
                    email = session.email,
                    discordAvatarUrl = session.discordAvatarUrl,
                    discordUsername = session.discordUsername
                )
            )
        )
    }
}
