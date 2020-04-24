package org.epilink.bot.ratelimiting

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import org.epilink.bot.assertStatus
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RateLimitingTest {

    @Test
    fun `Test rate limiting headers`(): Unit = withTestApplication {
        val resetDur = Duration.ofMinutes(1)
        with(application) {
            install(RateLimiting) {
                limit = 5
                timeBeforeReset = resetDur
            }
            routing {
                rateLimited {
                    get("/") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }
        // Add a second to account for possible lag or delays
        val max = Instant.now() + resetDur + Duration.ofSeconds(1)
        repeat(5) { iteration ->
            handleRequest(HttpMethod.Get, "/").apply {
                assertRateLimitedHeaders(5, 4L - iteration, max)
                assertStatus(HttpStatusCode.OK)
            }
        }

        handleRequest(HttpMethod.Get, "/").apply {
            assertStatus(HttpStatusCode.TooManyRequests)
            assertRateLimitedHeaders(5, 0, max)
            assertEquals(response.headers["X-RateLimit-Reset-After"]!!, response.headers["Retry-After"])
        }
    }

    @Test
    fun `Different buckets have different counters`(): Unit = withTestApplication {
        val resetDur = Duration.ofMinutes(1)
        var useMe = "One"
        with(application) {
            install(RateLimiting) {
                limit = 5
                timeBeforeReset = resetDur
                callerKeyProducer = {
                    useMe.toByteArray()
                }
            }
            routing {
                rateLimited {
                    get("/") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }
        // Add a second to account for possible lag or delays
        val max = Instant.now() + resetDur + Duration.ofSeconds(1)

        // Simulate first client
        repeat(4) { iteration ->
            handleRequest(HttpMethod.Get, "/").apply {
                assertRateLimitedHeaders(5, 4L - iteration, max)
                assertStatus(HttpStatusCode.OK)
            }
        }
        // Simulate second client
        useMe = "Two"
        repeat(3) { iteration ->
            handleRequest(HttpMethod.Get, "/").apply {
                assertRateLimitedHeaders(5, 4L - iteration, max)
            }
        }
        // Simulate first client, whose bucket should not have expired
        useMe = "One"
        handleRequest(HttpMethod.Get, "/").apply {
            assertRateLimitedHeaders(5, 0L, max)
            assertStatus(HttpStatusCode.OK)
        }
        handleRequest(HttpMethod.Get, "/").apply {
            assertStatus(HttpStatusCode.TooManyRequests)
            assertRateLimitedHeaders(5, 0, max)
            assertEquals(response.headers["X-RateLimit-Reset-After"]!!, response.headers["Retry-After"])
        }
    }

    @Test
    fun `Different routes have different counters`(): Unit = withTestApplication {
        val resetDur = Duration.ofMinutes(1)
        with(application) {
            install(RateLimiting) {
                limit = 5
                timeBeforeReset = resetDur
            }
            routing {
                rateLimited {
                    get("/one") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
                rateLimited {
                    get("/two") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }
        // Add a second to account for possible lag or delays
        val max = Instant.now() + resetDur + Duration.ofSeconds(1)

        // Simulate first route
        repeat(4) { iteration ->
            handleRequest(HttpMethod.Get, "/one").apply {
                assertRateLimitedHeaders(5, 4L - iteration, max)
                assertStatus(HttpStatusCode.OK)
            }
        }
        // Simulate second route
        repeat(3) { iteration ->
            handleRequest(HttpMethod.Get, "/two").apply {
                assertRateLimitedHeaders(5, 4L - iteration, max)
            }
        }
        // Simulate second route, whose bucket should not have expired
        handleRequest(HttpMethod.Get, "/one").apply {
            assertRateLimitedHeaders(5, 0L, max)
            assertStatus(HttpStatusCode.OK)
        }
        handleRequest(HttpMethod.Get, "/one").apply {
            assertStatus(HttpStatusCode.TooManyRequests)
            assertRateLimitedHeaders(5, 0, max)
            assertEquals(response.headers["X-RateLimit-Reset-After"]!!, response.headers["Retry-After"])
        }
    }

    @Test
    fun `Different additional key have different counters`(): Unit = withTestApplication {
        val resetDur = Duration.ofMinutes(1)
        with(application) {
            install(RateLimiting) {
                limit = 5
                timeBeforeReset = resetDur
            }
            routing {
                rateLimited(additionalKeyExtractor = {
                    parameters["id"]!!
                }) {
                    get("/{id}") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }
        // Add a second to account for possible lag or delays
        val max = Instant.now() + resetDur + Duration.ofSeconds(1)

        // Simulate first key
        repeat(4) { iteration ->
            handleRequest(HttpMethod.Get, "/one").apply {
                assertRateLimitedHeaders(5, 4L - iteration, max)
                assertStatus(HttpStatusCode.OK)
            }
        }
        // Simulate second key
        repeat(3) { iteration ->
            handleRequest(HttpMethod.Get, "/two").apply {
                assertRateLimitedHeaders(5, 4L - iteration, max)
            }
        }
        // Simulate second key, whose bucket should not have expired
        handleRequest(HttpMethod.Get, "/one").apply {
            assertRateLimitedHeaders(5, 0L, max)
            assertStatus(HttpStatusCode.OK)
        }
        handleRequest(HttpMethod.Get, "/one").apply {
            assertStatus(HttpStatusCode.TooManyRequests)
            assertRateLimitedHeaders(5, 0, max)
            assertEquals(response.headers["X-RateLimit-Reset-After"]!!, response.headers["Retry-After"])
        }
    }

    @Test
    fun `Rate limit expires`(): Unit = withTestApplication {
        val resetDur = Duration.ofMillis(300)
        with(application) {
            install(RateLimiting) {
                limit = 3
                timeBeforeReset = resetDur
            }
            routing {
                rateLimited(additionalKeyExtractor = {
                    parameters["id"]!!
                }) {
                    get("/{id}") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }
        repeat(2) {
            handleRequest(HttpMethod.Get, "/one").apply {
                assertStatus(HttpStatusCode.OK)
            }
        }
        repeat(3) {
            handleRequest(HttpMethod.Get, "/two").apply {
                assertStatus(HttpStatusCode.OK)
            }
        }
        handleRequest(HttpMethod.Get, "/two").apply {
            assertStatus(HttpStatusCode.TooManyRequests)
        }
        Thread.sleep(300)
        handleRequest(HttpMethod.Get, "/one").apply {
            assertStatus(HttpStatusCode.OK)
            assertEquals("2", response.headers["X-RateLimit-Remaining"])
        }
        handleRequest(HttpMethod.Get, "/two").apply {
            assertStatus(HttpStatusCode.OK)
            assertEquals("2", response.headers["X-RateLimit-Remaining"])
        }
    }

    // The following test cannot be ran automatically. Uncomment it and run it. You should see in the DEBUG logs
    // a lot of output from epilink.ratelimiting.inmemory like "Removing stale bucket abcdefg...=": that means the purge
    // has ran.
    /*
    @Test
    fun `Check purge has run`(): Unit = withTestApplication {
        val resetDur = Duration.ofMillis(300)
        with(application) {
            install(RateLimiting) {
                limit = 3
                timeBeforeReset = resetDur
                limiter = InMemoryRateLimiter(5, Duration.ofMillis(400))
            }
            routing {
                rateLimited(additionalKeyExtractor = {
                    parameters["id"]!!
                }) {
                    get("/{id}") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }
        repeat(100) {
            handleRequest(HttpMethod.Get, "/$it").apply {
                assertStatus(HttpStatusCode.OK)
            }
        }
        Thread.sleep(400)
        handleRequest(HttpMethod.Get, "/1").apply {
            assertStatus(HttpStatusCode.OK)
        }
        Thread.sleep(1000) // Wait for the purge to happen, it runs in the background
    }
    */
}

/*
 * Has some weirdness to avoid rounding errors
 */
fun TestApplicationCall.assertRateLimitedHeaders(
    expectedLimit: Long,
    expectedRemaining: Long,
    resetMax: Instant,
    inMillis: Boolean = false
) {
    val actualResetMax =
        if (inMillis)
            resetMax
        else
            Instant.ofEpochSecond(ceil(resetMax.toEpochMilli() / 1000.0).toLong())

    assertEquals(expectedLimit.toString(), response.headers["X-RateLimit-Limit"])
    assertEquals(expectedRemaining.toString(), response.headers["X-RateLimit-Remaining"])
    val resetAt =
        if (inMillis)
            Instant.ofEpochMilli((response.headers["X-RateLimit-Reset"]!!.toDouble() * 1000).toLong())
        else
            Instant.ofEpochSecond((response.headers["X-RateLimit-Reset"]!!.toLong()))
    response.headers["X-RateLimit-Reset"]!!.toLong()
    println("Check Reset at ${resetAt.toEpochMilli()} <= actual reset max ${actualResetMax.toEpochMilli()}")
    assertTrue(resetAt <= actualResetMax, "Resets before max")
    val resetAfter =
        if (inMillis)
            Duration.ofMillis((response.headers["X-RateLimit-Reset-After"]!!.toDouble() * 1000).toLong())
        else
            Duration.ofSeconds(response.headers["X-RateLimit-Reset-After"]!!.toLong())
    val expectedResetAfterMillis = Duration.between(Instant.now(), actualResetMax).toMillis()
    val expectedResetAfter =
        if (inMillis)
            Duration.ofMillis(expectedResetAfterMillis)
        else
            Duration.ofSeconds(ceil(expectedResetAfterMillis / 1000.0).toLong())
    println("Check reset after ${resetAfter.toMillis()} <= ${expectedResetAfter.toMillis()}}")
    assertTrue(
        resetAfter <= expectedResetAfter,
        "Reset after less than max"
    )
    assertNotNull(response.headers["X-RateLimit-Bucket"], "Has a bucket")
}