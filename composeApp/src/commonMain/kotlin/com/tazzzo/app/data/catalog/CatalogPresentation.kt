package com.tazzzo.app.data.catalog

/*
 * Pure presentation rules for the REAL catalogue: what a screen may say about a product, a
 * failure, a location. No Compose, no I/O, so every rule is unit-tested.
 *
 * Ground rules (PR-04C):
 *  - never show raw server text, codes, ids or status numbers;
 *  - never invent: no ₹0 for a missing price, no ETA, rating, brand name or count;
 *  - `buyable` is the only purchase-eligibility signal, and with no server cart there is no
 *    enabled purchase action at all.
 */

/** Taxonomy depth, from the backend's id prefix. Mapping: TZS = group, TZC = category, TZG = subcategory. */
enum class NodeLevel {
    SECTION, CATEGORY, SUBCATEGORY, VERTICAL, UNKNOWN;

    companion object {
        fun of(id: String): NodeLevel = when {
            id.startsWith("TZS-") -> SECTION
            id.startsWith("TZC-") -> CATEGORY
            id.startsWith("TZG-") -> SUBCATEGORY
            id.startsWith("TZV-") -> VERTICAL
            else -> UNKNOWN
        }
    }
}

val CatalogNode.level: NodeLevel get() = NodeLevel.of(id)

// ---- failures --------------------------------------------------------------------------------------

/** Customer-facing headline for a failure. Never a code, a status number or server text. */
val CatalogFailure.title: String
    get() = when (this) {
        CatalogFailure.Network -> "No internet connection"
        CatalogFailure.Timeout -> "That took too long"
        is CatalogFailure.RateLimited -> "Too many requests"
        CatalogFailure.Unavailable -> "Temporarily unavailable"
        CatalogFailure.Server -> "Something went wrong at our end"
        CatalogFailure.NotFound -> "Not found"
        CatalogFailure.InvalidRequest, CatalogFailure.InvalidCursor, CatalogFailure.Unknown -> "Something went wrong"
    }

val CatalogFailure.hint: String
    get() = when (this) {
        CatalogFailure.Network -> "Check your connection and try again."
        CatalogFailure.Timeout -> "Your connection looks slow. Try once more."
        is CatalogFailure.RateLimited -> "Please wait a moment and try again."
        CatalogFailure.Unavailable -> "We're busy right now. Please try again shortly."
        CatalogFailure.Server -> "Please try again in a moment."
        CatalogFailure.NotFound -> "This isn't available any more."
        CatalogFailure.InvalidRequest, CatalogFailure.InvalidCursor, CatalogFailure.Unknown -> "Please try again."
    }

/**
 * How long the app waits before offering "try again".
 *  - 429 honours the server's `Retry-After` (bounded; 5 s if it gave none);
 *  - 503 / 5xx back off exponentially (2, 4, 8, 16, then 30 s) by consecutive failure count;
 *  - network / timeout may retry at once;
 *  - everything else is not retried automatically.
 */
object RetryPolicy {
    const val MAX_RATE_LIMIT_WAIT_SECONDS = 120
    const val DEFAULT_RATE_LIMIT_WAIT_SECONDS = 5
    const val MAX_BACKOFF_SECONDS = 30

    /** @param consecutiveFailures 1 for the first failure, 2 for the next in a row, ... */
    fun waitSeconds(failure: CatalogFailure, consecutiveFailures: Int): Int {
        val n = consecutiveFailures.coerceAtLeast(1)
        return when (failure) {
            is CatalogFailure.RateLimited ->
                (failure.retryAfterSeconds ?: DEFAULT_RATE_LIMIT_WAIT_SECONDS.toLong())
                    .coerceIn(1L, MAX_RATE_LIMIT_WAIT_SECONDS.toLong()).toInt()
            CatalogFailure.Unavailable, CatalogFailure.Server ->
                (1 shl n.coerceAtMost(5)).coerceAtMost(MAX_BACKOFF_SECONDS)
            CatalogFailure.Network, CatalogFailure.Timeout -> 0
            else -> 0
        }
    }
}

// ---- product -----------------------------------------------------------------------------------------

enum class StockTone { Available, Scarce, Unavailable, Unknown }

data class StockLabel(val text: String, val tone: StockTone)

/** UNKNOWN is its own thing: it is never worded or toned like OUT_OF_STOCK. */
fun CatalogProduct.stockLabel(): StockLabel = when (stockState) {
    StockState.IN_STOCK -> StockLabel("In stock", StockTone.Available)
    StockState.LOW_STOCK -> StockLabel(lowStockRemaining?.let { "Only $it left" } ?: "Low stock", StockTone.Scarce)
    StockState.OUT_OF_STOCK -> StockLabel("Out of stock", StockTone.Unavailable)
    StockState.UNKNOWN ->
        if (serviceable == false) StockLabel("Not available at your location", StockTone.Unknown)
        else StockLabel("Availability unknown", StockTone.Unknown)
}

