package org.epilink.bot.ratelimiting

import io.ktor.application.*
import io.ktor.features.origin
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.request.ApplicationRequest
import io.ktor.request.header
import io.ktor.response.ApplicationResponse
import io.ktor.response.header
import io.ktor.response.respondText
import io.ktor.routing.*
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.epilink.bot.debug
import org.epilink.bot.ratelimiting.RateLimiting.Configuration
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.math.ceil

/**
 * This feature implements rate limiting functionality.
 *
 * Rate limiting is when an API will only allow a certain number of calls be made during a certain amount of time. This
 * feature implements this behavior. Rate limiting can be done on a per-route basis, meaning that different routes may
 * have different rate limits and be timed differently.
 *
 * This rate-limiting functionality is close to the one
 * [implemented by Discord](https://discordapp.com/developers/docs/topics/rate-limits), with the notable exception that
 * the `Retry-After` header returns durations in **seconds**, not milliseconds.
 * [Discord's implementation does not follow standards](https://github.com/discord/discord-api-docs/issues/1463).
 *
 * This feature by itself does not limit anything, you need to define which routes are rate limited using the
 * [rateLimited] function. For example:
 *
 * ```
 *  routing {
 *      route("user") {
 *          rateLimited(...) {
 *              get("something") { ... }
 *              post("somethingElse") { ... }
 *          }
 *      }
 *  }
 * ```
 *
 * Each rate-limited route can have its own limits and reset times. Check the [rateLimited] function for more
 * information.
 *
 * Each rate limit has a unique identifier that is made of:
 *
 * - A **caller key** unique to the requester, determined using the
 * [callerKeyProducer][Configuration.callerKeyProducer].
 *
 * - A **routing key** unique to the route (where the [rateLimited] function is used). Randomly generated.
 *
 * - An **additional key**, which is especially useful if your route matches multiple paths (e.g. there is some ID in
 * your path) and you want each path to have its own individual limit.
 *
 * All of these keys are SHA-1'd together and turned into a Base 64 string: that is the bucket we return.
 *
 * When rate-limited, this feature will end the pipeline immediately, returning a HTTP 429 error with a JSON object.
 *
 * ```
 *  {
 *      "message": "You are being rate limited.",
 *      "retry_after": 12345,
 *      "global": false
 *  }
 * ```
 *
 * `global` is always false (global rate limits are not implemented yet) and `retry_after` has the same value as the
 * `Retry-After` header
 *
 * Global rate limits are not implemented yet.
 */
class RateLimiting(configuration: Configuration) {

    /**
     * The (non-standard) headers used by the rate limiting features. These are identical to the ones used by Discord.
     */
    object Headers {
        /**
         * The number of requests that can be made
         */
        const val Limit = "X-RateLimit-Limit"

        /**
         * The number of remaining requests that can be made before the reset time.
         */
        const val Remaining = "X-RateLimit-Remaining"

        /**
         * The epoch time at which the rate limit resets
         */
        const val Reset = "X-RateLimit-Reset"

        /**
         * The total time in seconds (either integer or floating) of when the current rate limit will reset
         */
        const val ResetAfter = "X-RateLimit-Reset-After"

        /**
         * Header for precision (second or millisecond)
         */
        const val Precision = "X-RateLimit-Precision"

        /**
         * "Bucket" header
         */
        const val Bucket = "X-RateLimit-Bucket"
    }

    /**
     * Configuration class for the Rate Limiting feature
     */
    class Configuration {
        /**
         * The limiter implementation. See [RateLimiter] for more information.
         *
         * The [RateLimiter] is intended for use with Strings here.
         *
         * An [InMemoryRateLimiter] by default which needs 100 items stored to purge itself (one purge per hour).
         */
        var limiter: RateLimiter<String> = InMemoryRateLimiter(100, Duration.ofHours(1))

        /**
         * The default limit (i.e. amount of requests) allowed for per-route rate limits. Can be overridden via the
         * [rateLimited] function.
         *
         * 50 requests by default
         */
        var limit: Long = 50L

        /**
         * The default amount of time before a rate limit is reset. Can be overridden via the [rateLimited] function.
         *
         * 2 minutes by default
         */
        var timeBeforeReset: Duration = Duration.ofMinutes(2)

        /**
         * This is the function that generates caller keys. The default uses the remote host as the caller key.
         */
        var callerKeyProducer: ApplicationCall.() -> ByteArray = {
            request.origin.remoteHost.toByteArray()
        }
    }

    internal val random = SecureRandom()
    private val rateLimiter = configuration.limiter
    private val limit = configuration.limit
    private val timeBeforeReset = configuration.timeBeforeReset.toMillis()
    private val keyProducer = configuration.callerKeyProducer
    private val logger = LoggerFactory.getLogger("epilink.ratelimiting")

