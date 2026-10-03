package com.tazzzo.app.ui.profile

import com.tazzzo.app.config.BrandCopy

/*
 * Presentation bindings for Profile, Help, About and Coins (UI-07). Audit of the REAL capabilities behind them:
 *  - identity: the REMOTE session carries no display name, phone or email in the app (login clears the mock profile; the
 *    backend's `GET /v1/customer/profile` has no client in this app yet) → no identity fields are rendered;
 *  - addresses, orders: real surfaces exist (UI-05, UI-06) → entries route to them;
 *  - coins: REMOTE has no coins contract (`RemoteModeCoinGuard`) → one truthful unavailable page, never a balance or ledger;
 *  - support: no configured support channel (the Help screen's WhatsApp action is a demo stub) and no backed FAQs
 *    (the fixtures promise coins rates, return windows and fees that nothing in the running product honours) → a
 *    truthful "support isn't set up yet" page;
 *  - legal: no Privacy Policy / Terms URL is configured anywhere → the rows are omitted until one is;
 *  - delete account, membership, notifications, language, voice: no supported customer-facing flow → omitted.
 * Nothing here reads mock data in REMOTE mode.
 */

object ProfileCopy {
    const val TITLE = "Profile"
    const val SIGNED_IN = "Signed in"
    const val SIGNED_IN_BODY = "Your orders and addresses are saved to your account."
    const val SIGNED_OUT_TITLE = "You're not signed in"
    const val SIGNED_OUT_BODY = "Log in to see your orders, addresses and account."
    const val LOG_IN = "Log in"
    const val ADDRESSES = "Saved addresses"
    const val ORDERS = "Orders"
    const val COINS = "Tazzzo Coins"
    const val COINS_SUB = "Not available yet"
    const val HELP = "Help"
    const val ABOUT = "About Tazzzo"
    const val GENIE = "Tazzzo Genie"
    const val GENIE_SUB = "Voice ordering is coming soon"
    const val LOG_OUT = "Log out"
    const val LOG_OUT_TITLE = "Log out of Tazzzo?"
    const val LOG_OUT_BODY = "Your cart and addresses stay saved to your account."
    const val CANCEL = "Cancel"
    const val SECTION_ACCOUNT = "Your account"
    const val SECTION_MORE = "More"
    const val VERSION_PREFIX = "Version"

    const val HELP_TITLE = "Help"
    const val HELP_UNAVAILABLE_TITLE = "Support isn't set up in the app yet"
    const val HELP_UNAVAILABLE_BODY = "We're getting it ready. Your orders and addresses are always available from your profile."
    const val HELP_GO_TO_ORDERS = "Go to Orders"

    const val ABOUT_TITLE = "About"
    const val ABOUT_DESCRIPTION = "Tazzzo brings everyday groceries and household essentials to your door at honest prices."
    const val LEGAL_UNAVAILABLE = "Privacy Policy and Terms will appear here once published."

    const val COINS_TITLE = "Tazzzo Coins"
    const val COINS_UNAVAILABLE_TITLE = "Tazzzo Coins aren't available yet"
    const val COINS_UNAVAILABLE_BODY = "When coins launch, your balance and activity will appear here."
    const val BACK_TO_PROFILE = "Back to Profile"
}

/** The rows a signed-in profile offers. Each is a REAL destination; nothing decorative. */
enum class ProfileEntry(val title: String, val subtitle: String?) {
    ADDRESSES(ProfileCopy.ADDRESSES, null),
    ORDERS(ProfileCopy.ORDERS, null),
    COINS(ProfileCopy.COINS, ProfileCopy.COINS_SUB),
    HELP(ProfileCopy.HELP, null),
    ABOUT(ProfileCopy.ABOUT, null),
    GENIE(ProfileCopy.GENIE, ProfileCopy.GENIE_SUB)
}

/** Account rows need a session; Help and About do not. */
fun profileEntries(signedIn: Boolean): List<ProfileEntry> =
    if (signedIn) ProfileEntry.entries.toList() else listOf(ProfileEntry.HELP, ProfileEntry.ABOUT, ProfileEntry.GENIE)

/** Identity the app can truthfully show for the REMOTE session: none today (see the file header). */
data class ProfileIdentity(val name: String?, val phone: String?, val email: String?) {
    val isEmpty: Boolean get() = name == null && phone == null && email == null
    companion object { val NONE = ProfileIdentity(null, null, null) }
}

/** What the REMOTE app knows about the customer: nothing identifying, by design — no internal id is ever surfaced. */
fun remoteIdentity(): ProfileIdentity = ProfileIdentity.NONE

/** Legal links exist only when a real URL is configured. Nothing is configured today. */
data class LegalLinks(val privacyUrl: String?, val termsUrl: String?) {
    val any: Boolean get() = privacyUrl != null || termsUrl != null
}

fun legalLinks(privacyUrl: String? = null, termsUrl: String? = null): LegalLinks =
    LegalLinks(privacyUrl?.takeIf { it.startsWith("https://") }, termsUrl?.takeIf { it.startsWith("https://") })

/** Support channels exist only when configured. The demo WhatsApp number in BrandCopy is NOT a configured support channel. */
data class SupportChannels(val phone: String?, val email: String?, val whatsapp: String?) {
    val any: Boolean get() = phone != null || email != null || whatsapp != null
    companion object { val NONE = SupportChannels(null, null, null) }
}

fun configuredSupportChannels(): SupportChannels = SupportChannels.NONE

/** Coins in REMOTE: no contract, so no balance, no ledger, no value claim. */
fun coinsAvailable(remoteMode: Boolean): Boolean = !remoteMode

/** The About tagline is the canonical brand line and nothing else (no savings claim: Decision D6 is unsubstantiated). */
fun aboutTagline(): String = BrandCopy.tagline
