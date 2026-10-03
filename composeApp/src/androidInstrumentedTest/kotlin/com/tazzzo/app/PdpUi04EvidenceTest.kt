package com.tazzzo.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.CatalogProductDetail
import com.tazzzo.app.data.catalog.ImageRole
import com.tazzzo.app.data.catalog.PdpState
import com.tazzzo.app.data.catalog.ProductAttribute
import com.tazzzo.app.data.catalog.ProductImage
import com.tazzzo.app.data.catalog.StockState
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.theme.TazzzoTheme
import com.tazzzo.app.ui.catalog.PdpActions
import com.tazzzo.app.ui.catalog.PdpScreenLayout
import com.tazzzo.app.ui.catalog.pdpFacts
import com.tazzzo.app.ui.common.ProductImageLoader
import com.tazzzo.app.ui.common.ProductImageState
import com.tazzzo.app.ui.common.ProvideProductImageLoader
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * UI-04 visual evidence: the PDP LAYOUT with a sample product (populated with a three-image gallery, loading,
 * not-found, network failure), because no non-prod backend is reachable from the emulator. The composable is the
 * production one; the product and the image source are sample data. The populated hero uses a bundled test-only
 * produce photo served through the real pipeline seam so the composition can be compared with `Veg Page.jpeg`; it is
 * NOT a shipped asset.
 */
@RunWith(AndroidJUnit4::class)
class PdpUi04EvidenceTest {
    @get:Rule val rule = createComposeRule()

    private val product = CatalogProduct(
        skuId = "TZP-77", productId = "TZP-77", name = "Hybrid Tomato", brandCode = null, thumbnailUrl = "https://media.example.test/tomato.jpg",
        sellingPrice = Money.ofPaise(2_400), mrp = Money.ofPaise(3_000), discountPercent = 20, discountAmount = Money.ofPaise(600),
        verticalId = "TZV-000225", stockState = StockState.IN_STOCK, lowStockRemaining = null, maxOrderQuantity = 10, minimumOrderQuantity = 1,
        serviceable = true, buyable = true
    )
    private val detail = CatalogProductDetail(
        product,
        gallery = listOf(
            ProductImage("https://media.example.test/tomato-1.jpg", ImageRole.PRIMARY, 0, null, 1200, 1200),
            ProductImage("https://media.example.test/tomato-2.jpg", ImageRole.GALLERY, 1, null, 1200, 1200),
            ProductImage("https://media.example.test/tomato-3.jpg", ImageRole.GALLERY, 2, null, 1200, 1200)
        ),
        attributes = listOf(ProductAttribute("origin", "Origin", JsonPrimitive("Nashik, Maharashtra"), null), ProductAttribute("storage", "Storage", JsonPrimitive("Keep refrigerated"), null)),
        resolvedReleaseId = "rel_1"
    )
    private val actions = PdpActions({}, {}, {})

    /** The sample hero source: a test asset decoded on demand (not in the app bundle). */
    private val sampleLoader = object : ProductImageLoader {
        override suspend fun load(url: String): ProductImageState {
            // Local, untracked sample asset (a crop of the supplied reference, never committed or shipped); without it the
            // hero shows the neutral plate, which is itself a valid state to review.
            val bmp = try {
                InstrumentationRegistry.getInstrumentation().context.assets.open("pdp_sample_tomato.jpg").use { BitmapFactory.decodeStream(it) }
            } catch (t: Throwable) { null } ?: return ProductImageState.Unavailable
            return ProductImageState.Ready(bmp.asImageBitmap())
        }
    }

    private fun content(body: @Composable () -> Unit) {
        TestState.resetKeepRemote()
        com.tazzzo.app.data.auth.AndroidAppContext.init(ApplicationProvider.getApplicationContext<android.content.Context>())
        rule.setContent {
            val app = remember { TazzzoAppState() }
            CompositionLocalProvider(LocalAppState provides app) {
                ProvideProductImageLoader(sampleLoader) { TazzzoTheme { Box(Modifier.fillMaxSize()) { body() } } }
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(2_000); rule.waitForIdle(); Thread.sleep(900)
    }

    private fun snapshot(name: String) {
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "evidence").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun pdp_populated() {
        content { PdpScreenLayout(PdpState.Content(detail), detail.pdpFacts { if (it == "TZV-000225") "Fresh Vegetables" else null }, actions) }
        rule.onNodeWithTag("pdpHero").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Hybrid Tomato").onFirst().assertIsDisplayed()   // the title, and the hero photo
        rule.onNodeWithText("FRESH VEGETABLES").assertIsDisplayed()
        rule.onNodeWithText("20% OFF").assertIsDisplayed()
        rule.onNodeWithContentDescription("Image 1 of 3").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add Hybrid Tomato to cart").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").assertIsDisplayed()
        snapshot("ui04_pdp_populated")
    }

    @Test fun pdp_populated_without_image_uses_the_plate() {
        val bare = CatalogProductDetail(product.copy(thumbnailUrl = null, name = "Organic Sona Masoori Rice Premium 5 kg Pack"), emptyList(), emptyList(), "rel_1")
        content { PdpScreenLayout(PdpState.Content(bare), bare.pdpFacts(), actions) }
        rule.onNodeWithTag("purchaseBar").assertIsDisplayed()
        snapshot("ui04_pdp_fallback")
    }

    @Test fun pdp_loading() {
        content { PdpScreenLayout(PdpState.Loading, null, actions) }
        rule.onNodeWithTag("pdpSkeleton").assertIsDisplayed()
        snapshot("ui04_pdp_loading")
    }

    @Test fun pdp_not_found() {
        content { PdpScreenLayout(PdpState.NotFound, null, actions) }
        rule.onNodeWithText("This product isn't available right now.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back to Shop").assertIsDisplayed()
        snapshot("ui04_pdp_not_found")
    }

    @Test fun pdp_failed() {
        content { PdpScreenLayout(PdpState.Failed(CatalogFailure.Network), null, actions) }
        rule.onNodeWithText("No internet connection").assertIsDisplayed()
        snapshot("ui04_pdp_failed")
    }
}
