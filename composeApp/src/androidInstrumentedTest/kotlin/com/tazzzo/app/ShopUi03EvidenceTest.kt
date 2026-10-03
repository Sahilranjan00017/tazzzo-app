package com.tazzzo.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.CatalogNode
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.NodesState
import com.tazzzo.app.data.catalog.PagedState
import com.tazzzo.app.data.catalog.StockState
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.theme.TazzzoTheme
import com.tazzzo.app.ui.catalog.BrowseActions
import com.tazzzo.app.ui.catalog.BrowsePath
import com.tazzzo.app.ui.catalog.BrowseScreenLayout
import com.tazzzo.app.ui.catalog.ChipLevel
import com.tazzzo.app.ui.catalog.SearchScreenLayout
import com.tazzzo.app.ui.catalog.SearchSurface
import com.tazzzo.app.ui.catalog.ShopActions
import com.tazzzo.app.ui.catalog.ShopScreenLayout
import com.tazzzo.app.ui.common.ProductImageLoader
import com.tazzzo.app.ui.common.ProductImageState
import com.tazzzo.app.ui.common.ProvideProductImageLoader
import com.tazzzo.app.ui.home.FloatingNavBar
import com.tazzzo.app.ui.home.HomeActions
import com.tazzzo.app.ui.home.HomeHeaderData
import com.tazzzo.app.ui.home.HomeScreenLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * UI-03 visual evidence: Shop root, the browse/PLP screen (populated, loading, empty, failed), Search and the Home
 * rail, rendered with SAMPLE catalogue state because no non-prod backend is reachable from the emulator. Every
 * composable is the production one; only the state is sample data, and the image loader is a stand-in that paints
 * labelled placeholder "photos" so the pipeline's layout (well, fit, no jump) is visible. The review sheet is labelled
 * "Derived from Tazzzo approved reference design system".
 */
@RunWith(AndroidJUnit4::class)
class ShopUi03EvidenceTest {
    @get:Rule val rule = createComposeRule()

    private fun product(sku: String, name: String, price: Long, mrp: Long?, off: Int?, stock: StockState = StockState.IN_STOCK, low: Int? = null, url: String? = "https://media.example.test/$sku.jpg") = CatalogProduct(
        skuId = sku, productId = "TZP-${sku.hashCode().toUInt() % 100000u}", name = name, brandCode = null, thumbnailUrl = url,
        sellingPrice = Money.ofPaise(price), mrp = mrp?.let { Money.ofPaise(it) }, discountPercent = off, discountAmount = null,
        verticalId = null, stockState = stock, lowStockRemaining = low, maxOrderQuantity = 10, minimumOrderQuantity = 1,
        serviceable = true, buyable = true
    )

    private val roots = NodesState.Loaded(listOf(
        CatalogNode("TZS-000001", "Staples"), CatalogNode("TZS-000002", "Fresh Produce"), CatalogNode("TZS-000003", "Meat & Eggs"),
        CatalogNode("TZS-000004", "Dairy & Bread"), CatalogNode("TZS-000005", "Snacks"), CatalogNode("TZS-000006", "Beverages"),
        CatalogNode("TZS-000007", "Household")
    ))
    private val products = listOf(
        product("TZP-1", "Whole Wheat Atta 5 kg", 21_000, 24_000, 12), product("TZP-2", "Basmati Rice Premium", 48_900, 56_000, 13),
        product("TZP-3", "Toor Dal Unpolished", 15_900, 17_500, 9), product("TZP-4", "Sunflower Oil 1 L", 13_900, null, null),
        product("TZP-5", "Sona Masoori Rice", 29_900, 32_000, 7, StockState.LOW_STOCK, 3), product("TZP-6", "Chana Dal", 9_900, 11_000, 10, StockState.OUT_OF_STOCK, url = null)
    )
    private val level0 = ChipLevel(0, "TZS-000001", listOf(CatalogNode("TZC-000010", "Rice"), CatalogNode("TZC-000011", "Atta & Flour"), CatalogNode("TZC-000012", "Dal & Pulses"), CatalogNode("TZC-000013", "Oil & Ghee")))
    private val level1 = ChipLevel(1, "TZC-000010", listOf(CatalogNode("TZG-000100", "Basmati"), CatalogNode("TZG-000101", "Sona Masoori"), CatalogNode("TZG-000102", "Brown Rice")))
    private val browseActions = BrowseActions({}, { _, _ -> }, {}, {}, {}, {})

