package com.tazzzo.app.home

import com.tazzzo.app.config.BrandCopy
import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.ui.profile.ProfileCopy
import com.tazzzo.app.ui.profile.ProfileEntry
import com.tazzzo.app.ui.profile.aboutTagline
import com.tazzzo.app.ui.profile.coinsAvailable
import com.tazzzo.app.ui.profile.configuredSupportChannels
import com.tazzzo.app.ui.profile.legalLinks
import com.tazzzo.app.ui.profile.profileEntries
import com.tazzzo.app.ui.profile.remoteIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** UI-07: Profile, Help, About and Coins render only what the REMOTE app can truthfully offer. */
class ProfileUiModelTest {

    @Test fun aSignedInProfileOffersOnlyRealDestinations() {
        assertEquals(listOf(ProfileEntry.ADDRESSES, ProfileEntry.ORDERS, ProfileEntry.COINS, ProfileEntry.HELP, ProfileEntry.ABOUT), profileEntries(true))
        for (forbidden in listOf("Delete account", "Membership", "Club", "Notifications", "Language", "Voice", "Wallet", "Refunds", "Gift"))
            assertFalse(ProfileEntry.entries.any { it.title.contains(forbidden, ignoreCase = true) }, forbidden)
    }

    @Test fun aSignedOutProfileOffersHelpAndAboutOnlyAndNoCustomerData() {
        assertEquals(listOf(ProfileEntry.HELP, ProfileEntry.ABOUT), profileEntries(false))
        assertEquals("Log in", ProfileCopy.LOG_IN)
    }

    @Test fun noInternalIdentifierOrInventedIdentityIsEverShown() {
        val id = remoteIdentity()
        assertTrue(id.isEmpty); assertNull(id.name); assertNull(id.phone); assertNull(id.email)
        // The signed-in card falls back to a neutral label, never "Guest", a customer id or a placeholder name.
        assertEquals("Signed in", ProfileCopy.SIGNED_IN)
        assertFalse("id" in ProfileCopy.SIGNED_IN_BODY.lowercase())
    }

    @Test fun savedAddressesAndOrdersAreRowsThatRouteToTheExistingSurfaces() {
        assertTrue(ProfileEntry.ADDRESSES in profileEntries(true)); assertTrue(ProfileEntry.ORDERS in profileEntries(true))
        assertEquals("Saved addresses", ProfileEntry.ADDRESSES.title); assertEquals("Orders", ProfileEntry.ORDERS.title)
    }

    @Test fun coinsAreUnavailableInRemoteModeAndNothingIsFabricated() {
        assertFalse(coinsAvailable(remoteMode = true))
        assertEquals("Not available yet", ProfileEntry.COINS.subtitle)
        val all = (ProfileCopy.COINS_UNAVAILABLE_TITLE + " " + ProfileCopy.COINS_UNAVAILABLE_BODY).lowercase()
        for (w in listOf("₹", "cashback", "%", "expire", "redeem", "saved")) assertFalse(w in all, w)
    }

    @Test fun supportHasNoConfiguredChannelSoNoActionIsDrawn() {
        val c = configuredSupportChannels()
        assertFalse(c.any); assertNull(c.phone); assertNull(c.email); assertNull(c.whatsapp)
        // The demo WhatsApp number in BrandCopy is never treated as support configuration.
        assertFalse(BrandCopy.whatsappNumber in ProfileCopy.HELP_UNAVAILABLE_BODY)
        for (w in listOf("24/7", "10 min", "refund", "instant")) assertFalse(w in ProfileCopy.HELP_UNAVAILABLE_BODY.lowercase(), w)
    }

    @Test fun aboutShowsTheCanonicalTaglineAndNoUnsubstantiatedClaim() {
        assertEquals("Best Value. Smart Shopping.", aboutTagline())
        assertFalse("%" in ProfileCopy.ABOUT_DESCRIPTION); assertFalse("SAVE" in ProfileCopy.ABOUT_DESCRIPTION)
        assertFalse(BrandCopy.whatsappNumber in ProfileCopy.ABOUT_DESCRIPTION)
    }

    @Test fun legalLinksExistOnlyWhenARealHttpsUrlIsConfigured() {
        assertFalse(legalLinks().any)
        assertFalse(legalLinks(privacyUrl = "http://insecure.example/privacy").any)
        assertFalse(legalLinks(privacyUrl = "javascript:alert(1)").any)
        assertTrue(legalLinks(privacyUrl = "https://tazzzo.example/privacy").any)
        assertNull(legalLinks(termsUrl = "ftp://x").termsUrl)
    }

    @Test fun logoutRequiresAnExplicitConfirmation() {
        assertEquals("Log out of Tazzzo?", ProfileCopy.LOG_OUT_TITLE)
        assertEquals("Cancel", ProfileCopy.CANCEL)
    }

    @Test fun productionOrderingStaysOff() { assertFalse(CatalogCapabilities.REMOTE.orderIntegration) }
}
