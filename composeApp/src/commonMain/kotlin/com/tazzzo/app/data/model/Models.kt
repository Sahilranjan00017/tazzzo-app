package com.tazzzo.app.data.model

import kotlinx.serialization.Serializable

/*
 * Wire format for the catalogue and order models.
 *
 * Ground rules, all of them load-bearing:
 *  - Money is INTEGER RUPEES everywhere. No floats, no strings, no paise.
 *  - Computed properties (discountPercent, purchasableLimit, lineTotal, saved,
 *    ok) are derived on the client and are NOT part of any payload.
 *  - `Availability` and `CartIssue` are sealed types whose documented JSON is
 *    flat. kotlinx's default polymorphism would emit a "type" discriminator
 *    around a nested object instead, so both have hand-written serializers in
 *    `WireFormat.kt`. Do not replace them with the generated encoding without
 *    changing the contract documents first.
 *
 * The authority for these shapes is docs/BACKEND_INTEGRATION_READINESS.md §3.
 */


@Serializable
data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val tint: Long,             // pastel tile background (ARGB)
    val group: String,          // top-level group shown as section header
    val subcategories: List<Subcategory> = emptyList()
)

@Serializable
data class Subcategory(
    val id: String,
    val name: String,
    val emoji: String
)

/**
 * Stock position for a SKU at the serving dark store.
 *
 * Availability is a DATA concern, not a UI concern: the model must be able to
 * carry what the backend reports so the UI can react. Solving out-of-stock only
 * in the UI layer is how grocery apps end up selling stock they do not have.
 */
@Serializable(with = AvailabilitySerializer::class)
sealed interface Availability {
    data object InStock : Availability
    /** In stock but scarce — drives "Only N left" urgency and caps quantity. */
    data class LowStock(val remaining: Int) : Availability
    data object OutOfStock : Availability
    /** Not sold at the customer's current location. */
    data object NotServiceable : Availability

    val isPurchasable: Boolean get() = this is InStock || this is LowStock
}

@Serializable
data class Product(
    val id: String,
    val name: String,
    val brand: String,
    /**
     * Placeholder glyph shown until real product photography exists.
     *
     * Defaulted to empty ON PURPOSE. It was required with no default, which
     * meant a perfectly valid payload that omitted it would fail to
     * deserialize — and this field is a temporary development placeholder that
     * is meant to disappear once `imageUrl` is populated, so it must not be
     * able to break the catalogue on its way out. The renderer already treats a
     * blank glyph as "no glyph". The server may still always send it.
     */
    val emoji: String = "",
    val unit: String,           // "500 g", "1 L", "6 pcs"
    val price: Int,             // selling price in ₹
    val mrp: Int,               // strike-through price in ₹
    val categoryId: String,
    val subcategoryId: String,
    val rating: Double,
    val ratingCount: Int,
    val etaMinutes: Int = 59,
    val tags: List<String> = emptyList(),   // "Bestseller", "New", "Veg"
    val highlights: List<String> = emptyList(),
    val availability: Availability = Availability.InStock,
    /** Hard ceiling per order, independent of stock (e.g. fair-use limits). */
    val maxOrderQuantity: Int = 10,
    val imageUrl: String? = null,
    /**
     * Local-language name shown beside the English one — "Eerulli" for onion in
     * Bengaluru, "Baale Hannu" for banana.
     *
     * Per-SKU and ultimately per-store, because the language follows the dark
     * store that serves the customer, not the phone's locale. Null where no
     * verified name exists: a transliteration guessed at render time would put
     * a wrong word in a customer's own language, which is worse than showing
     * only English.
     */
    val localName: String? = null,
    /**
     * The catalogue service's vertical id for this SKU — "TZV-000225".
     *
     * Carried on the mock so that swapping mock for service is a data change,
     * not a re-modelling exercise. Nullable because the app's own aisles
     * (fresh produce, dairy, pet) have no vertical in taxonomy v0.9.0: those
     * were a recorded scope exclusion, and a null here marks exactly which
     * SKUs the backend cannot serve today rather than hiding the gap.
     *
     * Store the bare id. The master CSV currently suffixes every id with
     * " (provisional)"; that suffix is branch status, not identity, and keying
     * on it would break the day the branch locks.
     */
    val verticalId: String? = null
) {
    val discountPercent: Int get() = if (mrp > price) ((mrp - price) * 100) / mrp else 0

    /**
     * Price per standard unit — "₹32/kg", "₹58/L", "₹7/pc" — derived from the
     * pack size and nothing else.
     *
     * Null whenever the pack is not a measurable quantity ("1 unit",
     * "180 pages"): inventing a denominator would be a false price claim, and
     * this figure exists precisely so a customer can compare two pack sizes
     * honestly.
     */
    val unitPriceLabel: String? get() = unitPriceLabel(price, unit)

    val isPurchasable: Boolean get() = availability.isPurchasable

    /** The most a customer may add right now, respecting both stock and policy. */
    val purchasableLimit: Int
        get() = when (val a = availability) {
            is Availability.LowStock -> minOf(a.remaining, maxOrderQuantity)
            Availability.InStock -> maxOrderQuantity
            else -> 0
        }
}

@Serializable
data class PromoBanner(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val dark: Boolean           // dark-green banner vs orange banner
)

