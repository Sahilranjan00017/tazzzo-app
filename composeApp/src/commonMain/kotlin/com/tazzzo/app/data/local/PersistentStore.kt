package com.tazzzo.app.data.local

import com.russhwolf.settings.Settings
import com.tazzzo.app.data.model.MembershipState
import com.tazzzo.app.data.model.Money
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

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
 *  - No credentials or payment data. Auth tokens live in the platform secure
 *    store (Android Keystore / iOS Keychain, see `data/auth/SecureTokenStore`),
 *    never here. Only the non-secret `authInstallMarker` is kept.
 *  - Bounded: recent searches capped at 8, addresses at 20.
 */
class PersistentStore(provided: Settings? = null) {

    // Resolved on first read or write, never at construction. The no-arg
    // `Settings()` needs an Android Context, so building it eagerly made merely
    // loading `ServiceLocator` fail in a plain-JVM unit test. In the app the
    // first access happens on the same default store as before.
    private val settings: Settings by lazy { provided ?: Settings() }

    private val json = Json { ignoreUnknownKeys = true }

    // ---- cart --------------------------------------------------------------

    @Serializable
    data class SavedCartLine(val id: String, val qty: Int, val priceAtSavePaise: Long)

    fun saveCart(lines: List<SavedCartLine>) {
        settings.putString(KEY_CART, json.encodeToString(lines))
    }

    fun loadCart(): List<SavedCartLine> = decodeList(KEY_CART)

    fun clearCart() = settings.remove(KEY_CART)

    /**
     * The v1 cart stored `priceAtSave` as whole RUPEES against MOCK product ids. It is neither
     * reinterpreted as paise nor converted: it is dropped, once, and the caller tells the
     * customer. The v2 key (integer paise) is the only cart representation read from here on.
     *
     * @return true if an obsolete v1 cart was found and discarded.
     */
    fun discardObsoleteCart(): Boolean {
        if (settings.getStringOrNull(KEY_CART_V1) == null) return false
        settings.remove(KEY_CART_V1)
        return true
    }

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

    fun loadMembership(): MembershipState? {
        settings.getStringOrNull(KEY_MEMBERSHIP)?.let {
            return runCatching { json.decodeFromString<MembershipState>(it) }.getOrNull()
        }
        return migrateMembershipV1()
    }

    /**
     * v1 stored `cumulativeSpendRupees` / `cumulativeSavingsRupees` as whole rupees. Whole rupees
     * convert to paise exactly (x 100), so unlike the cart this IS migrated losslessly, once, then
     * the v1 entry is removed. Anything unreadable is dropped rather than guessed at.
     */
    private fun migrateMembershipV1(): MembershipState? {
        val raw = settings.getStringOrNull(KEY_MEMBERSHIP_V1) ?: return null
        settings.remove(KEY_MEMBERSHIP_V1)
        val state = runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            fun paise(key: String) = JsonPrimitive(Money.ofRupees(obj[key]?.jsonPrimitive?.long ?: 0L).paise)
            val migrated = JsonObject(
                obj.filterKeys { it != "cumulativeSpendRupees" && it != "cumulativeSavingsRupees" } +
                    mapOf("cumulativeSpendPaise" to paise("cumulativeSpendRupees"), "cumulativeSavingsPaise" to paise("cumulativeSavingsRupees"))
            )
            json.decodeFromJsonElement(MembershipState.serializer(), migrated)
        }.getOrNull() ?: return null
        saveMembership(state)
        return state
    }

    fun clearMembership() { settings.remove(KEY_MEMBERSHIP); settings.remove(KEY_MEMBERSHIP_V1) }

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

    /**
     * Whether the customer wants order and offer notifications.
     *
     * Defaults to TRUE, matching what a fresh install would send today, so the
     * switch reflects reality on first open instead of showing "off" beside a
     * system that is in fact on. Turning it off is a real preference and it
     * persists; nothing else reads it yet, which is why the row says so.
     */
    var notificationsEnabled: Boolean
        get() = settings.getBoolean(KEY_NOTIFICATIONS, true)
        set(v) = settings.putBoolean(KEY_NOTIFICATIONS, v)

    /**
     * Non-secret marker that THIS install has initialised its auth storage.
     * The iOS Keychain outlives an uninstall; this flag does not, so its
     * absence means "fresh install — clear any inherited credentials".
     */
    var authInstallMarker: Boolean
        get() = settings.getBoolean(KEY_AUTH_INSTALL, false)
        set(v) = settings.putBoolean(KEY_AUTH_INSTALL, v)

    /**
     * Random, non-secret per-install id for the `X-Tazzzo-Installation-Id` header
     * (see `data/catalog/InstallationId`). Not an account, device or ad identifier.
     */
    var installationId: String?
        get() = settings.getStringOrNull(KEY_INSTALLATION_ID)
        set(v) { if (v == null) settings.remove(KEY_INSTALLATION_ID) else settings.putString(KEY_INSTALLATION_ID, v) }

    /** The delivery PIN used for catalogue/serviceability reads. Non-secret app state. */
    var launchPin: String?
        get() = settings.getStringOrNull(KEY_LAUNCH_PIN)
        set(v) { if (v == null) settings.remove(KEY_LAUNCH_PIN) else settings.putString(KEY_LAUNCH_PIN, v) }

    // ---- helpers -----------------------------------------------------------

    private inline fun <reified T> decodeList(key: String): List<T> =
        settings.getStringOrNull(key)
            ?.let { runCatching { json.decodeFromString<List<T>>(it) }.getOrNull() }
            ?: emptyList()

    private companion object {
        const val KEY_CART = "tazzzo.cart.v2"           // integer paise (PR-04B)
        const val KEY_CART_V1 = "tazzzo.cart.v1"        // whole rupees, mock ids: discarded, never read
        const val KEY_NOTIFICATIONS = "tazzzo.prefs.notifications.v1"
        const val KEY_SESSION = "tazzzo.session.v1"
        const val KEY_ADDRESSES = "tazzzo.addresses.v1"
        const val KEY_SEARCHES = "tazzzo.searches.v1"
        const val KEY_TOUR_SEEN = "tazzzo.tourSeen.v1"
        const val KEY_ONBOARDED = "tazzzo.onboarded.v1"
        const val KEY_MEMBERSHIP = "tazzzo.membership.v2" // Money as integer paise (PR-04B)
        const val KEY_MEMBERSHIP_V1 = "tazzzo.membership.v1"
        const val KEY_AUTH_INSTALL = "tazzzo.authInstall.v1"
        const val KEY_INSTALLATION_ID = "tazzzo.installationId.v1"
        const val KEY_LAUNCH_PIN = "tazzzo.launchPin.v1"
    }
}
