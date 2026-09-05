package com.tazzzo.app.data.local

import com.russhwolf.settings.Settings
import com.tazzzo.app.data.model.MembershipState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Local persistence — NSUserDefaults on iOS, SharedPreferences on Android,
 * through multiplatform-settings.
 *
 * Production rules encoded here:
 *  - The cart persists only id + quantity + the price the customer last saw.
 *    Never a whole Product: stale products are how apps sell stock they don't
 *    have at prices they no longer charge. The price is stored ONLY to detect
 *    and disclose drift on restore — the restored cart always carries current
 *    catalogue prices.
 *  - The checkout session is deliberately NOT persisted. A half-finished
 *    transaction restored after process death is a liability, not a feature.
 *  - No credentials or payment data. When real auth lands, tokens belong in
 *    Keychain / EncryptedSharedPreferences, not here — documented in
 *    BLOCKERS.md (P0 backend).
 *  - Bounded: recent searches capped at 8, addresses at 20.
 */
class PersistentStore(private val settings: Settings = Settings()) {

    private val json = Json { ignoreUnknownKeys = true }

    // ---- cart --------------------------------------------------------------

    @Serializable
    data class SavedCartLine(val id: String, val qty: Int, val priceAtSave: Int)

    fun saveCart(lines: List<SavedCartLine>) {
        settings.putString(KEY_CART, json.encodeToString(lines))
    }

    fun loadCart(): List<SavedCartLine> = decodeList(KEY_CART)

    fun clearCart() = settings.remove(KEY_CART)

    // ---- session -----------------------------------------------------------

    @Serializable
    data class SavedSession(
        val name: String,
        val phone: String,
        val isGuest: Boolean,
        val coinBalance: Int,
        val address: String
    )

    fun saveSession(s: SavedSession) = settings.putString(KEY_SESSION, json.encodeToString(s))
    fun loadSession(): SavedSession? =
        settings.getStringOrNull(KEY_SESSION)?.let { runCatching { json.decodeFromString<SavedSession>(it) }.getOrNull() }

    // ---- addresses (user-added; seed addresses come from the repository) ---

    @Serializable
    data class SavedAddress(
        val id: String, val label: String, val line1: String,
        val line2: String, val pincode: String, val isServiceable: Boolean
    )

    fun saveAddresses(list: List<SavedAddress>) =
        settings.putString(KEY_ADDRESSES, json.encodeToString(list.take(20)))

    fun loadAddresses(): List<SavedAddress> = decodeList(KEY_ADDRESSES)

    // ---- recent searches ---------------------------------------------------

    fun saveRecentSearches(items: List<String>) =
        settings.putString(KEY_SEARCHES, json.encodeToString(items.take(8)))

    fun loadRecentSearches(): List<String> = decodeList(KEY_SEARCHES)

    // ---- Tazzzo Club membership -------------------------------------------

    /**
     * The customer's club standing.
     *
     * Stored as the domain model directly (it is already `@Serializable`)
     * rather than a private DTO, because unlike the cart there is no
     * "re-resolve against the live catalogue" step — but it carries the same
     * caveat as coins: this is a LOCAL MIRROR, not the authority. Cumulative
     * spend, savings, order counts and reward unlocks must become
     * server-authoritative before launch, for exactly the reason coin
     * crediting must (BLOCKERS.md P0). A customer who can edit their own
     * preferences file can otherwise grant themselves a discount tier.
     */
    fun saveMembership(state: MembershipState) =
        settings.putString(KEY_MEMBERSHIP, json.encodeToString(state))

    fun loadMembership(): MembershipState? =
        settings.getStringOrNull(KEY_MEMBERSHIP)?.let {
            runCatching { json.decodeFromString<MembershipState>(it) }.getOrNull()
        }

    fun clearMembership() = settings.remove(KEY_MEMBERSHIP)

    // ---- one-time flags ----------------------------------------------------

    var tourSeen: Boolean
        get() = settings.getBoolean(KEY_TOUR_SEEN, false)
        set(v) = settings.putBoolean(KEY_TOUR_SEEN, v)

    /** True once the user has passed onboarding (skip or login). Returning
     *  customers land on Home — nobody should see the login wall twice
     *  unless they log out. */
    var onboarded: Boolean
        // Migration: installs that predate this flag derive it from tourSeen —
        // anyone who completed the tour necessarily passed onboarding.
        get() = settings.getBoolean(KEY_ONBOARDED, false) || tourSeen
        set(v) = settings.putBoolean(KEY_ONBOARDED, v)

    // ---- helpers -----------------------------------------------------------

    private inline fun <reified T> decodeList(key: String): List<T> =
        settings.getStringOrNull(key)
            ?.let { runCatching { json.decodeFromString<List<T>>(it) }.getOrNull() }
            ?: emptyList()

    private companion object {
        const val KEY_CART = "tazzzo.cart.v1"
        const val KEY_SESSION = "tazzzo.session.v1"
        const val KEY_ADDRESSES = "tazzzo.addresses.v1"
        const val KEY_SEARCHES = "tazzzo.searches.v1"
        const val KEY_TOUR_SEEN = "tazzzo.tourSeen.v1"
        const val KEY_ONBOARDED = "tazzzo.onboarded.v1"
        const val KEY_MEMBERSHIP = "tazzzo.membership.v1"
    }
}
