package com.tazzzo.app.catalog

import com.tazzzo.app.HomeTab
import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.data.catalog.CatalogMode
import com.tazzzo.app.data.catalog.PurchaseAction
import com.tazzzo.app.data.catalog.StockState
import com.tazzzo.app.data.catalog.StockTone
import com.tazzzo.app.data.catalog.purchaseAction
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.ui.catalog.BrowsePath
import com.tazzzo.app.ui.catalog.CATEGORY_GRID_MIN_CELL_DP
import com.tazzzo.app.ui.catalog.PRODUCT_GRID_MIN_CELL_DP
import com.tazzzo.app.ui.catalog.SearchSurface
import com.tazzzo.app.ui.catalog.ShopCopy
import com.tazzzo.app.ui.catalog.cardFacts
import com.tazzzo.app.ui.catalog.searchSurface
import com.tazzzo.app.ui.home.visibleHomeTabs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** UI-03: the truthful bindings behind Shop, browsing, the canonical card and Search. */
class ShopModelTest {

    // ---- the canonical card ------------------------------------------------------------------------------------------

    @Test fun priceAndMrpRenderExactlyAsTheServerGaveThem() {
        val f = cp(price = Money.ofPaise(4_950), mrp = Money.ofPaise(5_500), discountPercent = 10).cardFacts()
        assertEquals("₹49.50", f.price); assertEquals("₹55", f.mrp); assertEquals("10% off", f.discount)
        assertEquals("https://media.example.test/a.jpg", f.imageUrl)
    }

    @Test fun theDiscountChipAppearsOnlyWhenTheServerSentADiscount() {
        assertNull(cp(discountPercent = null).cardFacts().discount)
        assertNull(cp(discountPercent = 0).cardFacts().discount)
        assertEquals("12% off", cp(discountPercent = 12).cardFacts().discount)
    }

    @Test fun theMrpIsStruckOnlyWhenItReallyIsHigher() {
        assertNull(cp(price = Money.ofPaise(5_500), mrp = Money.ofPaise(5_500)).cardFacts().mrp)
        assertNull(cp(price = Money.ofPaise(5_500), mrp = null).cardFacts().mrp)
        assertNull(cp(price = null, mrp = Money.ofPaise(5_500)).cardFacts().mrp)
    }

    @Test fun noPriceMeansPriceUnavailableNeverZero() {
        val f = cp(price = null, mrp = null, discountPercent = null).cardFacts()
        assertNull(f.price); assertNull(f.mrp); assertNull(f.discount)
        assertFalse("0" in ShopCopy.PRICE_UNAVAILABLE)
    }

    @Test fun noPackSizeIsEverInvented() {
        // The card facts have no pack-size field at all: the backend sends none, so nothing can render one.
        val f = cp().cardFacts()
        val rendered = listOfNotNull(f.name, f.price, f.mrp, f.discount, f.stockNote).joinToString(" ").lowercase()
        for (unit in listOf(" g", " kg", " ml", " l ", "pack", "pcs")) assertFalse(unit in rendered, unit)
    }

    @Test fun onlyScarcityOrUnavailabilityEarnsAStockLine() {
        assertNull(cp(stock = StockState.IN_STOCK).cardFacts().stockNote)
        assertNull(cp(stock = StockState.UNKNOWN, serviceable = null).cardFacts().stockNote)
        assertEquals("Only 3 left" to StockTone.Scarce, cp(stock = StockState.LOW_STOCK, low = 3).cardFacts().let { it.stockNote to it.stockTone })
        assertEquals("Out of stock" to StockTone.Unavailable, cp(stock = StockState.OUT_OF_STOCK).cardFacts().let { it.stockNote to it.stockTone })
    }

    @Test fun theCardsAddControlFollowsTheServersBuyableAndTheRealCartCapability() {
        assertEquals(PurchaseAction.Enabled, purchaseAction(cp(), CatalogCapabilities.REMOTE))
        assertTrue(purchaseAction(cp(buyable = false), CatalogCapabilities.REMOTE) is PurchaseAction.Disabled)
        assertTrue(purchaseAction(cp(price = null), CatalogCapabilities.REMOTE) is PurchaseAction.Disabled)
    }

