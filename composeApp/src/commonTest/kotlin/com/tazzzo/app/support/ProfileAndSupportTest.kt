package com.tazzzo.app.support

import com.tazzzo.app.address.authedApi
import com.tazzzo.app.auth.JSON_HEADERS
import com.tazzzo.app.auth.bodyText
import com.tazzzo.app.checkout.hx
import com.tazzzo.app.data.account.CustomerProfile
import com.tazzzo.app.data.account.DisplayNameRules
import com.tazzzo.app.data.account.NameSave
import com.tazzzo.app.data.account.ProfileSource
import com.tazzzo.app.data.account.ProfileState
import com.tazzzo.app.data.account.ProfileStore
import com.tazzzo.app.data.account.RemoteProfileDataSource
import com.tazzzo.app.data.catalog.Page
import com.tazzzo.app.data.catalog.PagedState
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import com.tazzzo.app.data.support.MessageAuthor
import com.tazzzo.app.data.support.RemoteSupportDataSource
import com.tazzzo.app.data.support.SupportCase
import com.tazzzo.app.data.support.SupportCaseSummary
import com.tazzzo.app.data.support.SupportCategory
import com.tazzzo.app.data.support.SupportFailure
import com.tazzzo.app.data.support.SupportRules
import com.tazzzo.app.data.support.SupportSource
import com.tazzzo.app.data.support.SupportStatus
import com.tazzzo.app.data.support.SupportStore
import com.tazzzo.app.data.support.SupportWrite
import com.tazzzo.app.data.support.toSupportFailure
import com.tazzzo.app.ui.support.RequestsSection
import com.tazzzo.app.ui.support.messageViews
import com.tazzzo.app.ui.support.orderSubject
import com.tazzzo.app.ui.support.requestsSection
import com.tazzzo.app.ui.support.rowView
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `GET/PATCH /v1/customer/profile` and `/v1/customer/support/cases` against the running backend's DTOs
 * (`CustomerProfileResponseDto{customerId,displayName,email,version}`, `SupportDtos.CustomerCase/CustomerSummary/CustomerPage`).
 *
 * Mutation notes: sending any create field besides category/subject/message/orderId fails [aNewRequestSendsExactlyTheAllowedFields];
 * dropping If-Match fails [patchSendsOnlyTheDisplayNameWithIfMatch]; a 412 not re-reading fails [aStaleSaveReReadsTheProfile];
 * sending a carriage return/tab fails [textIsCleanedToTheBackendsPlainTextRule].
 */
class ProfileAndSupportTest {
    private val profileJson = """{"customerId":"CUS_123","displayName":"Asha","email":null,"version":3,"requestId":"r"}"""

    // ---- profile -----------------------------------------------------------------------------------------------------------