/** `₹49.50`, or null when there is no price — never a fake `₹0`. */
fun CatalogProduct.priceLabel(): String? = sellingPrice?.format()

/** The struck-through MRP, only when both prices exist and the MRP really is higher. */
fun CatalogProduct.mrpLabel(): String? {
    val selling = sellingPrice ?: return null
    val list = mrp ?: return null
    return if (list > selling) list.format() else null
}

/** From the server's `discountPercent` only — never recomputed. */
fun CatalogProduct.discountPercentLabel(): String? = discountPercent?.takeIf { it > 0 }?.let { "$it% off" }

/** From the server's `discountAmountPaise` only — never recomputed. */
fun CatalogProduct.savingLabel(): String? = discountAmount?.takeIf { it.isPositive }?.let { "You save ${it.format()}" }

sealed interface PurchaseAction {
    /** The product may be added to the cart of the active catalogue (server cart in REMOTE). */
    data object Enabled : PurchaseAction

    /** A neutral, non-interactive action. [label] is the customer copy. */
    data class Disabled(val reason: Reason, val label: String) : PurchaseAction

    enum class Reason { PriceUnavailable, NotBuyable, CartNotAvailable }
}

/**
 * The only place purchase eligibility is decided.
 *  1. no price -> nothing to buy;
 *  2. `buyable == false` (the server's word) -> unavailable;
 *  3. the catalogue has no cart integration -> disabled even when the server says buyable.
 * Stock state is deliberately not consulted.
 */
fun purchaseAction(product: CatalogProduct, caps: CatalogCapabilities): PurchaseAction = when {
    product.sellingPrice == null -> PurchaseAction.Disabled(PurchaseAction.Reason.PriceUnavailable, "Price unavailable")
    !product.buyable -> PurchaseAction.Disabled(PurchaseAction.Reason.NotBuyable, "Currently unavailable")
    !caps.cartIntegration -> PurchaseAction.Disabled(PurchaseAction.Reason.CartNotAvailable, "Cart isn't available")
    else -> PurchaseAction.Enabled
}

// ---- serviceability ------------------------------------------------------------------------------------

enum class BannerTone { Neutral, Positive, Warning, Error }

data class ServiceabilityBanner(val text: String, val tone: BannerTone, val retryable: Boolean)

/** `20–35 min` only when the server actually sent it. */
fun ServiceabilityResult.etaLabel(): String? {
    val lo = etaMinutesMin
    val hi = etaMinutesMax
    return when {
        lo != null && hi != null && lo != hi -> "$lo–$hi min"
        lo != null && hi != null -> "$lo min"
        lo != null -> "$lo min"
        hi != null -> "$hi min"
        else -> null
    }
}

fun ServiceabilityState.banner(pin: Pincode): ServiceabilityBanner? = when (this) {
    ServiceabilityState.Unknown -> null
    ServiceabilityState.Loading -> ServiceabilityBanner("Checking delivery to ${pin.value}…", BannerTone.Neutral, false)
    is ServiceabilityState.Serviceable ->
        ServiceabilityBanner("Delivering to ${pin.value}" + (result.etaLabel()?.let { " · $it" } ?: ""), BannerTone.Positive, false)
    is ServiceabilityState.NotServiceable ->
        ServiceabilityBanner("We don't deliver to ${pin.value} yet", BannerTone.Warning, false)
    is ServiceabilityState.Failed ->
        ServiceabilityBanner("Couldn't check delivery to ${pin.value}", BannerTone.Error, failure.isRetryable)
}

// ---- attributes ------------------------------------------------------------------------------------------

/**
 * The text of a governed attribute for display, or null when it has no sensible one-line form
 * (nested objects are skipped rather than printed raw). Booleans read "Yes" / "No".
 */
fun ProductAttribute.displayValue(): String? {
    fun primitive(e: kotlinx.serialization.json.JsonElement): String? {
        val p = e as? kotlinx.serialization.json.JsonPrimitive ?: return null
        if (p is kotlinx.serialization.json.JsonNull) return null
        return when {
            !p.isString && p.content == "true" -> "Yes"
            !p.isString && p.content == "false" -> "No"
            else -> p.content.takeIf { it.isNotBlank() }
        }
    }
    val text = when (val v = value) {
        is kotlinx.serialization.json.JsonArray -> v.mapNotNull { primitive(it) }.takeIf { it.isNotEmpty() }?.joinToString(", ")
        is kotlinx.serialization.json.JsonObject -> null
        else -> primitive(v)
    } ?: return null
    return if (unit.isNullOrBlank()) text else "$text $unit"
}
