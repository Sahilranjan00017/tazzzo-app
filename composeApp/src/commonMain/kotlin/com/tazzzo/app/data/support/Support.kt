package com.tazzzo.app.data.support

import com.tazzzo.app.data.catalog.AppendState
import com.tazzzo.app.data.catalog.Page
import com.tazzzo.app.data.catalog.PagedLoader
import com.tazzzo.app.data.catalog.PagedState
import com.tazzzo.app.data.checkout.Iso8601
import com.tazzzo.app.data.order.RemoteOrderDataSource
import com.tazzzo.app.data.remote.ApiClient
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import com.tazzzo.app.data.remote.ApiRequest
import com.tazzzo.app.data.remote.execute
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * `/v1/customer/support/cases` (backend support module): the signed-in customer's OWN cases. Create = {category, subject,
 * message[, orderId]} (nothing else is accepted); list is cursor-paged (page_size, cursor); a case has a message thread; a reply
 * is {message}. Limits are the backend's: subject 1..120, message 1..2000 characters of plain text (line breaks allowed in a
 * message, no other control characters), at most 5 open cases per customer, 100 messages per case, a CLOSED case is final.
 * Text is shown as plain text only. Bodies are never logged.
 */

// ---- wire ---------------------------------------------------------------------------------------------------------------

@Serializable internal data class SupportMessageDto(val id: Int = 0, val author: String? = null, val text: String? = null, val at: String? = null)
@Serializable internal data class SupportCaseDto(
    val caseId: String, val category: String? = null, val orderId: String? = null, val subject: String? = null, val status: String? = null,
    val messages: List<SupportMessageDto> = emptyList(), val createdAt: String? = null, val updatedAt: String? = null, val requestId: String? = null
)
@Serializable internal data class SupportSummaryDto(
    val caseId: String, val category: String? = null, val subject: String? = null, val status: String? = null, val messageCount: Int = 0, val updatedAt: String? = null
)
@Serializable internal data class SupportPageDto(val items: List<SupportSummaryDto> = emptyList(), val nextCursor: String? = null, val requestId: String? = null)

// ---- domain -------------------------------------------------------------------------------------------------------------

/** The backend's closed category set, with customer labels. */
enum class SupportCategory(val label: String) {
    ORDER_ISSUE("An order"), DELIVERY("Delivery"), PRODUCT("A product"), ACCOUNT("My account"), OTHER("Something else");
    companion object { fun of(raw: String?) = entries.firstOrNull { it.name == raw } ?: OTHER }
}

enum class SupportStatus(val label: String) {
    OPEN("Open"), IN_PROGRESS("In progress"), RESOLVED("Resolved"), CLOSED("Closed"), UNRECOGNIZED("Open");
    /** A CLOSED case is final: no reply box. A RESOLVED one accepts a reply (which reopens it). */
    val acceptsReplies: Boolean get() = this != CLOSED
    companion object { fun of(raw: String?) = entries.firstOrNull { it.name == raw && it != UNRECOGNIZED } ?: UNRECOGNIZED }
}

enum class MessageAuthor { CUSTOMER, STAFF }

data class SupportMessage(val id: Int, val author: MessageAuthor, val text: String, val atMillis: Long?)

data class SupportCase(
    val caseId: String, val category: SupportCategory, val orderId: String?, val subject: String, val status: SupportStatus,
    val messages: List<SupportMessage>, val updatedAtMillis: Long?
) {
    override fun toString(): String = "SupportCase(${messages.size} messages)"
}

data class SupportCaseSummary(val caseId: String, val category: SupportCategory, val subject: String, val status: SupportStatus, val messageCount: Int, val updatedAtMillis: Long?) {
    override fun toString(): String = "SupportCaseSummary(***)"
}

internal val CASE_ID = Regex("^SUP_[A-Za-z0-9_-]{20,40}$")

private fun malformed(): Nothing = throw ApiException(ApiError.Decoding())

internal fun SupportCaseDto.toDomain(): SupportCase {
    if (!CASE_ID.matches(caseId)) malformed()
    return SupportCase(
        caseId, SupportCategory.of(category), orderId?.takeIf { RemoteOrderDataSource.isValidOrderId(it) }, subject?.takeIf { it.isNotBlank() } ?: "Support request",
        SupportStatus.of(status),
        messages.mapNotNull { m -> m.text?.takeIf { it.isNotBlank() }?.let { SupportMessage(m.id, if (m.author == "STAFF") MessageAuthor.STAFF else MessageAuthor.CUSTOMER, it, m.at?.let(Iso8601::parseMillis)) } },
        updatedAt?.let(Iso8601::parseMillis)
    )
}