    @Test fun theProfileMapsWithoutAnyIdOrPhone() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val p = RemoteProfileDataSource(authedApi { seen += it; respond(profileJson, HttpStatusCode.OK, JSON_HEADERS) }).get()
        assertEquals("Asha", p.displayName); assertNull(p.email); assertEquals(3, p.version)
        assertFalse("CUS_123" in p.toString())
        assertEquals("/v1/customer/profile", seen.single().url.encodedPath); assertEquals("Bearer acc1", seen.single().headers[HttpHeaders.Authorization])
    }

    @Test fun patchSendsOnlyTheDisplayNameWithIfMatch() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val ds = RemoteProfileDataSource(authedApi { seen += it; respond(profileJson, HttpStatusCode.OK, JSON_HEADERS) })
        ds.setDisplayName("Asha Rao", 3)
        ds.setDisplayName(null, 4)
        assertEquals(HttpMethod.Patch, seen[0].method); assertEquals("\"profile-3\"", seen[0].headers[HttpHeaders.IfMatch])
        val b0 = Json.parseToJsonElement(seen[0].bodyText()).jsonObject
        assertEquals(setOf("displayName"), b0.keys); assertEquals("Asha Rao", b0["displayName"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, Json.parseToJsonElement(seen[1].bodyText()).jsonObject["displayName"])   // empty name clears it
        assertFailsWith<IllegalArgumentException> { ds.setDisplayName("x".repeat(81), 3) }
        assertEquals(2, seen.size)
    }

    @Test fun displayNameRuleMatchesTheBackend() {
        assertEquals(DisplayNameRules.Check.Ok("José"), DisplayNameRules.check("  José "))
        assertEquals(DisplayNameRules.Check.Ok(null), DisplayNameRules.check("   "))
        assertEquals(DisplayNameRules.Check.TooLong, DisplayNameRules.check("😀".repeat(81)))
        assertIs<DisplayNameRules.Check.Ok>(DisplayNameRules.check("😀".repeat(80)))                 // code points, not UTF-16 units
        assertEquals(DisplayNameRules.Check.InvalidCharacters, DisplayNameRules.check("a\tb"))
    }

    private class FakeProfile : ProfileSource {
        var profile = CustomerProfile("Asha", null, 3)
        var failNext: Throwable? = null
        var gets = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun get(): CustomerProfile { gets++; gate?.await(); return profile }
        override suspend fun setDisplayName(name: String?, version: Long): CustomerProfile {
            failNext?.let { failNext = null; throw it }
            profile = CustomerProfile(name, null, version + 1); return profile
        }
    }

    private fun TestScope.profileStore(src: FakeProfile, authed: Boolean = true) = ProfileStore(backgroundScope, src) { authed }

    @Test fun theProfileLoadsOnceAndSavesTheName() = runTest {
        val src = FakeProfile(); val s = profileStore(src)
        s.ensureLoaded(); runCurrent(); s.ensureLoaded(); runCurrent()
        assertEquals(1, src.gets); assertEquals("Asha", (s.state.value as ProfileState.Loaded).profile.displayName)
        s.saveDisplayName(" Asha Rao "); runCurrent()
        assertEquals(NameSave.Saved, s.save.value); assertEquals("Asha Rao", (s.state.value as ProfileState.Loaded).profile.displayName)
    }

    @Test fun aStaleSaveReReadsTheProfile() = runTest {
        val src = FakeProfile(); val s = profileStore(src)
        s.ensureLoaded(); runCurrent()
        src.failNext = ApiException(ApiError.Http(412, "PRECONDITION_FAILED"))
        s.saveDisplayName("New"); runCurrent()
        assertEquals(NameSave.Stale, s.save.value); assertEquals(2, src.gets)
    }

    @Test fun signedOutLoadsNothingAndSignOutDiscardsAnInFlightRead() = runTest {
        assertEquals(ProfileState.SignedOut, profileStore(FakeProfile(), authed = false).also { it.ensureLoaded(); runCurrent() }.state.value)
        val src = FakeProfile(); val gate = CompletableDeferred<Unit>(); src.gate = gate
        val s = profileStore(src)
        s.ensureLoaded(); runCurrent()
        s.signOut(); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals(ProfileState.SignedOut, s.state.value)
    }

    // ---- support data source ---------------------------------------------------------------------------------------------

    private val caseJson = """{"caseId":"SUP_abcdefghijklmnopqrstu","category":"ORDER_ISSUE","orderId":"ORD_abc123","subject":"Help with order ORD_abc123","status":"OPEN",
        "messages":[{"id":1,"author":"CUSTOMER","text":"Missing item","at":"2026-10-02T09:00:00Z"},{"id":2,"author":"STAFF","text":"Sorry! We're on it.","at":"2026-10-02T09:30:00Z"}],
        "createdAt":"2026-10-02T09:00:00Z","updatedAt":"2026-10-02T09:30:00Z","requestId":"r"}"""

    private fun supportDs(seen: MutableList<HttpRequestData>, status: HttpStatusCode = HttpStatusCode.Created, body: String = caseJson) =
        RemoteSupportDataSource(authedApi { seen += it; respond(body, status, JSON_HEADERS) })

    @Test fun aNewRequestSendsExactlyTheAllowedFields() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val c = supportDs(seen).open(SupportCategory.ORDER_ISSUE, "Help with order ORD_abc123", "Missing item", "ORD_abc123")
        val r = seen.single()
        assertEquals(HttpMethod.Post, r.method); assertEquals("/v1/customer/support/cases", r.url.encodedPath)
        val body = Json.parseToJsonElement(r.bodyText()).jsonObject
        assertEquals(setOf("category", "subject", "message", "orderId"), body.keys)
        assertEquals("ORDER_ISSUE", body["category"]!!.jsonPrimitive.content)
        supportDs(seen).open(SupportCategory.OTHER, "Hi", "Hello", null)
        assertEquals(setOf("category", "subject", "message"), Json.parseToJsonElement(seen[1].bodyText()).jsonObject.keys)
        assertEquals(SupportStatus.OPEN, c.status); assertEquals("ORD_abc123", c.orderId)
        assertEquals(listOf(MessageAuthor.CUSTOMER, MessageAuthor.STAFF), c.messages.map { it.author })
    }

    @Test fun nothingInvalidIsEverSent() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val ds = supportDs(seen)
        assertFailsWith<IllegalArgumentException> { ds.open(SupportCategory.OTHER, "  ", "msg", null) }
        assertFailsWith<IllegalArgumentException> { ds.open(SupportCategory.OTHER, "s".repeat(121), "msg", null) }
        assertFailsWith<IllegalArgumentException> { ds.open(SupportCategory.OTHER, "s", "m".repeat(2001), null) }
        assertFailsWith<IllegalArgumentException> { ds.open(SupportCategory.OTHER, "s", "m", "../ORD") }
        assertFailsWith<IllegalArgumentException> { ds.get("SUP_short") }
        assertFailsWith<IllegalArgumentException> { ds.reply("../x", "hi") }
        assertTrue(seen.isEmpty())
    }

    @Test fun textIsCleanedToTheBackendsPlainTextRule() = runTest {
        assertEquals("line one\nline two  tab", SupportRules.cleanMessage("  line one\r\nline two\t\ttab\u0007 "))
        assertEquals("a b", SupportRules.cleanSubject("a\nb"))
        val seen = mutableListOf<HttpRequestData>()
        supportDs(seen, HttpStatusCode.OK).reply("SUP_abcdefghijklmnopqrstu", "Thanks\r\nstill missing")
        assertEquals("/v1/customer/support/cases/SUP_abcdefghijklmnopqrstu/messages", seen.single().url.encodedPath)
        assertEquals("Thanks\nstill missing", Json.parseToJsonElement(seen.single().bodyText()).jsonObject["message"]!!.jsonPrimitive.content)
    }

    @Test fun theListSendsOnlyPageSizeAndCursor() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val page = supportDs(seen, HttpStatusCode.OK, """{"items":[{"caseId":"SUP_abcdefghijklmnopqrstu","category":"DELIVERY","subject":"Late","status":"RESOLVED","messageCount":3,"updatedAt":"2026-10-02T09:30:00Z"}],"nextCursor":"djF8","requestId":"r"}""").list("c1")
        assertEquals(setOf("page_size", "cursor"), seen.single().url.parameters.names()); assertEquals("c1", seen.single().url.parameters["cursor"])
        assertTrue(page.hasMore)
        val row = page.items.single().rowView()
        assertEquals("Late", row.subject); assertEquals("Resolved", row.status); assertTrue("3 messages" in row.detail)
    }

    @Test fun backendRefusalsMapToAppCopy() {
        assertEquals(SupportFailure.TOO_MANY_OPEN, hx(409, "TOO_MANY_OPEN").toSupportFailure())
        assertEquals(SupportFailure.MESSAGE_LIMIT, hx(409, "MESSAGE_LIMIT").toSupportFailure())
        assertEquals(SupportFailure.CLOSED, hx(409, "STATE_CONFLICT").toSupportFailure())
        assertEquals(SupportFailure.ORDER_NOT_FOUND, hx(404, "NOT_FOUND").toSupportFailure())
        assertEquals(SupportFailure.SIGNED_OUT, hx(401).toSupportFailure())
        assertEquals(SupportFailure.NETWORK, ApiException(ApiError.Network).toSupportFailure())
        assertEquals(SupportFailure.UNAVAILABLE, hx(503, "SERVICE_UNAVAILABLE").toSupportFailure())
        for (f in SupportFailure.entries) assertFalse("error" in f.message.lowercase() || "409" in f.message, f.name)
    }

    // ---- support store + presentation --------------------------------------------------------------------------------------

    private class FakeSupport : SupportSource {
        val rows = mutableListOf<SupportCaseSummary>()
        var lists = 0
        var failWrite: Throwable? = null
        val created = SupportCase("SUP_abcdefghijklmnopqrstu", SupportCategory.OTHER, null, "Hi", SupportStatus.OPEN, emptyList(), null)
        override suspend fun list(cursor: String?) = run { lists++; Page(rows.toList(), null, false) }
        override suspend fun get(caseId: String) = created
        override suspend fun open(category: SupportCategory, subject: String, message: String, orderId: String?): SupportCase { failWrite?.let { throw it }; return created }
        override suspend fun reply(caseId: String, message: String): SupportCase { failWrite?.let { throw it }; return created }
    }

    @Test fun signedOutWritesNeverReachTheBackend() = runTest {
        val src = FakeSupport(); src.failWrite = IllegalStateException("must not be called")
        val s = SupportStore(backgroundScope, src) { false }
        assertEquals(SupportWrite.Failed(SupportFailure.SIGNED_OUT), s.submit(SupportCategory.OTHER, "Hi", "Hello", null))
        assertEquals(RequestsSection.SignedOut, requestsSection(false, s.cases.value))
    }

    @Test fun aSentRequestMakesTheListReloadAndAFailureIsAppCopy() = runTest {
        val src = FakeSupport(); val s = SupportStore(backgroundScope, src) { true }
        s.openList(); runCurrent()
        assertEquals(RequestsSection.Empty, requestsSection(true, s.cases.value))
        assertIs<SupportWrite.Done>(s.submit(SupportCategory.OTHER, "Hi", "Hello", null)); runCurrent()
        assertEquals(PagedState.Idle, s.cases.value)                                   // the next open reloads, newest first
        src.failWrite = hx(409, "TOO_MANY_OPEN")
        assertEquals(SupportWrite.Failed(SupportFailure.TOO_MANY_OPEN), s.submit(SupportCategory.OTHER, "Hi", "Hello", null))
    }

    @Test fun theThreadShowsWhoWroteEachMessageAsPlainText() = runTest {
        val c = supportDs(mutableListOf(), HttpStatusCode.OK).get("SUP_abcdefghijklmnopqrstu")
        val v = c.messageViews()
        assertEquals(listOf("You", "Tazzzo support"), v.map { it.author }); assertTrue(v[0].fromCustomer); assertFalse(v[1].fromCustomer)
        assertEquals("Help with order ORD_abc123", orderSubject("ORD_abc123"))
        assertTrue(SupportStatus.RESOLVED.acceptsReplies); assertFalse(SupportStatus.CLOSED.acceptsReplies)
    }
}