    /** Paints a labelled stand-in "photo" per URL: a tinted ground with a produce-like shape. Clearly not a packshot. */
    private val sampleLoader = object : ProductImageLoader {
        // No delay: a suspend delay inside a LaunchedEffect runs on the test's virtual frame clock and would never elapse.
        override suspend fun load(url: String): ProductImageState {
            android.util.Log.i("TazzzoEvidence", "sample image load $url")
            val seed = url.hashCode()
            val bmp = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val hues = listOf(Color.rgb(222, 199, 150), Color.rgb(196, 214, 180), Color.rgb(230, 190, 170), Color.rgb(205, 200, 160), Color.rgb(188, 206, 196))
            c.drawColor(hues[Math.floorMod(seed, hues.size)])
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.argb(200, 20, 53, 40); c.drawOval(70f, 60f, 240f, 250f, p)
            p.color = Color.argb(150, 244, 241, 232); c.drawCircle(120f, 110f, 26f, p)
            return ProductImageState.Ready(bmp.asImageBitmap())
        }
    }

    private fun content(tab: HomeTab = HomeTab.SHOP, nav: Boolean, body: @Composable () -> Unit) {
        TestState.resetKeepRemote()
        com.tazzzo.app.data.auth.AndroidAppContext.init(ApplicationProvider.getApplicationContext<android.content.Context>())
        rule.setContent {
            val app = remember { TazzzoAppState().also { it.homeTab = tab } }
            CompositionLocalProvider(LocalAppState provides app) {
                ProvideProductImageLoader(sampleLoader) {
                    TazzzoTheme {
                        Box(Modifier.fillMaxSize()) {
                            body()
                            if (nav) FloatingNavBar(Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(2_000); rule.waitForIdle(); Thread.sleep(900)
    }

    private fun snapshot(name: String) {
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "evidence").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun shop_root() {
        content(nav = true) { ShopScreenLayout(roots, "Delivering to 560047", ShopActions({}, {}, {})) }
        rule.onNodeWithContentDescription("Staples").assertIsDisplayed()
        rule.onNodeWithTag("shopRoot").assertIsDisplayed()
        snapshot("ui03_shop_root")
    }

    @Test fun shop_root_loading_and_failed() {
        content(nav = true) { ShopScreenLayout(NodesState.Loading, null, ShopActions({}, {}, {})) }
        snapshot("ui03_shop_loading")
    }

    @Test fun browse_populated() {
        content(nav = false) {
            BrowseScreenLayout("Staples", listOf("Rice"), listOf(level0, level1), BrowsePath("TZS-000001", listOf("TZC-000010")),
                PagedState.Content(products, hasMore = false), browseActions)
        }
        rule.onNodeWithTag("remoteProductGrid").assertIsDisplayed()
        rule.onNodeWithTag("breadcrumb").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add Whole Wheat Atta 5 kg to cart").assertIsDisplayed()
        rule.onNodeWithText("12% off").assertIsDisplayed()
        // One merged node per card: the photo's description folds into the card, so a screen reader hears the product once.
        rule.onAllNodesWithContentDescription("Whole Wheat Atta 5 kg").assertCountEquals(1)
        snapshot("ui03_plp_populated")
    }

    @Test fun browse_loading() {
        content(nav = false) { BrowseScreenLayout("Staples", emptyList(), listOf(level0), BrowsePath("TZS-000001"), PagedState.LoadingFirst, browseActions) }
        rule.onNodeWithTag("productGridSkeleton").assertIsDisplayed()
        snapshot("ui03_plp_loading")
    }

    @Test fun browse_empty() {
        content(nav = false) { BrowseScreenLayout("Staples", listOf("Oil & Ghee"), listOf(level0), BrowsePath("TZS-000001", listOf("TZC-000013")), PagedState.Empty, browseActions) }
        rule.onNodeWithText("Nothing here yet").assertIsDisplayed()
        snapshot("ui03_plp_empty")
    }

    @Test fun browse_failed() {
        content(nav = false) { BrowseScreenLayout("Staples", emptyList(), emptyList(), BrowsePath("TZS-000001"), PagedState.FirstPageFailed(CatalogFailure.Network), browseActions) }
        rule.onNodeWithText("No internet connection").assertIsDisplayed()
        snapshot("ui03_plp_failed")
    }

    @Test fun search_unavailable() {
        content(nav = false) { SearchScreenLayout(SearchSurface.Unavailable, onBack = {}, onShop = {}) }
        rule.onNodeWithText("Search isn't available yet").assertIsDisplayed()
        snapshot("ui03_search")
    }

    @Test fun home_rail_uses_the_shared_images() {
        content(tab = HomeTab.HOME, nav = true) {
            HomeScreenLayout(HomeHeaderData("Ejipura", "Delivering to 560047", 3), roots, PagedState.Content(products.take(4), hasMore = false), HomeActions({}, {}, {}, {}, {}, {}, {}))
        }
        rule.onNodeWithContentDescription("Cart, 3 items").assertIsDisplayed()
        snapshot("ui03_home_images")
    }
}
