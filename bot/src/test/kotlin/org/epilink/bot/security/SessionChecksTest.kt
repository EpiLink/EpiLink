/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.security

import guru.zoroark.tegral.di.dsl.put
import guru.zoroark.tegral.di.test.TegralSubjectTest
import guru.zoroark.tegral.di.test.mockk.putMock
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.ApplicationResponse
import io.ktor.server.response.ApplicationSendPipeline
import io.ktor.server.sessions.CurrentSession
import io.ktor.server.sessions.sessions
import io.ktor.util.Attributes
import io.ktor.util.pipeline.PipelineContext
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.epilink.bot.db.AdminStatus
import org.epilink.bot.db.DatabaseFacade
import org.epilink.bot.db.PermissionChecks
import org.epilink.bot.db.User
import org.epilink.bot.db.UsesTrueIdentity
import org.epilink.bot.http.ApiErrorResponse
import org.epilink.bot.http.SessionChecker
import org.epilink.bot.http.SessionCheckerImpl
import org.epilink.bot.http.sessions.ConnectedSession
import org.epilink.bot.http.userObjAttribute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionChecksTest : TegralSubjectTest<SessionChecker>(
    SessionChecker::class,
    { put<SessionChecker>(::SessionCheckerImpl) }
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
        mockkStatic("io.ktor.server.sessions.SessionDataKt")
        every { call.sessions } returns session
        every { call.attributes } returns Attributes()
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
     * Sets up necessary components for a mocked ApplicationCall to receive a response, and returns the objects that
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
            every { call } returns mockedCall
        }
        every { mockedCall.response } returns resp
        return MockedResponse(respSlot, respPipeline, resp)
    }

    @Test
    fun `Test user verification wrong session`() = test {
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
        assertFalse(subject.verifyUser(context))
        val captured = mr.slot.captured
        assertTrue(captured is ApiErrorResponse)
        assertEquals(300, captured.data!!.code)
        coVerify {
            session.clear("nnname")
            mr.response.status(HttpStatusCode.Unauthorized)
            mr.pipeline.execute(call, any())
        }
    }

    @Test
    fun `Test user verification user does not exist`() = test {
        val (call, session) = setupMockedSession(ConnectedSession("userid", "username", null))
        with(session) {
            every { clear("nnname") } just runs
        }
        val mr = mockResponse(call)
        val context = mockk<PipelineContext<Unit, ApplicationCall>> {
            every { context } returns call
            every { finish() } just runs
        }
        putMock<DatabaseFacade> {
            coEvery { getUser("userid") } returns null
        }

        assertFalse(subject.verifyUser(context))
        val captured = mr.slot.captured
        assertTrue(captured is ApiErrorResponse)
        assertEquals(300, captured.data!!.code)
        coVerify {
            session.clear("nnname")
            mr.response.status(HttpStatusCode.Unauthorized)
            mr.pipeline.execute(call, any())
            context.finish()
        }
    }

    @Test
    fun `Test user verification user exists`() = test {
        val (call, _) = setupMockedSession(ConnectedSession("userid", "username", null))
        val attr = mockk<Attributes> {
            every { put(any(), any()) } just runs
        }
        every { call.attributes } returns attr
        val context = mockk<PipelineContext<Unit, ApplicationCall>> {
            every { context } returns call
        }
        val u = mockk<User>()
        putMock<DatabaseFacade> {
            coEvery { getUser("userid") } returns u
        }
        assertTrue(subject.verifyUser(context))
        verify { attr.put(match { it.name.contains("User") }, u) }
    }

    @OptIn(UsesTrueIdentity::class)
    @Test
    fun `Test admin, not an admin`() = test {
        val (call, _) = setupMockedSession(ConnectedSession("userid", "username", null))
        val (_, u) = mockUserAttributes("userid", call)
        val mr = mockResponse(call)
        val context = mockk<PipelineContext<Unit, ApplicationCall>> {
            every { context } returns call
            every { finish() } just runs
        }
        putMock<PermissionChecks> {
            coEvery { canPerformAdminActions(u) } returns AdminStatus.NotAdmin
        }
        runBlocking {
            assertFalse(subject.verifyAdmin(context))
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
    fun `Test admin, admin but not identifiable`() = test {
        val (call, _) = setupMockedSession(ConnectedSession("adminid", "username", null))
        val (_, u) = mockUserAttributes("adminid", call)
        val mr = mockResponse(call)
        val context = mockk<PipelineContext<Unit, ApplicationCall>> {
            every { context } returns call
            every { finish() } just runs
        }
        putMock<PermissionChecks> {
            coEvery { canPerformAdminActions(u) } returns AdminStatus.AdminNotIdentifiable
        }
        runBlocking {
            assertFalse(subject.verifyAdmin(context))
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
    fun `Test admin when correct`() = test {
        val (call, _) = setupMockedSession(ConnectedSession("adminid", "username", null))
        val (attr, u) = mockUserAttributes("adminid", call)
        val context = mockk<PipelineContext<Unit, ApplicationCall>> {
            every { context } returns call
        }
        putMock<PermissionChecks> {
            coEvery { canPerformAdminActions(u) } returns AdminStatus.Admin
        }
        runBlocking {
            assertTrue(subject.verifyAdmin(context))
        }
        verify { attr.put(match { it.name.contains("Admin") }, u) }
    }

    private fun mockUserAttributes(discordId: String, mockedCall: ApplicationCall): Pair<Attributes, User> {
        val u = mockk<User> {
            every { this@mockk.discordId } returns discordId
        }
        val attributes = mockk<Attributes> {
            every { put(any(), any()) } just runs
            every { getOrNull(userObjAttribute) } returns u
        }
        every { mockedCall.attributes } returns attributes
        return attributes to u
    }
}
