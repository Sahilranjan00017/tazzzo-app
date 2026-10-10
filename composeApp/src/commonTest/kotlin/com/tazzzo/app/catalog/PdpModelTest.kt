package com.tazzzo.app.catalog

import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.data.catalog.ImageRole
import com.tazzzo.app.data.catalog.ProductImage
import com.tazzzo.app.data.catalog.PurchaseAction
import com.tazzzo.app.data.catalog.StockState
import com.tazzzo.app.data.catalog.StockTone
import com.tazzzo.app.data.catalog.purchaseAction
import com.tazzzo.app.data.cart.PendingTarget
import com.tazzzo.app.data.cart.PurchaseControl
import com.tazzzo.app.data.cart.purchaseControl
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.ui.catalog.PDP_CURVE_OVERLAP_DP
import com.tazzzo.app.ui.catalog.PDP_HERO_ASPECT
import com.tazzzo.app.ui.catalog.PdpCopy
import com.tazzzo.app.ui.catalog.PdpFacts
import com.tazzzo.app.ui.catalog.pdpFacts
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** UI-04: the truthful bindings behind the `Veg Page.jpeg` layout. */
class PdpModelTest {

    @Test fun theNameAndTheHeroComeFromTheBackendPrimaryImageFirst() {
        val f = detailOf().pdpFacts()
        assertEquals("Item SKU-1", f.name)
        assertEquals(listOf("https://media.example.test/a-0.jpg", "https://media.example.test/a-1.jpg"), f.heroUrls)
        assertEquals(2, f.galleryCount)
    }

    @Test fun theGalleryIsOrderedPrimaryFirstThenByOrderWhateverTheWireOrder() {
        val f = detailOf(gallery = listOf(
            ProductImage("https://m.test/g2.jpg", ImageRole.GALLERY, 2, null, null, null),
            ProductImage("https://m.test/g1.jpg", ImageRole.GALLERY, 1, null, null, null),
            ProductImage("https://m.test/p.jpg", ImageRole.PRIMARY, 5, null, null, null)
        )).pdpFacts()
        assertEquals(listOf("https://m.test/p.jpg", "https://m.test/g1.jpg", "https://m.test/g2.jpg"), f.heroUrls)
    }

    @Test fun withoutAGalleryTheCardThumbnailIsTheHeroAndWithoutThatThereIsNoHero() {
        assertEquals(listOf("https://media.example.test/a.jpg"), detailOf(gallery = emptyList()).pdpFacts().heroUrls)
        val none = detailOf(card = cp(thumb = null), gallery = emptyList()).pdpFacts()
        assertFalse(none.hasHero); assertEquals(0, none.galleryCount)
    }

    @Test fun priceMrpAndDiscountAreTheServersOrAbsent() {
        val f = detailOf(cp(price = Money.ofPaise(2_400), mrp = Money.ofPaise(3_000), discountPercent = 20)).pdpFacts()
        assertEquals("₹24", f.price); assertEquals("₹30", f.mrp); assertEquals("20% off", f.discount)
        val flat = detailOf(cp(price = Money.ofPaise(3_000), mrp = Money.ofPaise(3_000), discountPercent = null)).pdpFacts()
        assertNull(flat.mrp); assertNull(flat.discount)
        val unpriced = detailOf(cp(price = null, mrp = Money.ofPaise(3_000), discountPercent = 20)).pdpFacts()
        assertNull(unpriced.price); assertNull(unpriced.mrp)
        assertFalse("0" in PdpCopy.PRICE_UNAVAILABLE)
    }

    @Test fun theEyebrowIsTheKnownVerticalNameOrNothing() {
        assertEquals("FRESH VEGETABLES", detailOf().pdpFacts { if (it == "TZV-000225") "Fresh Vegetables" else null }.eyebrow)
        assertNull(detailOf().pdpFacts().eyebrow)
        assertNull(detailOf().pdpFacts { "  " }.eyebrow)
    }

    @Test fun onlyScarcityUnavailabilityOrLocationEarnsAStockLine() {
        assertNull(detailOf(cp(stock = StockState.IN_STOCK)).pdpFacts().stockNote)
        assertNull(detailOf(cp(stock = StockState.UNKNOWN, serviceable = null)).pdpFacts().stockNote)
        assertEquals("Only 3 left" to StockTone.Scarce, detailOf(cp(stock = StockState.LOW_STOCK, low = 3)).pdpFacts().let { it.stockNote to it.stockTone })
        assertEquals("Out of stock", detailOf(cp(stock = StockState.OUT_OF_STOCK)).pdpFacts().stockNote)
        assertEquals("Not available at your location", detailOf(cp(stock = StockState.UNKNOWN, serviceable = false)).pdpFacts().stockNote)
    }

