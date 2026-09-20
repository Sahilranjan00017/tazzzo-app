package com.tazzzo.app.config

import com.tazzzo.app.data.model.Promotion
import com.tazzzo.app.data.model.PromotionAudience
import com.tazzzo.app.data.model.PromotionScope
import com.tazzzo.app.data.model.PromotionType

/**
 * How promotions combine. [BUSINESS DECISION] — these are defaults for the
 * pre-backend build, not signed policy, in the same standing as D4/D5/D6.
 *
 * @param clubStacksWithPromotions false = the Club discount and any
 *   non-stackable promotion are mutually exclusive; the engine keeps whichever
 *   saves more and tells the customer. true = Club always applies on top.
 *   Defaulted to false because "5% off AND a coupon AND a category offer" is
 *   how a promotion budget disappears in a week.
 */
data class PromotionPolicy(
    val clubStacksWithPromotions: Boolean = false,
    val allowMultipleCoupons: Boolean = false
)

/**
 * The promotion set the app runs against today.
 *
 * [MOCKED] — development fixtures. Live promotions, usage counters and real
 * validity windows are [BACKEND REQUIRED]; when that service exists this
 * object becomes a `PromotionRepository` fallback, exactly as `MockCatalog`
 * did for the catalogue. Product and category ids below are real ids from the
 * fixture catalogue, not invented ones, so every offer here is exercisable in
 * the running app.
 */
object PromotionConfig {

    val policy: PromotionPolicy = PromotionPolicy()

    val active: List<Promotion> = listOf(
        // A small product-level offer that stacks with anything.
        Promotion(
            id = "milk-5",
            campaignId = "everyday",
            title = "₹5 off Toned Milk",
            description = "On Amul Toned Milk Pouch, every order.",
            type = PromotionType.FLAT_OFF,
            scope = PromotionScope.PRODUCT,
            scopeIds = listOf("p8"),
            flatRupees = 5,
            stackable = true,
            priority = 1
        ),
        // A category offer that competes with Club (non-stackable).
        Promotion(
            id = "dairy-10",
            campaignId = "everyday",
            title = "10% off Dairy",
            description = "On dairy, bread and eggs. Capped at ₹40.",
            type = PromotionType.PERCENT_OFF,
            scope = PromotionScope.CATEGORY,
            scopeIds = listOf("dairy"),
            percent = 10,
            minOrderRupees = 150,
            maxDiscountRupees = 40,
            stackable = false,
            priority = 10
        ),
        // A cart-level coupon. Applies only when entered.
        Promotion(
            id = "tazzzo50",
            campaignId = "launch",
            title = "TAZZZO50",
            description = "₹50 off orders of ₹400 or more.",
            type = PromotionType.FLAT_OFF,
            scope = PromotionScope.CART,
            flatRupees = 50,
            minOrderRupees = 400,
            couponCode = "TAZZZO50",
            stackable = false,
            priority = 20,
            usageLimitPerUser = 1,
            validUntilLabel = "30 Sep"
        ),
        // A members-only Buy 2 Get 1 on fruit — the kind of "member-only
        // offer" the Club landing page promises. Stackable with Club because
        // it IS a Club benefit, not a competitor to it.
        Promotion(
            id = "club-banana-b2g1",
            campaignId = "club-offers",
            title = "Buy 2 get 1 free · Bananas",
            description = "Club members only.",
            type = PromotionType.BUY_X_GET_Y,
            scope = PromotionScope.PRODUCT,
            scopeIds = listOf("p4"),
            buyQuantity = 2,
            getQuantity = 1,
            audience = PromotionAudience.MEMBERS_ONLY,
            stackable = true,
            priority = 5
        )
    )
}
