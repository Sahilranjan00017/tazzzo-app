package com.tazzzo.app.data.cart

import com.tazzzo.app.data.catalog.StockState
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException

/*
 * The REAL server cart (`/v1/customer/cart`). It exists only on the backend: nothing here is
 * persisted, mirrored or independently mutated by the app. It is purchase INTENT — it reserves no
 * stock, freezes no price and guarantees no serviceability.
 *
 * Identity is `skuId`. The cart exposes no `productId`, so nothing here (or in the UI) may assume
 * `skuId == productId`.
 */

/** The backend's closed set of per-line issue codes. Each one makes the line not buyable. */
enum class KnownIssue(val wire: String) {
    PRODUCT_UNAVAILABLE("PRODUCT_UNAVAILABLE"),
    PRICE_UNAVAILABLE("PRICE_UNAVAILABLE"),
    LOCATION_REQUIRED("LOCATION_REQUIRED"),
    UNSERVICEABLE("UNSERVICEABLE"),
    OUT_OF_STOCK("OUT_OF_STOCK"),
    STOCK_UNKNOWN("STOCK_UNKNOWN"),
    INSUFFICIENT_STOCK("INSUFFICIENT_STOCK"),
    ENRICHMENT_UNAVAILABLE("ENRICHMENT_UNAVAILABLE");

    companion object { fun fromWire(raw: String): KnownIssue? = entries.firstOrNull { it.wire == raw } }
}

/** A line issue as the server sent it. A code this build does not know is preserved and treated as blocking. */
sealed interface LineIssue {
    data class Known(val code: KnownIssue) : LineIssue
    data class Unrecognized(val raw: String) : LineIssue

    companion object {
        fun of(raw: String): LineIssue = KnownIssue.fromWire(raw)?.let { Known(it) } ?: Unrecognized(raw)
    }
}

data class CartItem(
    val skuId: String,
    val quantity: Int,
    val addedAt: String?,
    val updatedAt: String?,
    /** Null when the product is unavailable. */
    val title: String?,
    /** A raw brand CODE, not a display name — never shown as a brand. */
    val brandCode: String?,
    val imageUrl: String?,
    val unitPrice: Money?,
    val mrp: Money?,
    val lineTotal: Money?,
    val stockState: StockState,
    /** Effective purchasable quantity from inventory; 0 when unknown. NOT the cart's own per-line cap. */
    val maxOrderQuantity: Int,
    /** Null = unknown (no location). */
    val serviceable: Boolean?,
    /** The server's decision. Never overridden, never derived from stock. */
    val buyable: Boolean,
    val issues: List<LineIssue>
) {
    /** True when this line cannot be bought as it stands (any issue, known or not, or the server says not buyable). */
    val isBlocked: Boolean get() = issues.isNotEmpty() || !buyable

    override fun toString(): String = "CartItem(***)"
}

data class ServerCart(
    /** The latest version the server returned. 0 means no cart. Never computed locally. */
    val version: Long,
    val items: List<CartItem>,
    /** Total quantity (the badge). */
    val itemCount: Int,
    val distinctItemCount: Int,
    /** Sum of CURRENT line totals. Not a payable total: no delivery, tax, offers. */
    val subtotal: Money,
    val expiresAt: String?
) {
    fun item(skuId: String): CartItem? = items.firstOrNull { it.skuId == skuId }
    fun quantityOf(skuId: String): Int = item(skuId)?.quantity ?: 0
    val isEmpty: Boolean get() = items.isEmpty()

    override fun toString(): String = "ServerCart(${items.size} lines)"

    companion object {
        val EMPTY = ServerCart(0, emptyList(), 0, 0, Money.ZERO, null)
    }
}

/** Everything that can go wrong talking to the cart API, as data. Never carries server text. */
sealed interface CartFailure {
    data object Unauthenticated : CartFailure
    /** 400 — the app only ever sends integer quantities >= 1, so this is "the server did not accept that quantity". */
    data object InvalidRequest : CartFailure
    data object NotFound : CartFailure
    /** 409 `CART_ITEM_LIMIT_REACHED`. */
    data object ItemLimitReached : CartFailure
    /** 412: the cart changed since it was read. */
    data object PreconditionFailed : CartFailure
    /** 428 / 415: a client bug. Never retried. */
    data object ClientBug : CartFailure
    data object Unavailable : CartFailure
    data object Server : CartFailure
    data object Network : CartFailure
    data object Timeout : CartFailure
    data object Unknown : CartFailure

    val isRetryable: Boolean get() = this is Network || this is Timeout || this is Unavailable || this is Server

    /** The request may nevertheless have been applied: reconcile by reading the cart, never resend. */
    val isAmbiguous: Boolean get() = this is Network || this is Timeout || this is Server || this is Unknown
}

fun Throwable.toCartFailure(): CartFailure {
    val api = (this as? ApiException)?.error ?: return CartFailure.Unknown
    return when (api) {
        ApiError.Network -> CartFailure.Network
        ApiError.Timeout -> CartFailure.Timeout
        is ApiError.Decoding -> CartFailure.Unknown
        is ApiError.Http -> when {
            api.status == 401 -> CartFailure.Unauthenticated
            api.status == 412 -> CartFailure.PreconditionFailed
            api.status == 428 || api.status == 415 -> CartFailure.ClientBug
            api.status == 409 || api.code == "CART_ITEM_LIMIT_REACHED" -> CartFailure.ItemLimitReached
            api.status == 404 -> CartFailure.NotFound
            api.status == 400 -> CartFailure.InvalidRequest
            api.status == 503 -> CartFailure.Unavailable
            api.status in 500..599 -> CartFailure.Server
            else -> CartFailure.Unknown
        }
    }
}
