package com.tazzzo.app.config

import com.tazzzo.app.data.model.Money

/**
 * Business-critical values that must NEVER be hard-coded into screens.
 *
 * Everything here is either (a) a customer-facing promise, (b) money, or
 * (c) brand copy — all of which change without an engineering release and
 * some of which carry legal weight.
 *
 * Today these are compile-time defaults. Each is shaped so it can be replaced
 * by a remote config / serviceability response without touching any screen.
 */

// ---------------------------------------------------------------------------
// Delivery promise  [DECISION D4 — pending sign-off before launch]
// ---------------------------------------------------------------------------

/**
 * What we are willing to tell a customer about delivery speed.
 *
 * The app previously hard-coded "1 hour" in 12 places and displayed an invented
 * "Avg 28 min today". Both are claims we cannot currently honour, so the default
 * is [Unknown], which renders neutral copy.
 *
 * When the serviceability service exists it returns one of these per pincode.
 */
sealed interface DeliveryPromise {
    /** No verified promise available — show neutral, non-committal copy. */
    data object Unknown : DeliveryPromise

    /** A verified window, e.g. 45..75 minutes. */
    data class Window(val minMinutes: Int, val maxMinutes: Int) : DeliveryPromise

    /** A verified single estimate backed by live data. */
    data class Estimate(val minutes: Int) : DeliveryPromise

    /** We do not deliver to this location yet. */
    data object NotServiceable : DeliveryPromise
}

object DeliveryCopy {
    /** Headline used in the home header. Never states a time we cannot honour. */
    fun headline(promise: DeliveryPromise): String = when (promise) {
        is DeliveryPromise.Estimate -> "Delivery in ${promise.minutes} mins"
        is DeliveryPromise.Window -> "Delivery in ${promise.minMinutes}–${promise.maxMinutes} mins"
        DeliveryPromise.NotServiceable -> "Not delivering here yet"
        DeliveryPromise.Unknown -> "Fast delivery"
    }

    /** Short form for product cards and cart rows. */
    fun short(promise: DeliveryPromise): String? = when (promise) {
        is DeliveryPromise.Estimate -> "${promise.minutes} mins"
        is DeliveryPromise.Window -> "${promise.minMinutes}–${promise.maxMinutes} mins"
        else -> null    // render nothing rather than a false claim
    }

    /** Supporting line under the headline. */
    fun subtitle(promise: DeliveryPromise): String = when (promise) {
        DeliveryPromise.NotServiceable -> "Tell us where to launch next"
        DeliveryPromise.Unknown -> "Freshly sourced, delivered to your door"
        else -> "Freshly sourced, delivered to your door"
    }
}

// ---------------------------------------------------------------------------
// Loyalty  [DECISION D5 — pending sign-off before launch]
// ---------------------------------------------------------------------------

/**
 * Tazzzo Coins rules. Displayed copy is derived from these values, so the app
 * can never advertise an earn rate the ledger does not implement.
 */
data class CoinRules(
    /** Percent of item total earned as coins. */
    val earnPercent: Int = 2,
    /** Value of one coin. */
    val valuePerCoin: Money = Money.ofRupees(1),
    /** Max coins redeemable on a single order; null = uncapped. */
    val maxRedeemPerOrder: Int? = null,
    /** Days until earned coins expire; null = never. */
    val expiryDays: Int? = null,
    /** Master switch — false hides all coin UI. */
    val enabled: Boolean = true
) {
    /** Coins earned: `floor(itemTotal * earnPercent / 100)` whole rupees, integer paise arithmetic. */
    fun coinsFor(itemTotal: Money): Int = (itemTotal.percentOf(earnPercent).paise / 100L).toInt()
    val valueCopy: String get() = "1 Coin = $valuePerCoin"
    val earnCopy: String get() = "Earn $earnPercent% back on every order"
}

// ---------------------------------------------------------------------------
// Charges
// ---------------------------------------------------------------------------

/**
 * Delivery instruction options. Configuration, not code: a market can add
 * "Leave with security" or drop "Ring the bell" without touching a screen.
 * [BUSINESS DECISION] on the final set; these are sensible Indian-household
 * defaults for the pre-backend build.
 */
val defaultDeliveryInstructions = listOf(
    com.tazzzo.app.data.model.DeliveryInstruction("no-bell", "Don't ring the bell"),
    com.tazzzo.app.data.model.DeliveryInstruction("at-door", "Leave at my door"),
    com.tazzzo.app.data.model.DeliveryInstruction("call", "Call on arrival"),
    com.tazzzo.app.data.model.DeliveryInstruction("security", "Leave with security"),
    // Chosen in the cart rather than at review, because it changes how the
    // order is PACKED, not how it is handed over. It travels the same path as
    // every other instruction so the store actually receives it.
    com.tazzzo.app.data.model.DeliveryInstruction("no-bag", "No carry bag needed")
)

data class ChargeRules(
    val freeDeliveryAbove: Money = Money.ofRupees(199),
    val deliveryFee: Money = Money.ofRupees(25),
    val handlingFee: Money = Money.ofRupees(5)
)

// ---------------------------------------------------------------------------
// Brand copy  [DECISION D2 — cosmetic, changeable any time]
// ---------------------------------------------------------------------------

object BrandCopy {
    /** Lockup line directly under the logo. */
    /** Canonical brand tagline (Product decision Z4, 2026-10-02). Brand surfaces only. */
    const val tagline = "Best Value. Smart Shopping."

    /**
     * Founder-supplied savings claim from the Tazzzo flyer. DECISION D6:
     * must be substantiated (real price-comparison data) before launch;
     * set to null to run the claim-free hero copy.
     */
    val savingsClaim: String? = "SAVE 8–20%"
    val savingsClaimSub: String = "on market prices, every day"

    /** Supporting promise used on marketing surfaces. */
    const val promise = "Smart Groceries. Better Prices."

    const val whatsappNumber = "8050316087"
    /** Founder positioning line — pending substantiation review (D6). */
    const val voiceTeaser = "India's first Voice Commerce"
    const val voiceSub = "Speak your list in Hindi or English"
    const val voiceStatus = "Coming soon"
}

// ---------------------------------------------------------------------------
// Single access point
// ---------------------------------------------------------------------------

/**
 * Runtime configuration. Replace the backing values from a remote-config
 * response at startup; screens read through this object and update reactively.
 */
object AppConfig {
    /** True in development/demo builds: shows demo hints (e.g. OTP shortcut copy). */
    const val demoMode = true

    var deliveryPromise: DeliveryPromise = DeliveryPromise.Unknown
    var coins: CoinRules = CoinRules()
    var charges: ChargeRules = ChargeRules()
}