internal fun SupportSummaryDto.toDomain(): SupportCaseSummary {
    if (!CASE_ID.matches(caseId)) malformed()
    return SupportCaseSummary(caseId, SupportCategory.of(category), subject?.takeIf { it.isNotBlank() } ?: "Support request", SupportStatus.of(status), messageCount.coerceAtLeast(0), updatedAt?.let(Iso8601::parseMillis))
}

/** The backend's text rules, applied before sending so a 400 is never the first feedback. */
object SupportRules {
    const val MAX_SUBJECT = 120
    const val MAX_MESSAGE = 2000

    /** Line endings normalised to \n (a message only), tabs to spaces, any other control character removed, then trimmed. */
    fun cleanMessage(raw: String): String = raw.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ')
        .filter { it == '\n' || (it >= ' ' && it != '\u007F') }.trim()

    /** A subject is one line. */
    fun cleanSubject(raw: String): String = cleanMessage(raw).replace('\n', ' ').trim()

    enum class Problem { SUBJECT_EMPTY, SUBJECT_TOO_LONG, MESSAGE_EMPTY, MESSAGE_TOO_LONG }

    fun checkSubject(raw: String): Problem? = cleanSubject(raw).let { if (it.isEmpty()) Problem.SUBJECT_EMPTY else if (it.length > MAX_SUBJECT) Problem.SUBJECT_TOO_LONG else null }
    fun checkMessage(raw: String): Problem? = cleanMessage(raw).let { if (it.isEmpty()) Problem.MESSAGE_EMPTY else if (it.length > MAX_MESSAGE) Problem.MESSAGE_TOO_LONG else null }
}

/** Why a support write failed, as data with app-written copy. Never server text. */
enum class SupportFailure(val message: String) {
    TOO_MANY_OPEN("You already have 5 open requests. We'll reply to those first."),
    MESSAGE_LIMIT("This request has reached its message limit. Please start a new one."),
    CLOSED("This request is closed. Start a new one if you still need help."),
    ORDER_NOT_FOUND("We couldn't find that order on your account."),
    INVALID("Please check your message and try again."),
    SIGNED_OUT("Log in to contact us."),
    NETWORK("No internet connection. Please try again."),
    UNAVAILABLE("We couldn't send this right now. Please try again in a moment.")
}

fun Throwable.toSupportFailure(): SupportFailure {
    val e = (this as? ApiException)?.error ?: return SupportFailure.UNAVAILABLE
    return when (e) {
        ApiError.Network, ApiError.Timeout -> SupportFailure.NETWORK
        is ApiError.Decoding -> SupportFailure.UNAVAILABLE
        is ApiError.Http -> when {
            e.status == 401 -> SupportFailure.SIGNED_OUT
            e.code == "TOO_MANY_OPEN" -> SupportFailure.TOO_MANY_OPEN
            e.code == "MESSAGE_LIMIT" -> SupportFailure.MESSAGE_LIMIT
            e.code == "STATE_CONFLICT" -> SupportFailure.CLOSED
            e.status == 404 -> SupportFailure.ORDER_NOT_FOUND
            e.status == 400 -> SupportFailure.INVALID
            else -> SupportFailure.UNAVAILABLE
        }
    }
}

interface SupportSource {
    suspend fun list(cursor: String?): Page<SupportCaseSummary>
    suspend fun get(caseId: String): SupportCase
    suspend fun open(category: SupportCategory, subject: String, message: String, orderId: String?): SupportCase
    suspend fun reply(caseId: String, message: String): SupportCase
}

class RemoteSupportDataSource(private val api: ApiClient) : SupportSource {
    private fun req(method: HttpMethod, path: String, body: JsonObject? = null, query: Map<String, String?> = emptyMap()) =
        ApiRequest(method = method, path = path, query = query, body = body, authenticated = true)

