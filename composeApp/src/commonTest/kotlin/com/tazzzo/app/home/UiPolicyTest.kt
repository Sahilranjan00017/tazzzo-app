package com.tazzzo.app.home

import com.tazzzo.app.config.BrandCopy
import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.data.checkout.CheckoutCopy
import com.tazzzo.app.data.order.CustomerPaymentMethod
import com.tazzzo.app.data.order.OrderPaymentCondition
import com.tazzzo.app.ui.catalog.PdpCopy
import com.tazzzo.app.ui.catalog.ShopCopy
import com.tazzzo.app.ui.catalog.SearchSurface
import com.tazzzo.app.ui.catalog.searchSurface
import com.tazzzo.app.ui.checkout.PurchaseCopy
import com.tazzzo.app.ui.home.HomeCopy
import com.tazzzo.app.ui.order.OrderCopy
import com.tazzzo.app.ui.profile.ProfileCopy
import com.tazzzo.app.ui.profile.coinsAvailable
import com.tazzzo.app.ui.profile.configuredSupportChannels
import com.tazzzo.app.ui.profile.legalLinks
import com.tazzzo.app.ui.voice.GenieCopy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * UI-08 final policy suite: the customer-visible copy and the capability truths of every REMOTE surface, asserted through
 * the copy objects the screens render from (no screenshot pixels). A new surface adds its copy object here.
 */
class UiPolicyTest {
    private val copy: List<Pair<String, String>> = listOf(
        "HomeCopy" to listOf(HomeCopy.SEARCH_PLACEHOLDER, HomeCopy.HERO_EYEBROW, HomeCopy.HERO_CTA, HomeCopy.QUALITY_EYEBROW, HomeCopy.QUALITY_CTA, HomeCopy.RAIL_TITLE, HomeCopy.BULK_EYEBROW, HomeCopy.BULK_CTA, HomeCopy.CONTINUE_SHOPPING).joinToString(" | "),
        "ShopCopy" to listOf(ShopCopy.TITLE, ShopCopy.NO_CATEGORIES_TITLE, ShopCopy.NO_CATEGORIES_BODY, ShopCopy.EMPTY_SECTION_TITLE, ShopCopy.EMPTY_SECTION_BODY, ShopCopy.SEARCH_START_TITLE, ShopCopy.SEARCH_START_BODY, ShopCopy.NO_RESULTS_TITLE, ShopCopy.NO_RESULTS_BODY, ShopCopy.CATALOGUE_UNAVAILABLE_TITLE, ShopCopy.CATALOGUE_UNAVAILABLE_BODY, ShopCopy.END_OF_LIST, ShopCopy.PRICE_UNAVAILABLE).joinToString(" | "),
        "PdpCopy" to listOf(PdpCopy.ADD_TO_CART, PdpCopy.NOT_FOUND_TITLE, PdpCopy.NOT_FOUND_BODY, PdpCopy.BACK_TO_SHOP, PdpCopy.PRICE_UNAVAILABLE).joinToString(" | "),
        "PurchaseCopy" to listOf(PurchaseCopy.CART_EMPTY_TITLE, PurchaseCopy.CART_EMPTY_BODY, PurchaseCopy.CART_SIGNED_OUT_BODY, PurchaseCopy.ADDRESS_EMPTY_BODY, PurchaseCopy.ADDRESS_LIMIT_TITLE, PurchaseCopy.COD, PurchaseCopy.COD_HINT, PurchaseCopy.PLACING, PurchaseCopy.PAYABLE_CHANGED_TITLE, PurchaseCopy.PAYABLE_CHANGED_SUPPORT, PurchaseCopy.AMBIGUOUS_TITLE).joinToString(" | "),
        "OrderCopy" to listOf(OrderCopy.CONFIRMATION_TITLE, OrderCopy.CONFIRMATION_SUPPORT, OrderCopy.NO_ORDERS_TITLE, OrderCopy.HISTORY_FAILED_TITLE, OrderCopy.SIGNED_OUT_BODY, OrderCopy.NO_ORDERS_BODY, OrderCopy.LOAD_FAILED_BODY, OrderCopy.AMOUNT_UNAVAILABLE).joinToString(" | "),
        "ProfileCopy" to listOf(ProfileCopy.SIGNED_IN_BODY, ProfileCopy.SIGNED_OUT_BODY, ProfileCopy.COINS_SUB, ProfileCopy.HELP_UNAVAILABLE_TITLE, ProfileCopy.HELP_UNAVAILABLE_BODY, ProfileCopy.ABOUT_DESCRIPTION, ProfileCopy.LEGAL_UNAVAILABLE, ProfileCopy.COINS_UNAVAILABLE_TITLE, ProfileCopy.COINS_UNAVAILABLE_BODY).joinToString(" | "),
        "GenieCopy" to listOf(GenieCopy.TITLE, GenieCopy.STATUS, GenieCopy.HEADLINE_PLAIN, GenieCopy.HEADLINE_ITALIC, GenieCopy.BODY, GenieCopy.CTA).joinToString(" | "),
        "CheckoutCopy" to listOf(CheckoutCopy.SUBTOTAL_LABEL, CheckoutCopy.DISCOUNT_LABEL, CheckoutCopy.AMOUNT_DUE_LABEL, CheckoutCopy.NOTHING_DUE, CheckoutCopy.ORDER_CTA, CheckoutCopy.ORDERING_PAUSED, CheckoutCopy.PAYABLE_CHANGED, CheckoutCopy.CONTRACT_FAILURE).joinToString(" | ")
    )
    private val all = copy.joinToString(" | ") { it.second }

