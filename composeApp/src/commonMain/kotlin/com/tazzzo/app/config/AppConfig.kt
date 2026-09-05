package com.tazzzo.app.config

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
    /** Rupee value of one coin. */
    val rupeesPerCoin: Int = 1,
    /** Max coins redeemable on a single order; null = uncapped. */
    val maxRedeemPerOrder: Int? = null,
    /** Days until earned coins expire; null = never. */
    val expiryDays: Int? = null,
    /** Master switch — false hides all coin UI. */
    val enabled: Boolean = true
) {
    fun coinsFor(itemTotalRupees: Int): Int = (itemTotalRupees * earnPercent) / 100
    val valueCopy: String get() = "1 Coin = ₹$rupeesPerCoin"
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
    com.tazzzo.app.data.model.DeliveryInstruction("security", "Leave with security")
)

data class ChargeRules(
    val freeDeliveryAboveRupees: Int = 199,
    val deliveryFeeRupees: Int = 25,
    val handlingFeeRupees: Int = 5
)

// ---------------------------------------------------------------------------
// Brand copy  [DECISION D2 — cosmetic, changeable any time]
// ---------------------------------------------------------------------------

object BrandCopy {
    /** Lockup line directly under the logo. */
    const val tagline = "Better Value. Easier Shopping."

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