    @Test fun detailsAreTheGovernedAttributesWithADisplayFormOnly() {
        val f = detailOf(attributes = listOf(attr("origin", str("Nashik")), attr("weight", JsonPrimitive(500), unit = "g"), attr("blob", kotlinx.serialization.json.JsonObject(emptyMap())))).pdpFacts()
        assertEquals(listOf("Origin" to "Nashik", "Weight" to "500 g"), f.details)
        assertTrue(detailOf(attributes = emptyList()).pdpFacts().details.isEmpty())
    }

    @Test fun nothingTheReferenceShowsButTheBackendLacksIsEverRendered() {
        // Pack size, description, ratings, review counts, member price, pack selectors and recommendations are not in
        // CatalogProductDetail, so PdpFacts has no field for them; everything the page can print is listed here.
        val f = detailOf().pdpFacts()
        val rendered = (listOfNotNull(f.eyebrow, f.name, f.price, f.mrp, f.discount, f.stockNote) + f.details.flatMap { listOf(it.first, it.second) }).joinToString(" | ")
        for (forbidden in listOf("500 g", " kg", "★", "review", "4.8", "Tazzzo Price", "Get it for", "People also bought", "Pack size", "Handpicked"))
            assertFalse(forbidden in rendered, forbidden)
        assertEquals("Item SKU-1 | ₹49.50 | ₹55 | 10% off", rendered)
    }

    // ---- the purchase bar is the existing cart logic ----------------------------------------------------------------------

    @Test fun thePurchaseBarFollowsTheCanonicalPurchaseControl() {
        val p = cp(max = 2)
        assertEquals(PurchaseControl.Add, purchaseControl(p, CatalogCapabilities.REMOTE, 0, null))
        assertEquals(PurchaseControl.Stepper(1, false, true), purchaseControl(p, CatalogCapabilities.REMOTE, 1, null))
        assertEquals(PurchaseControl.Stepper(2, false, false), purchaseControl(p, CatalogCapabilities.REMOTE, 2, null))        // max reached
        assertEquals(PurchaseControl.Stepper(2, true, false), purchaseControl(p, CatalogCapabilities.REMOTE, 1, PendingTarget.Quantity(2)))
        assertEquals(PurchaseControl.Disabled("Currently unavailable"), purchaseControl(cp(buyable = false), CatalogCapabilities.REMOTE, 0, null))
        assertEquals(PurchaseControl.Disabled("Price unavailable"), purchaseControl(cp(price = null), CatalogCapabilities.REMOTE, 0, null))
        assertTrue(purchaseAction(cp(stock = StockState.OUT_OF_STOCK), CatalogCapabilities.REMOTE) is PurchaseAction.Enabled)   // stock never decides; `buyable` does
    }

    // ---- layout invariants ---------------------------------------------------------------------------------------------------

    @Test fun theHeroTakesTheReferencesShareOfTheScreen() {
        // Reference: 588 × 1280 with the photo over the top 540 px (42%). At 390dp wide the hero is 390 / aspect ≈ 371dp;
        // on an 844dp-tall phone that is 44%, inside the 40–45% band, and the curve overlaps it by 28dp.
        val heroDp = 390f / PDP_HERO_ASPECT
        assertTrue(heroDp / 844f in 0.40f..0.45f, "hero share = ${heroDp / 844f}")
        assertTrue(PDP_CURVE_OVERLAP_DP in 20..40)
        // 320dp: hero 305dp, still under half of a 640dp-tall screen so the title row is on screen without scrolling.
        assertTrue(320f / PDP_HERO_ASPECT < 640f * 0.5f)
    }

    @Test fun theNotFoundCopyIsCalmAndNeverTechnical() {
        for (s in listOf(PdpCopy.NOT_FOUND_TITLE, PdpCopy.NOT_FOUND_BODY)) { assertFalse("404" in s); assertFalse("http" in s.lowercase()); assertFalse("error" in s.lowercase()) }
        assertEquals("Back to Shop", PdpCopy.BACK_TO_SHOP)
    }

    @Test fun productionOrderingIsOn() {
        assertTrue(CatalogCapabilities.REMOTE.orderIntegration)
    }
}
