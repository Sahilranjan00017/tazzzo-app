package com.tazzzo.app.home

import com.tazzzo.app.config.BrandCopy
import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.ui.profile.ProfileCopy
import com.tazzzo.app.ui.profile.ProfileEntry
import com.tazzzo.app.ui.profile.aboutTagline
import com.tazzzo.app.ui.profile.coinsAvailable
import com.tazzzo.app.ui.profile.configuredSupportChannels
import com.tazzzo.app.ui.profile.message
import com.tazzzo.app.ui.profile.nameProblem
import com.tazzzo.app.data.account.CustomerProfile
import com.tazzzo.app.data.account.NameSave
import com.tazzzo.app.data.content.AppInfo
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
        assertEquals(listOf(ProfileEntry.ADDRESSES, ProfileEntry.ORDERS, ProfileEntry.HELP, ProfileEntry.TERMS, ProfileEntry.PRIVACY, ProfileEntry.ABOUT), profileEntries(true))
        for (forbidden in listOf("Delete account", "Membership", "Club", "Notifications", "Language", "Wallet", "Refunds", "Gift", "Coins", "Genie", "Voice"))
            assertFalse(ProfileEntry.entries.any { it.title.contains(forbidden, ignoreCase = true) }, forbidden)
        assertTrue(ProfileEntry.entries.none { (it.subtitle ?: "").contains("coming soon", ignoreCase = true) || (it.subtitle ?: "").contains("not available", ignoreCase = true) })
    }

    @Test fun aSignedOutProfileOffersHelpLegalAndAboutOnlyAndNoCustomerData() {
        assertEquals(listOf(ProfileEntry.HELP, ProfileEntry.TERMS, ProfileEntry.PRIVACY, ProfileEntry.ABOUT), profileEntries(false))
        assertEquals("Log in", ProfileCopy.LOG_IN)
    }

    @Test fun theIdentityIsTheProfilesDisplayNameAndEmailAndNeverAPhoneOrId() {
        val none = remoteIdentity()
        assertTrue(none.isEmpty); assertNull(none.name); assertNull(none.phone); assertNull(none.email)
        val id = remoteIdentity(CustomerProfile(displayName = "Asha", email = "asha@example.com", version = 3))
        assertEquals("Asha", id.name); assertEquals("asha@example.com", id.email); assertNull(id.phone)   // no phone in the contract
        assertEquals("Signed in", ProfileCopy.SIGNED_IN)
        assertFalse("id" in ProfileCopy.SIGNED_IN_BODY.lowercase())
    }

    @Test fun theNameEditorChecksTheBackendRuleLocally() {
        assertNull(nameProblem("Asha Rao")); assertNull(nameProblem("   "))                // empty clears the name
        assertNull(nameProblem("李明")); assertNull(nameProblem("x".repeat(80)))
        assertEquals(ProfileCopy.NAME_TOO_LONG, nameProblem("x".repeat(81)))
        assertEquals(ProfileCopy.NAME_INVALID, nameProblem("a\u0007b"))
        assertEquals(ProfileCopy.NAME_STALE, NameSave.Stale.message()); assertNull(NameSave.Saved.message())
    }

    @Test fun savedAddressesAndOrdersAreRowsThatRouteToTheExistingSurfaces() {
        assertTrue(ProfileEntry.ADDRESSES in profileEntries(true)); assertTrue(ProfileEntry.ORDERS in profileEntries(true))
        assertEquals("Saved addresses", ProfileEntry.ADDRESSES.title); assertEquals("Orders", ProfileEntry.ORDERS.title)
    }

    @Test fun coinsStayUnavailableInRemoteModeAndNothingIsFabricated() {
        assertFalse(coinsAvailable(remoteMode = true))
        val all = (ProfileCopy.COINS_UNAVAILABLE_TITLE + " " + ProfileCopy.COINS_UNAVAILABLE_BODY).lowercase()
        for (w in listOf("₹", "cashback", "%", "expire", "redeem", "saved")) assertFalse(w in all, w)
    }

    @Test fun supportChannelsComeOnlyFromAValidatedAppConfig() {
        assertFalse(configuredSupportChannels().any)
        val c = configuredSupportChannels(AppInfo("+918000000000", "help@tazzzo.com", true, null))
        assertEquals("+918000000000", c.phone); assertEquals("help@tazzzo.com", c.email); assertNull(c.whatsapp)
        // The demo WhatsApp number in BrandCopy is never treated as support configuration.
        assertFalse(BrandCopy.whatsappNumber in (ProfileCopy.CONTACT_NONE + ProfileCopy.REQUESTS_EMPTY))
        for (w in listOf("24/7", "10 min", "instant")) assertFalse(w in ProfileCopy.CONTACT_NONE.lowercase(), w)
    }

    @Test fun aboutShowsTheCanonicalTaglineAndNoUnsubstantiatedClaim() {
        assertEquals("Best Value. Smart Shopping.", aboutTagline())
        assertFalse("%" in ProfileCopy.ABOUT_DESCRIPTION); assertFalse("SAVE" in ProfileCopy.ABOUT_DESCRIPTION)
        assertFalse(BrandCopy.whatsappNumber in ProfileCopy.ABOUT_DESCRIPTION)
    }

    @Test fun logoutRequiresAnExplicitConfirmation() {
        assertEquals("Log out of Tazzzo?", ProfileCopy.LOG_OUT_TITLE)
        assertEquals("Cancel", ProfileCopy.CANCEL)
    }

    @Test fun productionOrderingIsOn() { assertTrue(CatalogCapabilities.REMOTE.orderIntegration) }
}