    private fun String.hasEmoji(): Boolean = any { it.code >= 0x1F300 || it.code in 0x2600..0x27BF }

    @Test fun noRemoteCopyContainsAnEmoji() { for ((name, text) in copy) assertFalse(text.hasEmoji(), name) }

    @Test fun noRemoteCopyLeaksMockFixturesOrDemoValues() {
        for (leak in listOf("Ejipura", "Asha Rao", "TZP-", "ORD_", "CHKQ_", BrandCopy.whatsappNumber, "demo", "mock", "sample", "lorem"))
            assertFalse(leak.lowercase() in all.lowercase(), leak)
    }

    @Test fun noRemoteCopyMakesAnUnsupportedOperationalPromise() {
        for (claim in listOf("10 min", "20 min", "30 min", "minutes", "24/7", "guaranteed", "cashback", "₹1 = ", "return within", "7 days", "24 hours", "free delivery", "instant", "India's first", "SAVE 8", "picked today", "farm fresh"))
            assertFalse(claim.lowercase() in all.lowercase(), claim)
    }

    @Test fun noRemoteCopyExposesTechnicalDetail() {
        for (tech in listOf("HTTP", "404", "409", "500", "exception", "Exception", "Mongo", "/v1/", "null", "timeout"))
            assertFalse(tech in all, tech)
    }

    @Test fun codIsTheOnlyPaymentMethodAndItNeverReadsPaid() {
        assertEquals(setOf("COD", "UNRECOGNIZED"), CustomerPaymentMethod.entries.map { it.name }.toSet())
        assertEquals(setOf("COD_DUE", "UNRECOGNIZED"), OrderPaymentCondition.entries.map { it.name }.toSet())
        assertFalse("paid" in all.lowercase()); assertFalse("upi" in all.lowercase()); assertFalse("card" in all.lowercase().replace("cart", ""))
    }

    @Test fun noFakeSlotHistoryCoinsSupportOrLegal() {
        assertFalse("slot" in all.lowercase())
        assertTrue(CatalogCapabilities.REMOTE.orderHistoryIntegration)                       // real history, not a fake one
        assertFalse(CatalogCapabilities.REMOTE.reorder)
        assertFalse(coinsAvailable(remoteMode = true))
        assertFalse(configuredSupportChannels().any)
        assertFalse(legalLinks().any)
        assertEquals(SearchSurface.Available, searchSurface(CatalogCapabilities.REMOTE))     // real product search
    }

    @Test fun voiceIsTruthfullyComingSoonAndHasNoTapToSpeak() {
        assertEquals("Voice ordering is coming soon", GenieCopy.STATUS)
        assertTrue("Nothing is placed without you" in GenieCopy.BODY)
        for (w in listOf("tap to speak", "listening", "india's first", "hindi")) assertFalse(w in (GenieCopy.BODY + GenieCopy.HEADLINE_PLAIN + GenieCopy.HEADLINE_ITALIC).lowercase(), w)
        assertEquals("Microphone, decorative", GenieCopy.MARK_DESCRIPTION)
    }

    @Test fun theBrandIsTheLowercaseWordmarkAndTheCanonicalTagline() {
        assertEquals("Best Value. Smart Shopping.", BrandCopy.tagline)
        assertFalse("Tazzzo Logo" in all); assertFalse("orange" in all.lowercase())
    }

    @Test fun productionCapabilityGatesAreUnchanged() {
        val c = CatalogCapabilities.REMOTE
        assertTrue(c.orderIntegration); assertTrue(c.orderHistoryIntegration); assertFalse(c.reorder); assertTrue(c.search); assertFalse(c.deals); assertFalse(c.banners)
        assertTrue(c.cartIntegration); assertTrue(c.checkoutIntegration)
    }
}
