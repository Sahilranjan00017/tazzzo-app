package com.tazzzo.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tazzzo.app.data.catalog.CatalogNode
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.NodesState
import com.tazzzo.app.data.catalog.PagedState
import com.tazzzo.app.data.catalog.StockState
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.theme.TazzzoTheme
import com.tazzzo.app.ui.home.FloatingNavBar
import com.tazzzo.app.ui.home.HomeActions
import com.tazzzo.app.ui.home.HomeHeaderData
import com.tazzzo.app.ui.home.HomeScreenLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * UI-02 visual evidence: the Home LAYOUT with sample catalogue state (five root categories, a four-product first page,
 * a 3-item cart badge) because no non-prod backend is reachable from the emulator. Everything rendered is the real
 * composable; only the state is sample data, and the capture is labelled as such in the review sheet.
 */
@RunWith(AndroidJUnit4::class)
class HomeUi02EvidenceTest {
    @get:Rule val rule = createComposeRule()

    private fun product(sku: String, name: String, price: Long, mrp: Long?, off: Int?) = CatalogProduct(
        skuId = sku, productId = "prod_$sku", name = name, brandCode = null, thumbnailUrl = null,
        sellingPrice = Money.ofPaise(price), mrp = mrp?.let { Money.ofPaise(it) }, discountPercent = off, discountAmount = null,
        verticalId = null, stockState = StockState.IN_STOCK, lowStockRemaining = null, maxOrderQuantity = 10, minimumOrderQuantity = 1,
        serviceable = true, buyable = true
    )

    @Test fun home_layout_with_sample_catalogue() {
        TestState.resetKeepRemote()
        com.tazzzo.app.data.auth.AndroidAppContext.init(ApplicationProvider.getApplicationContext<android.content.Context>())
        val roots = NodesState.Loaded(listOf(CatalogNode("n1", "Staples"), CatalogNode("n2", "Fresh"), CatalogNode("n3", "Meat & Eggs"), CatalogNode("n4", "Dairy"), CatalogNode("n5", "Snacks")))
        val rail = PagedState.Content(listOf(
            product("TZP-1", "Whole Wheat Atta", 21_000, 24_000, 12), product("TZP-2", "Farm Fresh Tomato", 2_400, 3_000, 20),
            product("TZP-3", "Chicken Curry Cut", 17_900, 22_000, 19), product("TZP-4", "Farm Fresh Eggs", 7_200, 8_000, 10)
        ), hasMore = false)
        rule.setContent {
            val app = remember { TazzzoAppState() }
            CompositionLocalProvider(LocalAppState provides app) {
                TazzzoTheme {
                    Box(Modifier.fillMaxSize()) {
                        HomeScreenLayout(
                            header = HomeHeaderData("Ejipura", "Delivering to 560047", 3), root = roots, rail = rail,
                            actions = HomeActions({}, {}, {}, {}, {}, {}, {})
                        )
                        FloatingNavBar(Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
        rule.waitForIdle(); Thread.sleep(800)
        rule.onNodeWithContentDescription("Cart, 3 items").assertIsDisplayed()
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "evidence").apply { mkdirs() }
        File(dir, "ui02_home_layout.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
