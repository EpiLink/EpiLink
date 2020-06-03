/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import io.ktor.application.ApplicationCall
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.response.ApplicationResponse
import io.ktor.response.ApplicationSendPipeline
import io.ktor.sessions.CurrentSession
import io.ktor.sessions.sessions
import io.ktor.util.pipeline.PipelineContext
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.epilink.bot.db.LinkServerDatabase
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.http.ApiErrorResponse
import org.epilink.bot.http.LinkSessionChecks
import org.epilink.bot.http.LinkSessionChecksImpl
import org.epilink.bot.http.sessions.ConnectedSession
import org.koin.core.get
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.mock.declare
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionChecksTest : KoinBaseTest(
    module {
        single<LinkSessionChecks> { LinkSessionChecksImpl() }
    }
) {
    /**
     * Used for setting up a fake session by mocking some session functionality.
     *
     * Run this when you need to inject a session directly.
     *
     * Returns the mocked CurrentSession and ApplicationCall objects
     */
    private fun setupMockedSession(actualSession: ConnectedSession?): Pair<ApplicationCall, CurrentSession> {
        val headersMocked = mockk<Headers> {
            every { this@mockk[any()] } returns "Mocked"
        }
        val call = mockk<ApplicationCall> {
            every { request } returns mockk {
                every { headers } returns headersMocked
            }
        }
        val session: CurrentSession = mockk {
            every { findName(any()) } returns "nnname"
            every { this@mockk.get("nnname") } returns actualSession
        }
        mockkStatic("io.ktor.sessions.SessionsKt")
        every { call.sessions } returns session
        return call to session
    }

    /**
     * Object returned by [mockResponse]
     */
    private class MockedResponse(
        /**
         * Slot in which the sent response is put
         */
        val slot: CapturingSlot<Any>,
        /**
         * The send pipeline that was mocked
         */
        val pipeline: ApplicationSendPipeline,
        /**
         * The application response that was mocked
         */
        val response: ApplicationResponse
    )

    /**
     * Sets up necessary components for a mocked ApplicationCall to receive a response, and returns the obejcts that
     * were mocked in order to do this.
     */
    private fun mockResponse(mockedCall: ApplicationCall): MockedResponse {
        val respSlot = slot<Any>()
        val respPipeline = mockk<ApplicationSendPipeline> {
            coEvery { execute(mockedCall, capture(respSlot)) } answers { respSlot.captured }
        }
        val resp = mockk<ApplicationResponse> {
            every { status(HttpStatusCode.Unauthorized) } just runs
            every { pipeline } returns respPipeline
        }
        every { mockedCall.response } returns resp
        return MockedResponse(respSlot, respPipeline, resp)
    }

    @Test
    fun `Test user verification wrong session`() {
        // This test is cursed and i hate it
        val (call, session) = setupMockedSession(null)
        with(session) {
            every { clear("nnname") } just runs
        }
        val mr = mockResponse(call)
        val context = mockk<PipelineContext<Unit, ApplicationCall>> {
            every { context } returns call
            every { finish() } just runs
        }
        runBlocking {
            assertFalse(get<LinkSessionChecks>().verifyUser(context))
            val captured = mr.slot.captured
            assertTrue(captured is ApiErrorResponse)
            assertEquals(300, captured.data!!.code)
        }
        coVerify {
            session.clear("nnname")
            mr.response.status(HttpStatusCode.Unauthorized)
            mr.pipeline.execute(call, any())
        }
    }

    @Test
    fun `Test user verification user does not exist`() {
        val (call, session) = setupMockedSession(ConnectedSession("userid", "username", null))
        with(session) {
            every { clear("nnname") } just runs
        }
        val mr = mockResponse(call)
        val context = mockk<PipelineContext<Unit, ApplicationCall>> {
            every { context } returns call
            every { finish() } just runs
        }
        mockHere<LinkServerDatabase> {
            coEvery { getUser("userid") } returns null
        }
        runBlocking {
            assertFalse(get<LinkSessionChecks>().verifyUser(context))
            val captured = mr.slot.captured
            assertTrue(captured is ApiErrorResponse)
            assertEquals(300, captured.data!!.code)
        }
        coVerify {
            session.clear("nnname")
            mr.response.status(HttpStatusCode.Unauthorized)
            mr.pipeline.execute(call, any())
            context.finish()
        }
    }

    @Test
    fun `Test user verification user exists`() {
        val (call, _) = setupMockedSession(ConnectedSession("userid", "username", null))
        val context = mockk<PipelineContext<Unit, ApplicationCall>> {
            every { context } returns call
        }
        mockHere<LinkServerDatabase> {
            coEvery { getUser("userid") } returns mockk()
        }
        runBlocking {
            assertTrue(get<LinkSessionChecks>().verifyUser(context))
        }
    }

    @Test
    fun `Test admin, not an admin`() {
        val (call, _) = setupMockedSession(ConnectedSession("userid", "username", null))
        val mr = mockResponse(call)
        val context = mockk<PipelineContext<Unit, ApplicationCall>> {
            every { context } returns call
            every { finish() } just runs
        }
        declare(named("admins")) { listOf("adminid") }
        runBlocking {
            assertFalse(get<LinkSessionChecks>().verifyAdmin(context))
            val captured = mr.slot.captured
            assertTrue(captured is ApiErrorResponse)
            assertEquals(301, captured.data!!.code)
        }
        coVerify {
            mr.response.status(HttpStatusCode.Unauthorized)
            mr.pipeline.execute(call, any())
            context.finish()
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test admin, admin but not identifiable`() {
        val (call, _) = setupMockedSession(ConnectedSession("adminid", "username", null))
        val mr = mockResponse(call)
        val context = mockk<PipelineContext<Unit, ApplicationCall>> {
            every { context } returns call
            every { finish() } just runs
        }
        mockHere<LinkServerDatabase> {
            coEvery { isUserIdentifiable("adminid") } returns false
        }
        declare(named("admins")) { listOf("adminid") }
        runBlocking {
            assertFalse(get<LinkSessionChecks>().verifyAdmin(context))
            val captured = mr.slot.captured
            assertTrue(captured is ApiErrorResponse)
            assertEquals(301, captured.data!!.code)
        }
        coVerify {
            mr.response.status(HttpStatusCode.Unauthorized)
            mr.pipeline.execute(call, any())
            context.finish()
        }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test admin when correct`() {
        val (call, _) = setupMockedSession(ConnectedSession("adminid", "username", null))
        val context = mockk<PipelineContext<Unit, ApplicationCall>> {
            every { context } returns call
        }
        mockHere<LinkServerDatabase> {
            coEvery { isUserIdentifiable("adminid") } returns true
        }
        declare(named("admins")) { listOf("adminid") }
        runBlocking {
            assertTrue(get<LinkSessionChecks>().verifyAdmin(context))
        }
    }
}