@Serializable
data class CartLine(val product: Product, val quantity: Int) {
    val lineTotal: Int get() = product.price * quantity
    val lineMrp: Int get() = product.mrp * quantity
}

@Serializable
data class BillSummary(
    val itemTotal: Int,
    val itemMrpTotal: Int,
    val deliveryFee: Int,
    val handlingCharge: Int,
    val coinsEarned: Int,
    val grandTotal: Int,
    /**
     * Rupees off from Tazzzo Club for THIS order. 0 for non-members and
     * ineligible orders. Placed AFTER grandTotal and defaulted so every
     * existing positional BillSummary(...) fixture keeps compiling and every
     * value it constructs keeps meaning what it always meant.
     */
    val clubDiscount: Int = 0,
    /** Rupees off from promotions/coupons on THIS order. 0 when none applied. */
    val promotionDiscount: Int = 0,
    /** Each promotion that applied, with its customer-facing explanation. */
    val appliedPromotions: List<AppliedPromotion> = emptyList(),
    /** Offers the customer could see but did not get, and why. */
    val declinedPromotions: List<DeclinedPromotion> = emptyList(),
    /** One sentence when the engine chose between competing offers. */
    val bestOfferNote: String? = null,
    /** True when a promotion waived the delivery fee. */
    val freeDeliveryByPromotion: Boolean = false,
    /**
     * Delivery fee the customer would have paid and did not — because of the
     * free-delivery threshold, a promotion, a free slot, or a Club benefit.
     * Money genuinely not charged, so it counts toward realised savings; the
     * reason is carried so the bill can say WHY it was free.
     */
    val deliveryFeeWaivedRupees: Int = 0,
    val deliveryFeeReason: String? = null,
    /**
     * A voluntary amount added to this order.
     *
     * Kept out of [realisedSavings] and out of every discount line: a tip is
     * money the customer ADDS, and folding it anywhere near savings would make
     * the bill unreadable. It is added to [grandTotal] and shown on its own row.
     */
    val tip: Int = 0
) {
    /** MRP savings only — a list-price comparison, not money taken off the payable. */
    val saved: Int get() = itemMrpTotal - itemTotal

    /**
     * Money ACTUALLY taken off the payable amount: promotions + Club. This is
     * the honest "You saved ₹X" for the cart. MRP savings are deliberately
     * excluded — blending a strike-through comparison with a real discount is
     * how a "you saved ₹60" is born that the customer cannot find on the bill.
     */
    val realisedSavings: Int get() = promotionDiscount + clubDiscount + deliveryFeeWaivedRupees
}

@Serializable
enum class OrderStatus { PLACED, PACKED, ON_THE_WAY, DELIVERED }

@Serializable
data class Order(
    val id: String,
    val lines: List<CartLine>,
    val bill: BillSummary,
    val status: OrderStatus,
    val placedAtLabel: String,
    val address: String,
    /** How the customer paid. Nullable so historical/mock orders stay valid. */
    val payment: PaymentMethodKind? = null,
    /** The delivery slot the customer chose. Echoed on confirmation, orders and detail. */
    val slot: DeliverySlot? = null,
    val instructionIds: List<String> = emptyList()
)

@Serializable
data class CoinTransaction(
    val id: String,
    val title: String,
    val amount: Int,            // positive = earned, negative = spent
    val dateLabel: String
)

@Serializable
data class UserProfile(
    val name: String,
    val phone: String,
    val isGuest: Boolean,
    val coinBalance: Int,
    val address: String
)

@Serializable
data class FaqItem(val question: String, val answer: String)

/**
 * Derives a per-unit price label from a pack-size string.
 *
 * Understands "1 kg", "500 g", "1 L", "250 ml", "6 pcs" and multipacks such as
 * "4 x 100 g" (which is 400 g, not 100 g — reading only the trailing figure
 * would overstate the value by four times). Returns null for anything it cannot
 * measure rather than guessing.
 */
internal fun unitPriceLabel(price: Int, unit: String): String? {
    val u = unit.lowercase()
    val multi = Regex("""(\d+)\s*[x\u00d7]\s*(\d+(?:\.\d+)?)\s*(kg|g|l|ml)\b""").find(u)
    val mass = multi ?: Regex("""(\d+(?:\.\d+)?)\s*(kg|g|l|ml)\b""").find(u)
    if (mass != null) {
        val g = mass.groupValues
        val qty: Double
        val suffix: String
        if (multi != null) { qty = g[1].toDouble() * g[2].toDouble(); suffix = g[3] }
        else { qty = g[1].toDouble(); suffix = g[2] }
        if (qty <= 0.0) return null
        return when (suffix) {
            "kg" -> "\u20b9${(price / qty).roundToWhole()}/kg"
            "g"  -> if (qty < 20) null else "\u20b9${(price * 1000 / qty).roundToWhole()}/kg"
            "l"  -> "\u20b9${(price / qty).roundToWhole()}/L"
            "ml" -> if (qty < 20) null else "\u20b9${(price * 1000 / qty).roundToWhole()}/L"
            else -> null
        }
    }
    val pieces = Regex("""(\d+)\s*(pcs|pc|pieces|piece)\b""").find(u) ?: return null
    val n = pieces.groupValues[1].toIntOrNull() ?: return null
    if (n <= 1) return null
    return "\u20b9${(price.toDouble() / n).roundToWhole()}/pc"
}

private fun Double.roundToWhole(): Int = (this + 0.5).toInt()