    override suspend fun list(cursor: String?): Page<SupportCaseSummary> {
        require(cursor == null || (cursor.isNotEmpty() && cursor.length <= MAX_CURSOR)) { "invalid cursor" }
        val p = api.execute<SupportPageDto>(req(HttpMethod.Get, BASE, query = linkedMapOf("page_size" to PAGE_SIZE.toString(), "cursor" to cursor))).body
        val next = p.nextCursor?.takeIf { it.isNotEmpty() && it.length <= MAX_CURSOR }
        return Page(p.items.map { it.toDomain() }, next, next != null)
    }

    override suspend fun get(caseId: String): SupportCase {
        require(CASE_ID.matches(caseId)) { "invalid case id" }
        return api.execute<SupportCaseDto>(req(HttpMethod.Get, "$BASE/$caseId")).body.toDomain()
    }

    override suspend fun open(category: SupportCategory, subject: String, message: String, orderId: String?): SupportCase {
        val s = SupportRules.cleanSubject(subject); val m = SupportRules.cleanMessage(message)
        require(SupportRules.checkSubject(s) == null && SupportRules.checkMessage(m) == null) { "invalid support request" }
        require(orderId == null || RemoteOrderDataSource.isValidOrderId(orderId)) { "invalid order id" }
        val body = buildMap {
            put("category", JsonPrimitive(category.name)); put("subject", JsonPrimitive(s)); put("message", JsonPrimitive(m))
            if (orderId != null) put("orderId", JsonPrimitive(orderId))
        }
        return api.execute<SupportCaseDto>(req(HttpMethod.Post, BASE, JsonObject(body))).body.toDomain()
    }

    override suspend fun reply(caseId: String, message: String): SupportCase {
        require(CASE_ID.matches(caseId)) { "invalid case id" }
        val m = SupportRules.cleanMessage(message)
        require(SupportRules.checkMessage(m) == null) { "invalid message" }
        return api.execute<SupportCaseDto>(req(HttpMethod.Post, "$BASE/$caseId/messages", JsonObject(mapOf("message" to JsonPrimitive(m))))).body.toDomain()
    }

    companion object {
        const val BASE = "/v1/customer/support/cases"
        const val PAGE_SIZE = 20
        const val MAX_CURSOR = 128
        fun isValidCaseId(id: String) = CASE_ID.matches(id)
    }
}

/** The outcome of a create/reply, for the screen. */
sealed interface SupportWrite {
    data class Done(val case: SupportCase) : SupportWrite
    data class Failed(val failure: SupportFailure) : SupportWrite
}

/**
 * The customer's support cases: the list (paged, keyed by a session epoch like order history) and the write helpers.
 * List calls go through [scope]; [submit] and [reply] are suspend calls the screen awaits.
 */
class SupportStore(
    private val scope: CoroutineScope,
    private val source: SupportSource,
    private val isAuthenticated: () -> Boolean
) {
    private val pager = PagedLoader<Int, SupportCaseSummary>(scope, { it.caseId }) { _, cursor -> source.list(cursor) }
    val cases: StateFlow<PagedState<SupportCaseSummary>> = pager.state
    private var epoch = 0

    fun openList() = scope.launch { if (isAuthenticated()) pager.setKey(epoch) else pager.reset() }
    fun refreshList() = scope.launch { if (isAuthenticated()) pager.refresh() else pager.reset() }
    fun loadMore() = scope.launch {
        val s = pager.state.value as? PagedState.Content ?: return@launch
        if (s.append is AppendState.Failed) pager.retryAppend() else pager.loadMore()
    }

    /** Session ended or changed: forget the list. */
    fun signOut() = scope.launch { epoch++; pager.reset() }

    suspend fun load(caseId: String): SupportCase? =
        try { source.get(caseId) } catch (e: CancellationException) { throw e } catch (_: Throwable) { null }

    suspend fun submit(category: SupportCategory, subject: String, message: String, orderId: String?): SupportWrite =
        write { source.open(category, subject, message, orderId) }

    suspend fun reply(caseId: String, message: String): SupportWrite = write { source.reply(caseId, message) }

    private suspend fun write(block: suspend () -> SupportCase): SupportWrite {
        if (!isAuthenticated()) return SupportWrite.Failed(SupportFailure.SIGNED_OUT)
        return try {
            val c = block()
            scope.launch { epoch++; pager.reset() }       // the list reloads (newest first) the next time it is shown
            SupportWrite.Done(c)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            SupportWrite.Failed(SupportFailure.INVALID)
        } catch (e: Throwable) {
            SupportWrite.Failed(e.toSupportFailure())
        }
    }
}
