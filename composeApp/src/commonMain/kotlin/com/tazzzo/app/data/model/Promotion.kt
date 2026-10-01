package com.tazzzo.app.data.model

import kotlinx.serialization.Serializable

/*
 * Promotions — the decision layer the cart was missing.
 *
 * Before this file existed Tazzzo had exactly one kind of saving (the Club
 * discount) and it stood alone. The moment a second kind arrives — a coupon, a
 * festival offer, a Buy-X-Get-Y — something has to decide which applies,
 * whether two may combine, and what to TELL the customer. That "why" is the
 * whole point: a total that changes silently is a total the customer does not
 * trust.
 *
 * Nothing here computes money. `config/PromotionEngine.kt` does, once, and
 * `BillCalculator` is its only caller. Screens render `AppliedPromotion` and
 * `DeclinedPromotion` verbatim; they never re-derive a discount.
 *
 * Every rule is data, so the backend owns it later: [BACKEND REQUIRED] for the
 * live promotion set, usage counters and real validity dates. Until then the
 * set in `PromotionConfig` is [MOCKED] and says so.
 */

enum class PromotionType {
    /** `percent` off the eligible base, optionally capped. */
    PERCENT_OFF,
    /** `flat` off the eligible base. */
    FLAT_OFF,
    /** Buy `buyQuantity` of a product, the cheapest `getQuantity` are free. */
    BUY_X_GET_Y,
    /** Waives the delivery fee. Carries no rupee discount itself. */
    FREE_DELIVERY
}

/** What the promotion's "eligible base" is. */
enum class PromotionScope {
    /** The whole cart's item total. */
    CART,
    /** Only lines whose product id is in `scopeIds`. */
    PRODUCT,
    /** Only lines whose category id is in `scopeIds`. */
    CATEGORY
}

enum class PromotionAudience { EVERYONE, MEMBERS_ONLY, NON_MEMBERS_ONLY }

/**
 * One promotion rule. Configuration, not behaviour.
 *
 * @param couponCode when non-null the promotion applies ONLY if the customer
 *   entered this code (case-insensitive). Null = auto-applied when eligible.
 * @param stackable false = mutually exclusive with every other non-stackable
 *   promotion; the engine keeps the single best. true = combines freely with
 *   other stackables.
 * @param priority tie-break among equal-value candidates; higher wins. Never
 *   overrides "best discount wins" — it only decides ties, deterministically.
 * @param minOrder measured against the promotion's OWN scope base, not
 *   the whole cart — a dairy offer's minimum is a minimum on dairy.
 * @param usageLimitPerUser null = unlimited. Enforced by the backend; carried
 *   here so the UI can explain "you've used this offer" when told so.
 * @param validFromLabel / validUntilLabel display copy only. Real validity is
 *   a server decision; the client must never gate money on its own clock.
 */
@Serializable
data class Promotion(
    val id: String,
    val campaignId: String? = null,
    val title: String,
    val description: String,
    val type: PromotionType,
    val scope: PromotionScope,
    val scopeIds: List<String> = emptyList(),
    val audience: PromotionAudience = PromotionAudience.EVERYONE,
    val percent: Int? = null,
    val flat: Money? = null,
    val buyQuantity: Int? = null,
    val getQuantity: Int? = null,
    val minOrder: Money = Money.ZERO,
    val maxDiscount: Money? = null,
    val couponCode: String? = null,
    val stackable: Boolean = false,
    val priority: Int = 0,
    val usageLimitPerUser: Int? = null,
    val validFromLabel: String? = null,
    val validUntilLabel: String? = null,
    val excludedProductIds: List<String> = emptyList()
) {
    init {
        flat?.let { Money.requireNonNegative(it, "flat") }
        Money.requireNonNegative(minOrder, "minOrder")
        maxDiscount?.let { Money.requireNonNegative(it, "maxDiscount") }
    }

    val isCoupon: Boolean get() = couponCode != null
}

/** A promotion that IS on this bill, with the sentence that explains it. */
@Serializable
data class AppliedPromotion(
    val promotionId: String,
    val title: String,
    val discount: Money,
    /** Customer-facing. "10% off dairy, capped at ₹40". */
    val explanation: String,
    val freeDelivery: Boolean = false
) {
    init { Money.requireNonNegative(discount, "discount") }
}

/**
 * A promotion the customer could see but did not get, with the reason in
 * plain words. Rendering these is what turns "why did my total change?" into
 * "oh, that's why".
 */
@Serializable
data class DeclinedPromotion(
    val promotionId: String,
    val title: String,
    /** Customer-facing. "Add ₹72 more to dairy items", "Cannot be combined with your Club discount". */
    val reason: String,
    /** What it WOULD have saved, when that is meaningful to show. */
    val wouldHaveSaved: Money? = null
)

/**
 * The engine's complete verdict for one cart. Immutable; the bill is built
 * from it and screens read from it.
 *
 * @param clubApplied whether the Club discount survived stacking. When Club and
 *   a non-stackable promotion are mutually exclusive, the better one wins and
 *   the other appears in [declined] — the customer is told, never surprised.
 * @param bestOfferNote one sentence for the cart when a choice was made
 *   between competing offers. Null when nothing competed.
 */
@Serializable
data class PromotionResolution(
    val applied: List<AppliedPromotion> = emptyList(),
    val declined: List<DeclinedPromotion> = emptyList(),
    val promotionDiscount: Money = Money.ZERO,
    val freeDelivery: Boolean = false,
    val clubApplied: Boolean = false,
    val clubDiscount: Money = Money.ZERO,
    val bestOfferNote: String? = null
) {
    companion object {
        val NONE = PromotionResolution()
    }
}
