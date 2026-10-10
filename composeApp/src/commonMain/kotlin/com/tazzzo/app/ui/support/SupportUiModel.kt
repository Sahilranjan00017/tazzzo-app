package com.tazzzo.app.ui.support

import com.tazzzo.app.data.content.LegalDocument
import com.tazzzo.app.data.content.legalDateLabel
import com.tazzzo.app.data.order.OrderTime
import com.tazzzo.app.data.support.MessageAuthor
import com.tazzzo.app.data.support.SupportCase
import com.tazzzo.app.data.support.SupportCaseSummary
import com.tazzzo.app.data.support.SupportRules

/*
 * Presentation for Help & support (FAQs, contact, the customer's requests) and the in-app legal documents. Pure, unit-tested.
 * All server text (FAQ answers, legal paragraphs, messages) is rendered as plain text; nothing here builds a URL except the
 * strict tel:/mailto: of ContactLinks.
 */

object SupportCopy {
    const val NEW_TITLE = "Contact us"
    const val ABOUT_ORDER = "About order"
    const val CATEGORY = "What is it about?"
    const val SUBJECT = "Subject"
    const val MESSAGE = "How can we help?"
    const val SEND = "Send"
    const val SENDING = "Sending…"
    const val LOADING = "Loading…"
    const val SUBJECT_EMPTY = "Add a short subject."
    const val SUBJECT_TOO_LONG = "Keep the subject under ${SupportRules.MAX_SUBJECT} characters."
    const val MESSAGE_EMPTY = "Tell us a little about it."
    const val MESSAGE_TOO_LONG = "Keep the message under ${SupportRules.MAX_MESSAGE} characters."
    const val CASE_TITLE = "Your request"
    const val CASE_FAILED = "We couldn't load this request."
    const val REPLY = "Write a reply"
    const val CLOSED_NOTE = "This request is closed. Start a new one if you still need help."
    const val YOU = "You"
    const val TAZZZO = "Tazzzo support"
    const val SIGNED_OUT_TITLE = "Log in to contact us"
    const val SIGNED_OUT_BODY = "Your requests and our replies are saved to your account."
    const val LOG_IN = "Log in"
}

object LegalCopy {
    /** 404 (not published, or a backend without the endpoint) or an empty body. */
    const val NOT_PUBLISHED_TITLE = "This document isn't available yet"
    const val NOT_PUBLISHED_BODY = "Please check back soon."
    const val FAILED_TITLE = "We couldn't load this document"
    const val EFFECTIVE_PREFIX = "Effective"
}

fun SupportRules.Problem.copy(): String = when (this) {
    SupportRules.Problem.SUBJECT_EMPTY -> SupportCopy.SUBJECT_EMPTY
    SupportRules.Problem.SUBJECT_TOO_LONG -> SupportCopy.SUBJECT_TOO_LONG
    SupportRules.Problem.MESSAGE_EMPTY -> SupportCopy.MESSAGE_EMPTY
    SupportRules.Problem.MESSAGE_TOO_LONG -> SupportCopy.MESSAGE_TOO_LONG
}

/** The live counter under a text field, e.g. `112 / 2000`. */
fun counter(text: String, max: Int): String = "${SupportRules.cleanMessage(text).length} / $max"

data class SupportRowView(val caseId: String, val subject: String, val status: String, val detail: String)

fun SupportCaseSummary.rowView(): SupportRowView = SupportRowView(
    caseId, subject, status.label,
    listOfNotNull(category.label, if (messageCount == 1) "1 message" else "$messageCount messages", updatedAtMillis?.let { "Updated ${OrderTime.label(it)}" }).joinToString(" · ")
)

data class MessageView(val id: Int, val fromCustomer: Boolean, val author: String, val text: String, val time: String?)

fun SupportCase.messageViews(): List<MessageView> = messages.map {
    MessageView(it.id, it.author == MessageAuthor.CUSTOMER, if (it.author == MessageAuthor.CUSTOMER) SupportCopy.YOU else SupportCopy.TAZZZO, it.text, it.atMillis?.let(OrderTime::label))
}

/** What the Legal screen shows. */
sealed interface LegalLoad {
    data object Loading : LegalLoad
    data class Loaded(val document: LegalDocument) : LegalLoad
    data object NotPublished : LegalLoad
    data object Failed : LegalLoad
}

/** The effective-date line, or null when the backend sent none (or an unreadable one). */
fun LegalDocument.effectiveLine(): String? = legalDateLabel(effectiveDate)?.let { "${LegalCopy.EFFECTIVE_PREFIX} $it" }

/** A one-shot load for the Help screen's public sections (FAQs, contact details). */
sealed interface HelpLoad<out T> {
    data object Loading : HelpLoad<Nothing>
    data class Loaded<T>(val value: T) : HelpLoad<T>
    data object Failed : HelpLoad<Nothing>
}

/** What the "My requests" section shows. */
sealed interface RequestsSection {
    data object SignedOut : RequestsSection
    data object Loading : RequestsSection
    data object Failed : RequestsSection
    data object Empty : RequestsSection
    data class Content(val rows: List<SupportRowView>, val hasMore: Boolean, val appendFailed: Boolean, val appending: Boolean) : RequestsSection
}

fun requestsSection(authenticated: Boolean, cases: com.tazzzo.app.data.catalog.PagedState<SupportCaseSummary>): RequestsSection = when {
    !authenticated -> RequestsSection.SignedOut
    else -> when (cases) {
        com.tazzzo.app.data.catalog.PagedState.Idle, com.tazzzo.app.data.catalog.PagedState.LoadingFirst -> RequestsSection.Loading
        is com.tazzzo.app.data.catalog.PagedState.FirstPageFailed -> RequestsSection.Failed
        com.tazzzo.app.data.catalog.PagedState.Empty -> RequestsSection.Empty
        is com.tazzzo.app.data.catalog.PagedState.Content -> RequestsSection.Content(
            cases.items.map { it.rowView() }, cases.hasMore,
            appendFailed = cases.append is com.tazzzo.app.data.catalog.AppendState.Failed,
            appending = cases.append == com.tazzzo.app.data.catalog.AppendState.Loading
        )
    }
}

/** The subject a "Contact us about this order" request starts with (editable). */
fun orderSubject(orderId: String): String = "Help with order $orderId"
