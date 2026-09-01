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
    const val COIN_VIEW = "coin_view"
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
