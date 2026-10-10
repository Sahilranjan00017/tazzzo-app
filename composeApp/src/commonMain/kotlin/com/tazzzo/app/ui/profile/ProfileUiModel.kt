package com.tazzzo.app.ui.profile

import com.tazzzo.app.config.BrandCopy

/*
 * Presentation bindings for Profile, Help & support, About and Legal (UI-07, product-closure release). REAL capabilities:
 *  - identity: `GET /v1/customer/profile` returns an optional display name and email (and NO phone number): the card shows the
 *    display name (editable through PATCH) and the email when set, otherwise "Signed in"; the customer id is never shown;
 *  - addresses, orders: real surfaces (UI-05, UI-06);
 *  - help & support: FAQs from `GET /v1/content/faqs`, contact details from `GET /v1/app-config`, and the customer's own
 *    support requests (`/v1/customer/support/cases`);
 *  - legal: Terms and Privacy from `GET /v1/content/legal/{slug}`, rendered as plain text in the app;
 *  - coins, voice ordering (Genie), membership, notifications, language, delete account: no supported customer flow in REMOTE →
 *    no row (the MOCK-mode screens are untouched).
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
    const val HELP = "Help & support"
    const val TERMS = "Terms of Service"
    const val PRIVACY = "Privacy Policy"
    const val ABOUT = "About Tazzzo"
    const val EDIT_NAME = "Edit name"
    const val ADD_NAME = "Add your name"
    const val NAME_TITLE = "Your name"
    const val NAME_HINT = "Shown on your profile. Leave empty to remove it."
    const val NAME_TOO_LONG = "Use up to 80 characters."
    const val NAME_INVALID = "That name can't be saved. Please check it and try again."
    const val NAME_STALE = "Your profile changed on another device. Please try again."
    const val NAME_FAILED = "We couldn't save your name right now. Please try again."
    const val SAVE = "Save"
    const val LOG_OUT = "Log out"
    const val LOG_OUT_TITLE = "Log out of Tazzzo?"
    const val LOG_OUT_BODY = "Your cart and addresses stay saved to your account."
    const val CANCEL = "Cancel"
    const val SECTION_ACCOUNT = "Your account"
    const val SECTION_MORE = "More"
    const val VERSION_PREFIX = "Version"

    const val HELP_TITLE = "Help & support"
    const val CONTACT_TITLE = "Contact us"
    const val CONTACT_NONE = "Contact details aren't available right now. You can still send us a request below."
    const val CALL = "Call"
    const val EMAIL = "Email"
    const val REQUESTS_TITLE = "My requests"
    const val REQUESTS_EMPTY = "You haven't contacted us yet."
    const val REQUESTS_SIGNED_OUT = "Log in to contact us or see your requests."
    const val REQUESTS_FAILED = "We couldn't load your requests."
    const val NEW_REQUEST = "Contact us"
    const val FAQ_TITLE = "Frequently asked questions"
    const val FAQ_EMPTY = "No FAQs have been published yet."
    const val FAQ_FAILED = "We couldn't load the FAQs."
    const val TRY_AGAIN = "Try again"
    const val LOAD_MORE = "Load more"

    const val ABOUT_TITLE = "About"
    const val ABOUT_DESCRIPTION = "Tazzzo brings everyday groceries and household essentials to your door at honest prices."
    const val LEGAL_SECTION = "Legal"

    const val COINS_TITLE = "Tazzzo Coins"
    const val COINS_UNAVAILABLE_TITLE = "Tazzzo Coins aren't available yet"
    const val COINS_UNAVAILABLE_BODY = "When coins launch, your balance and activity will appear here."
    const val BACK_TO_PROFILE = "Back to Profile"
}

/** The rows the REMOTE profile offers. Each is a REAL destination; nothing decorative, nothing "coming soon". */
enum class ProfileEntry(val title: String, val subtitle: String?) {
    ADDRESSES(ProfileCopy.ADDRESSES, null),
    ORDERS(ProfileCopy.ORDERS, null),
    HELP(ProfileCopy.HELP, null),
    TERMS(ProfileCopy.TERMS, null),
    PRIVACY(ProfileCopy.PRIVACY, null),
    ABOUT(ProfileCopy.ABOUT, null)
}

/** Account rows need a session; Help, the legal documents and About do not. */
fun profileEntries(signedIn: Boolean): List<ProfileEntry> =
    if (signedIn) ProfileEntry.entries.toList() else listOf(ProfileEntry.HELP, ProfileEntry.TERMS, ProfileEntry.PRIVACY, ProfileEntry.ABOUT)

/** The account section (signed in) and the "More" section, in display order. */
val ACCOUNT_ENTRIES = listOf(ProfileEntry.ADDRESSES, ProfileEntry.ORDERS)
val MORE_ENTRIES = listOf(ProfileEntry.HELP, ProfileEntry.TERMS, ProfileEntry.PRIVACY, ProfileEntry.ABOUT)

/** Identity the app can truthfully show: the profile's display name and email when set. Never a phone (not in the contract). */
data class ProfileIdentity(val name: String?, val phone: String?, val email: String?) {
    val isEmpty: Boolean get() = name == null && phone == null && email == null
    companion object { val NONE = ProfileIdentity(null, null, null) }
}

/** What the REMOTE app knows about the customer: the loaded profile's display name/email, or nothing — no internal id ever. */
fun remoteIdentity(profile: com.tazzzo.app.data.account.CustomerProfile? = null): ProfileIdentity =
    if (profile == null) ProfileIdentity.NONE else ProfileIdentity(profile.displayName, null, profile.email)

/** The inline message for the name editor's state, or null. */
fun com.tazzzo.app.data.account.NameSave.message(): String? = when (this) {
    com.tazzzo.app.data.account.NameSave.Invalid -> ProfileCopy.NAME_INVALID
    com.tazzzo.app.data.account.NameSave.Stale -> ProfileCopy.NAME_STALE
    com.tazzzo.app.data.account.NameSave.Failed -> ProfileCopy.NAME_FAILED
    else -> null
}

/** The local check of a typed name, as copy (null when it can be sent). */
fun nameProblem(raw: String): String? = when (com.tazzzo.app.data.account.DisplayNameRules.check(raw)) {
    is com.tazzzo.app.data.account.DisplayNameRules.Check.Ok -> null
    com.tazzzo.app.data.account.DisplayNameRules.Check.TooLong -> ProfileCopy.NAME_TOO_LONG
    com.tazzzo.app.data.account.DisplayNameRules.Check.InvalidCharacters -> ProfileCopy.NAME_INVALID
}

/** Support contacts exist only when configured (app-config) AND valid. The demo WhatsApp number is NOT a support channel. */
data class SupportChannels(val phone: String?, val email: String?, val whatsapp: String?) {
    val any: Boolean get() = phone != null || email != null || whatsapp != null
    companion object { val NONE = SupportChannels(null, null, null) }
}

fun configuredSupportChannels(info: com.tazzzo.app.data.content.AppInfo? = null): SupportChannels =
    if (info == null) SupportChannels.NONE else SupportChannels(info.supportPhone, info.supportEmail, null)

/** Coins in REMOTE: no contract, so no balance, no ledger, no value claim. */
fun coinsAvailable(remoteMode: Boolean): Boolean = !remoteMode

/** The About tagline is the canonical brand line and nothing else (no savings claim: Decision D6 is unsubstantiated). */
fun aboutTagline(): String = BrandCopy.tagline