    /**
     * Feature companion object for the rate limiting feature
     */
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, RateLimiting> {
        override val key = AttributeKey<RateLimiting>("RateLimiting")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): RateLimiting {
            return RateLimiting(Configuration().apply(configure))
        }
    }

    /**
     * Handles a rate-limited call.
     *
     * @param limit Limit override
     * @param timeBeforeReset Reset time override (in milliseconds)
     */
    internal suspend fun handleRateLimitedCall(
        limit: Long?,
        timeBeforeReset: Long?,
        context: PipelineContext<Unit, ApplicationCall>,
        fullKeyProcessor: suspend (ByteArray) -> String
    ) = with(context.call) {
        // Initialize values
        val bucket = fullKeyProcessor(context.call.keyProducer())
        val actualLimit = limit ?: this@RateLimiting.limit
        val actualTimeBeforeReset = timeBeforeReset ?: this@RateLimiting.timeBeforeReset
        val rateContext = RateLimitingContext(actualLimit, actualTimeBeforeReset)
        // Handle the rate limit
        val rate = rateLimiter.handle(rateContext, bucket)
        // Append information to reply
        val inMillis = request.shouldRateLimitTimeBeInMillis()
        val remainingTimeBeforeReset = Duration.between(Instant.now(), rate.resetAt)
        response.appendRateLimitHeaders(rate, inMillis, remainingTimeBeforeReset, rateContext, bucket)
        // Interrupt call if we should limit it
        if (rate.shouldLimit()) {
            logger.debug { "Bucket $bucket (remote host ${request.origin.remoteHost}) is being rate limited, resets at ${rate.resetAt}" }
            val retryAfter = toHeaderValueWithPrecision(false, remainingTimeBeforeReset.toMillis())
            // Always in seconds
            response.header(HttpHeaders.RetryAfter, retryAfter)
            respondText(ContentType.Application.Json, HttpStatusCode.TooManyRequests) {
                """{"message":"You are being rate limited.","retry_after":$retryAfter,"global":false}"""
            }
            context.finish()
        } else {
            logger.debug {
                "Bucket $bucket (remote host ${request.origin.remoteHost}) passes rate limit, remaining = ${rate.remainingRequests - 1}, resets at ${rate.resetAt}"
            }
            context.proceed()
        }
    }
}

/**
 * Intercepts every call made inside the route block and adds rate-limiting to it.
 *
 * This function requires the [RateLimiting] feature to be installed.
 *
 * Optionally, you can override some parameters that will only apply to this route.
 *
 * @param limit Overrides the global limit set when configuring the feature. Maximum amount of requests that can be
 * performed before being rate-limited and receiving HTTP 429 errors.
 * @param timeBeforeReset Overrides the global time before reset set when configuring the feature. Time before a
 * rate-limit expires.
 * @param additionalKeyExtractor Function used for retrieving the additional key. See [RateLimiting] for more
 * information.
 * @param callback Block for configuring the rate-limited route
 */
fun Route.rateLimited(
    limit: Long? = null,
    timeBeforeReset: Duration? = null,
    additionalKeyExtractor: ApplicationCall.() -> String = { "" },
    callback: Route.() -> Unit
): Route {
    // Create the route
    val rateLimitedRoute = createChild(object : RouteSelector(1.0) {
        override fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
            RouteSelectorEvaluation.Constant
    })
    // Rate limiting feature object
    val rateLimiting = application.feature(RateLimiting)
    // Generate a key for this route
    val arr = ByteArray(64)
    rateLimiting.random.nextBytes(arr)
    // Intercepting every call and checking the rate limit
    rateLimitedRoute.intercept(ApplicationCallPipeline.Features) {
        rateLimiting.handleRateLimitedCall(limit, timeBeforeReset?.toMillis(), this) {
            // This is the key generation. We simply SHA1 together all three keys.
            sha1(it, call.additionalKeyExtractor().toByteArray(), arr)
        }
    }
    rateLimitedRoute.callback()
    return rateLimitedRoute
}

/**
 * Shortcut function for checking if the precision of the rate limit should be in milliseconds (true) or seconds (false)
 */
private fun ApplicationRequest.shouldRateLimitTimeBeInMillis(): Boolean =
    header(RateLimiting.Headers.Precision) == "millisecond"

/**
 * Appends rate-limiting related headers to the response
 */
private fun ApplicationResponse.appendRateLimitHeaders(
    rate: Rate,
    inMillis: Boolean,
    remainingTimeBeforeReset: Duration,
    context: RateLimitingContext,
    bucket: String
) {
    header(RateLimiting.Headers.Limit, context.limit)
    header(RateLimiting.Headers.Remaining, (rate.remainingRequests - 1).coerceAtLeast(0))
    header(RateLimiting.Headers.Reset, toHeaderValueWithPrecision(inMillis, rate.resetAt.toEpochMilli()))
    header(RateLimiting.Headers.ResetAfter, toHeaderValueWithPrecision(inMillis, remainingTimeBeforeReset.toMillis()))
    header(RateLimiting.Headers.Bucket, bucket)
}

/**
 * Turns [millis] into either seconds (integer) if [inMillis] is false or seconds with milliseconds precision (double)
 * if [inMillis] is true and convert them to a String.
 *
 * @param millis A value in milliseconds
 */
private fun toHeaderValueWithPrecision(inMillis: Boolean, millis: Long): String {
    return if (inMillis)
        (millis / 1000.0).toString()
    else
        ceil(millis / 1000.0).toInt().toString()
}

/**
 * Creates a Base 64 string from SHA-1'ing all of the arrays, treating them as a single byte array
 */
private suspend fun sha1(vararg arr: ByteArray): String = withContext(Dispatchers.Default) {
    val md = MessageDigest.getInstance("SHA-1")
    arr.forEach { md.update(it) }
    Base64.getEncoder().encodeToString(md.digest())
}