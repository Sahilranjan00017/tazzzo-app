package com.tazzzo.app.support

import com.tazzzo.app.auth.BASE
import com.tazzzo.app.auth.JSON_HEADERS
import com.tazzzo.app.catalog.INSTALL_ID
import com.tazzzo.app.catalog.installationId
import com.tazzzo.app.data.content.ContactLinks
import com.tazzzo.app.data.content.FaqCategory
import com.tazzzo.app.data.content.LegalSlug
import com.tazzzo.app.data.content.RemoteContentDataSource
import com.tazzzo.app.data.content.legalDateLabel
import com.tazzzo.app.data.content.legalParagraphs
import com.tazzzo.app.data.remote.ApiClient
import com.tazzzo.app.data.remote.ApiException
import com.tazzzo.app.ui.support.LegalCopy
import com.tazzzo.app.ui.support.effectiveLine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Public help content against the running backend's shapes: `GET /v1/content/faqs` (`Faqs{faqs[{faqId,category,question,answer}]}`),
 * `GET /v1/app-config` (`Config{storeOpen, maintenance{enabled,message}, support{phone,email}, legal{...}}`) and the frozen
 * `GET /v1/content/legal/{slug}` (`{slug,title,body,effectiveDate|null}`; 404 = not published).
 *
 * Mutation notes: opening an unvalidated contact fails [onlyStrictTelAndMailtoValuesBecomeLinks]; treating a legal 404 as a
 * failure fails [aMissingLegalDocumentIsNotPublishedNotAnError]; rendering the body as one block fails [legalBodiesSplitIntoParagraphs].
 */
class HelpContentTest {
    private fun source(seen: MutableList<HttpRequestData> = mutableListOf(), status: HttpStatusCode = HttpStatusCode.OK, body: String) =
        RemoteContentDataSource(ApiClient(baseUrl = BASE, engine = MockEngine { req -> seen += req; respond(body, status, JSON_HEADERS) })) { installationId() }

    @Test fun faqsAreReadAnonymouslyAndGroupedInDisplayOrder() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val groups = source(seen, body = """{"faqs":[
            {"faqId":"F1","category":"PAYMENT","question":"Can I pay by card?","answer":"Cash on delivery only for now."},
            {"faqId":"F2","category":"DELIVERY","question":"Where do you deliver?","answer":"Check your PIN on the Home screen."},
            {"faqId":"F3","category":"WHATEVER","question":"Other?","answer":"Yes."},
            {"faqId":"F4","category":"DELIVERY","question":"  ","answer":"dropped"}],"requestId":"r"}""").faqs()
        val r = seen.single()
        assertEquals("/v1/content/faqs", r.url.encodedPath); assertTrue(r.url.parameters.isEmpty())
        assertNull(r.headers[HttpHeaders.Authorization]); assertEquals(INSTALL_ID, r.headers["X-Tazzzo-Installation-Id"])
        assertEquals(listOf(FaqCategory.DELIVERY, FaqCategory.PAYMENT, FaqCategory.OTHER), groups.map { it.category })
        assertEquals(listOf("F2"), groups[0].faqs.map { it.id })
    }

    @Test fun appConfigKeepsOnlyValidContacts() = runTest {
        val ok = source(body = """{"storeOpen":true,"maintenance":{"enabled":false},"android":{},"ios":{},"support":{"phone":"+918000000000","email":"help@tazzzo.com"},"legal":{"termsUrl":null,"privacyUrl":null,"refundPolicyUrl":null},"requestId":"r"}""").appConfig()
        assertEquals("+918000000000", ok.supportPhone); assertEquals("help@tazzzo.com", ok.supportEmail); assertTrue(ok.hasContact)
        val bad = source(body = """{"storeOpen":false,"maintenance":{"enabled":true,"message":"Back at 6 pm"},"support":{"phone":"080 1234","email":"x@y?subject=hi"},"requestId":"r"}""").appConfig()
        assertNull(bad.supportPhone); assertNull(bad.supportEmail); assertFalse(bad.hasContact)
        assertEquals("Back at 6 pm", bad.maintenanceMessage); assertFalse(bad.storeOpen)
        val none = source(body = """{"storeOpen":true,"requestId":"r"}""").appConfig()
        assertFalse(none.hasContact)
    }

    @Test fun onlyStrictTelAndMailtoValuesBecomeLinks() {
        assertEquals("tel:+918000000000", ContactLinks.telUri("+918000000000"))
        assertEquals("mailto:help@tazzzo.com", ContactLinks.mailtoUri("help@tazzzo.com"))
        for (bad in listOf(null, "", "8000000000", "+91 8000000000", "+0123456789", "+9180000000000000", "tel:+918000000000", "+91800000000;ext=1"))
            assertNull(ContactLinks.telUri(bad), bad.toString())
        for (bad in listOf(null, "", "help", "help@tazzzo", "a b@tazzzo.com", "help@tazzzo.com?subject=x", "a%40b@tazzzo.com", "javascript:alert(1)//@x.com", "\"q\"@tazzzo.com", "help@tazzzo.com\n"))
            assertNull(ContactLinks.mailtoUri(bad), bad.toString())
    }

    @Test fun aPublishedLegalDocumentIsPlainTextParagraphs() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val doc = source(seen, body = """{"slug":"privacy","title":"Privacy Policy","body":"We collect your phone number.\n\nWe never sell it.\r\n\r\n<b>Not HTML</b>","effectiveDate":"2026-10-01","requestId":"r"}""").legal(LegalSlug.PRIVACY)!!
        assertEquals("/v1/content/legal/privacy", seen.single().url.encodedPath); assertTrue(seen.single().url.parameters.isEmpty())
        assertNull(seen.single().headers[HttpHeaders.Authorization])                              // public: works before login
        assertEquals(listOf("We collect your phone number.", "We never sell it.", "<b>Not HTML</b>"), doc.paragraphs)   // shown verbatim as text
        assertEquals("Effective 1 October 2026", doc.effectiveLine())
    }

    @Test fun aMissingLegalDocumentIsNotPublishedNotAnError() = runTest {
        assertNull(source(status = HttpStatusCode.NotFound, body = """{"code":"NOT_FOUND","message":"not found","requestId":"r"}""").legal(LegalSlug.TERMS))
        assertNull(source(body = """{"slug":"terms","title":"Terms","body":"  \n\n ","effectiveDate":null,"requestId":"r"}""").legal(LegalSlug.TERMS))
        assertFailsWith<ApiException> { source(status = HttpStatusCode.ServiceUnavailable, body = """{"code":"SERVICE_UNAVAILABLE","message":"x","requestId":"r","retryable":true}""").legal(LegalSlug.TERMS) }
        assertEquals("This document isn't available yet", LegalCopy.NOT_PUBLISHED_TITLE)
    }

    @Test fun legalBodiesSplitIntoParagraphs() {
        assertEquals(listOf("A\nstill A", "B"), legalParagraphs("A\nstill A\n\n  \nB\n"))
        assertEquals(listOf("one"), legalParagraphs("one"))
        assertNull(legalDateLabel(null)); assertNull(legalDateLabel("2026-13-01")); assertNull(legalDateLabel("01/10/2026"))
        assertEquals("29 February 2028", legalDateLabel("2028-02-29"))
        assertEquals(LegalSlug.TERMS, LegalSlug.of("terms")); assertNull(LegalSlug.of("../admin")); assertNull(LegalSlug.of("TERMS"))
    }
}