    @Test fun theImageUrlOnTheCardIsTheBackendsThumbnailOrNothing() {
        assertNull(cp(thumb = null).cardFacts().imageUrl)
    }

    // ---- the browse path (any depth) ----------------------------------------------------------------------------------

    @Test fun theBrowsePathOpensOnTheNodeAndListsIt() {
        val p = BrowsePath("TZS-000001")
        assertEquals("TZS-000001", p.current)
        assertEquals(listOf("TZS-000001"), p.levels)
    }

    @Test fun choosingAChildNarrowsTheListAndRevealsItsOwnLevel() {
        val p = BrowsePath("TZS-000001").select(0, "TZC-000010").select(1, "TZG-000100").select(2, "TZV-000225")
        assertEquals("TZV-000225", p.current)
        assertEquals(listOf("TZS-000001", "TZC-000010", "TZG-000100", "TZV-000225"), p.levels)
        assertTrue(p.isSelected(1, "TZG-000100")); assertFalse(p.isSelected(1, "TZG-000101"))
    }

    @Test fun reChoosingAnUpperLevelDropsEverythingBeneathIt() {
        val deep = BrowsePath("TZS-000001").select(0, "TZC-000010").select(1, "TZG-000100")
        assertEquals(listOf("TZC-000011"), deep.select(0, "TZC-000011").selected)
        assertEquals(listOf("TZC-000010"), deep.selectAll(1).selected)
        assertEquals(emptyList(), deep.selectAll(0).selected)
        assertEquals("TZS-000001", deep.selectAll(0).current)
    }

    @Test fun anInitialSubcategoryIsJustAOneStepPath() {
        val p = BrowsePath("TZC-000010", listOf("TZG-000100"))
        assertEquals("TZG-000100", p.current)
        assertEquals(listOf("TZC-000010", "TZG-000100"), p.levels)
    }

    // ---- search and the shop tab --------------------------------------------------------------------------------------

    @Test fun searchIsAvailableInBothModes() {
        assertEquals(SearchSurface.Available, searchSurface(CatalogCapabilities.REMOTE))
        assertEquals(SearchSurface.Available, searchSurface(CatalogCapabilities.MOCK))
        assertEquals(SearchSurface.Available, searchSurface(CatalogCapabilities.forMode(CatalogMode.REMOTE)))
    }

    @Test fun theSearchCopySaysProductsOnlyAndPointsAtTheShop() {
        assertTrue("products only" in ShopCopy.SEARCH_START_BODY.lowercase())
        assertFalse("isn't available" in (ShopCopy.SEARCH_START_TITLE + ShopCopy.SEARCH_START_BODY + ShopCopy.NO_RESULTS_BODY).lowercase())
        assertEquals("Browse the Shop", ShopCopy.BACK_TO_SHOP)
    }

    @Test fun theShopTabIsTheSecondTabInRemoteMode() {
        assertEquals(HomeTab.SHOP, visibleHomeTabs(CatalogCapabilities.REMOTE)[1])
    }

    @Test fun theGridsNeverCollapseToOneColumnAt320dp() {
        // Products: 320dp − 2×12dp edge − 8dp gap = 288dp of cells, two 140dp cards fit.
        assertTrue(2 * PRODUCT_GRID_MIN_CELL_DP <= 320 - 24 - 8)
        // Categories: two 96dp tiles at 320dp (16dp gutters, 12dp gap), three from 360dp up.
        assertTrue(2 * CATEGORY_GRID_MIN_CELL_DP <= 320 - 32 - 12)
        assertTrue(3 * CATEGORY_GRID_MIN_CELL_DP <= 360 - 32 - 24)
    }

    @Test fun productionOrderingIsOn() {
        assertTrue(CatalogCapabilities.REMOTE.orderIntegration)
    }
}
