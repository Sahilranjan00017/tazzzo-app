package com.tazzzo.app.data.content

import kotlinx.serialization.Serializable

/*
 * Help, contact and legal content from the public content endpoints (anonymous, installation id, no query beyond the contract):
 *  - `GET /v1/content/faqs`  → the live help-centre FAQ (plain text, no markup), grouped here by category;
 *  - `GET /v1/app-config`    → support phone/email (validated again here: E.164 and a strict email shape), store/maintenance;
 *  - `GET /v1/content/legal/{slug}` (slug `terms` | `privacy`) → the one live document, body = plain-text paragraphs separated
 *    by blank lines; 404 = not published yet.
 * Every string is rendered as TEXT. Nothing here is ever opened as a URL except the tel:/mailto: built by [ContactLinks] from a
 * value that passed its strict check.
 */

// ---- wire ---------------------------------------------------------------------------------------------------------------

@Serializable internal data class FaqDto(val faqId: String? = null, val category: String? = null, val question: String? = null, val answer: String? = null)
@Serializable internal data class FaqsDto(val faqs: List<FaqDto> = emptyList(), val requestId: String? = null)

@Serializable internal data class AppConfigSupportDto(val phone: String? = null, val email: String? = null)
@Serializable internal data class AppConfigMaintenanceDto(val enabled: Boolean = false, val message: String? = null)
@Serializable internal data class AppConfigDto(
    val storeOpen: Boolean = true,
    val maintenance: AppConfigMaintenanceDto? = null,
    val support: AppConfigSupportDto? = null,
    val requestId: String? = null
)

@Serializable internal data class LegalDocumentDto(
    val slug: String? = null, val title: String? = null, val body: String? = null, val effectiveDate: String? = null, val requestId: String? = null
)

// ---- domain -------------------------------------------------------------------------------------------------------------

/** The backend's closed FAQ category set (`ContentBlock.FaqCategory`), in display order; anything else groups under OTHER. */
enum class FaqCategory(val label: String) {
    DELIVERY("Delivery"), PRODUCT("Products"), PAYMENT("Payment"), REFUND("Refunds"), ACCOUNT("Account"), CLUB("Membership"), OTHER("More help");
    companion object { fun of(raw: String?) = entries.firstOrNull { it.name == raw && it != OTHER } ?: OTHER }
}

data class Faq(val id: String, val category: FaqCategory, val question: String, val answer: String)
data class FaqGroup(val category: FaqCategory, val faqs: List<Faq>)

/** Groups in [FaqCategory] order, each keeping the backend's order. An entry without a question or answer is dropped. */
internal fun FaqsDto.toGroups(): List<FaqGroup> {
    val faqs = faqs.mapIndexedNotNull { i, f ->
        val q = f.question?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
        val a = f.answer?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
        Faq(f.faqId?.takeIf { it.isNotBlank() } ?: "faq-$i", FaqCategory.of(f.category), q, a)
    }
    return FaqCategory.entries.mapNotNull { c -> faqs.filter { it.category == c }.takeIf { it.isNotEmpty() }?.let { FaqGroup(c, it) } }
}

/** What the app shows from `/v1/app-config`. A contact that fails its strict check is dropped (shown nowhere). */
data class AppInfo(val supportPhone: String?, val supportEmail: String?, val storeOpen: Boolean, val maintenanceMessage: String?) {
    val hasContact: Boolean get() = supportPhone != null || supportEmail != null
}

internal fun AppConfigDto.toDomain(): AppInfo = AppInfo(
    supportPhone = support?.phone?.trim()?.takeIf { ContactLinks.isE164(it) },
    supportEmail = support?.email?.trim()?.takeIf { ContactLinks.isEmail(it) },
    storeOpen = storeOpen,
    maintenanceMessage = maintenance?.takeIf { it.enabled }?.message?.trim()?.takeIf { it.isNotEmpty() && it.length <= 200 }
)

/**
 * The ONLY external URIs the app opens: `tel:` and `mailto:` built from a value that matches a strict pattern (the backend's own
 * E.164 rule; an email shape without `%`, quotes, spaces or query characters). Anything else returns null and stays plain text.
 */
object ContactLinks {
    private val E164 = Regex("^\\+[1-9][0-9]{7,14}$")
    private val EMAIL = Regex("^[A-Za-z0-9._+-]{1,64}@[A-Za-z0-9-]{1,63}(\\.[A-Za-z0-9-]{1,63})*\\.[A-Za-z]{2,24}$")

    fun isE164(value: String): Boolean = E164.matches(value)
    fun isEmail(value: String): Boolean = value.length <= 254 && EMAIL.matches(value)
    fun telUri(phone: String?): String? = phone?.takeIf { isE164(it) }?.let { "tel:$it" }
    fun mailtoUri(email: String?): String? = email?.takeIf { isEmail(it) }?.let { "mailto:$it" }
}

/** The two legal documents the backend publishes. The slug is a closed set: nothing else is ever put in the path. */
enum class LegalSlug(val path: String, val fallbackTitle: String) {
    TERMS("terms", "Terms of Service"), PRIVACY("privacy", "Privacy Policy");
    companion object { fun of(raw: String?) = entries.firstOrNull { it.path == raw } }
}

/** A published legal document: plain-text paragraphs (never HTML), with its effective date when the backend sends one. */
data class LegalDocument(val slug: LegalSlug, val title: String, val paragraphs: List<String>, val effectiveDate: String?)

private val ISO_DATE = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

/** `2026-10-01` → `1 October 2026`; anything else (or an impossible month/day) → null (not shown). */
fun legalDateLabel(iso: String?): String? {
    val m = iso?.let { ISO_DATE.matchEntire(it) }?.groupValues ?: return null
    val mo = m[2].toInt(); val d = m[3].toInt()
    if (mo !in 1..12 || d !in 1..31) return null
    return "$d ${MONTHS[mo - 1]} ${m[1]}"
}

/** Paragraphs = text separated by blank lines (CRLF tolerated); single line breaks inside a paragraph are kept. */
fun legalParagraphs(body: String): List<String> =
    body.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\\n[ \\t]*\\n")).map { it.trim() }.filter { it.isNotEmpty() }

internal fun LegalDocumentDto.toDomain(expected: LegalSlug): LegalDocument? {
    val paragraphs = body?.let { legalParagraphs(it) }.orEmpty()
    if (paragraphs.isEmpty()) return null                              // an empty document is "not published", never a blank page
    return LegalDocument(expected, title?.trim()?.takeIf { it.isNotEmpty() } ?: expected.fallbackTitle, paragraphs, effectiveDate)
}
