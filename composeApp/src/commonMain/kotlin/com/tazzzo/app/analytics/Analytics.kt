package com.tazzzo.app.analytics

/**
 * Vendor-neutral analytics boundary.
 *
 * Business logic and screens report events through [Analytics.track] only;
 * no vendor SDK types leak into the app. When a provider is selected, its
 * adapter implements [AnalyticsSink] and is installed at startup — nothing
 * else changes. Events never carry credentials, addresses, phone numbers or
 * payment details.
 */
object AnalyticsEvents {
    const val APP_OPEN = "app_open"
    const val SEARCH = "search"
    const val PRODUCT_VIEW = "product_view"
    const val ADD_TO_CART = "add_to_cart"
    const val REMOVE_FROM_CART = "remove_from_cart"
    const val CHECKOUT_STARTED = "checkout_started"
    const val CHECKOUT_STEP_COMPLETED = "checkout_step_completed"
    const val PAYMENT_SELECTED = "payment_selected"
    const val ORDER_ATTEMPTED = "order_attempted"
    const val ORDER_SUCCESS = "order_success"
    const val ORDER_FAILURE = "order_failure"
    const val REORDER = "reorder"
    // REMOTE order lifecycle (PR-08): outcome only. NEVER carries an order id, quote id, items, address or any amount.
    const val ORDER_PLACE_STARTED = "order_place_started"
    const val ORDER_PLACE_SUCCEEDED = "order_place_succeeded"
    const val ORDER_PLACE_FAILED = "order_place_failed"
    const val COIN_VIEW = "coin_view"

    // ---- Tazzzo Club membership ------------------------------------------
    // Payment-related events carry only a plan id, a stage and a test-mode
    // flag. No payment reference, no amount tied to a person, no PII — a
    // payment funnel is exactly where over-collection becomes a compliance
    // problem (see the raw-search-query decision still open in BLOCKERS.md).
    const val MEMBERSHIP_VIEW = "membership_view"
    const val MEMBERSHIP_BENEFIT_VIEW = "membership_benefit_view"
    const val MEMBERSHIP_JOIN_TAP = "membership_join_tap"
    const val MEMBERSHIP_PAYMENT_STARTED = "membership_payment_started"
    const val MEMBERSHIP_PAYMENT_SUCCESS = "membership_payment_success"
    const val MEMBERSHIP_PAYMENT_FAILED = "membership_payment_failed"
    const val MEMBERSHIP_PAYMENT_CANCELLED = "membership_payment_cancelled"
    const val MEMBERSHIP_PAYMENT_PENDING = "membership_payment_pending"
    const val MEMBERSHIP_ACTIVATED = "membership_activated"
    const val CLUB_DISCOUNT_VIEW = "club_discount_view"
    const val CLUB_DISCOUNT_APPLIED = "club_discount_applied"
    const val CLUB_PROGRESS_VIEW = "club_progress_view"
    const val MILESTONE_REACHED = "milestone_reached"
    const val REWARD_UNLOCKED = "reward_unlocked"
    const val REWARD_REDEEMED = "reward_redeemed"
    const val DELIVERY_SLOT_VIEW = "delivery_slot_view"
    const val DELIVERY_SLOT_SELECTED = "delivery_slot_selected"
    const val FESTIVAL_IMPRESSION = "festival_impression"
    const val FESTIVAL_CTA_TAP = "festival_cta_tap"
}

interface AnalyticsSink {
    fun track(name: String, props: Map<String, String>)
}

object NoopSink : AnalyticsSink {
    override fun track(name: String, props: Map<String, String>) {}
}

/** Development sink — visible in the device log for verification. */
object DevLogSink : AnalyticsSink {
    override fun track(name: String, props: Map<String, String>) {
        println("TAZZZO-ANALYTICS $name ${props.entries.joinToString { "${it.key}=${it.value}" }}")
    }
}

object Analytics {
    var sink: AnalyticsSink = DevLogSink   // swap for the vendor adapter at launch

    fun track(name: String, props: Map<String, String> = emptyMap()) {
        sink.track(name, props)
    }
}
