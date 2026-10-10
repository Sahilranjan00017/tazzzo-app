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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tazzzo.app.data.address.AddressField
import com.tazzzo.app.data.address.AddressLabel
import com.tazzzo.app.data.address.AddressServiceability
import com.tazzzo.app.data.address.BookState
import com.tazzzo.app.data.address.CustomerAddress
import com.tazzzo.app.data.address.FieldError
import com.tazzzo.app.data.cart.CartItem
import com.tazzzo.app.data.cart.CartState
import com.tazzzo.app.data.cart.LineIssue
import com.tazzzo.app.data.cart.ServerCart
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.catalog.StockState
import com.tazzzo.app.data.checkout.AddressStamp
import com.tazzzo.app.data.checkout.BenefitPreviewState
import com.tazzzo.app.data.checkout.CheckoutQuote
import com.tazzzo.app.data.checkout.CheckoutQuoteItem
import com.tazzzo.app.data.checkout.CheckoutSource
import com.tazzzo.app.data.checkout.CheckoutState
import com.tazzzo.app.data.checkout.DeliveryContent
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.model.PayableMoney
import com.tazzzo.app.data.order.OrderFailure
import com.tazzzo.app.data.order.OrderState
import com.tazzzo.app.data.order.PlaceOrderAvailability
import com.tazzzo.app.theme.TazzzoTheme
import com.tazzzo.app.ui.address.AddressFormLayout
import com.tazzzo.app.ui.address.AddressListActions
import com.tazzzo.app.ui.address.AddressListLayout
import com.tazzzo.app.ui.cart.CartActions
import com.tazzzo.app.ui.cart.CartScreenLayout
import com.tazzzo.app.ui.checkout.CheckoutActions
import com.tazzzo.app.ui.checkout.CheckoutScreenLayout
import com.tazzzo.app.ui.common.ProductImageLoader
import com.tazzzo.app.ui.common.ProductImageState
import com.tazzzo.app.ui.common.ProvideProductImageLoader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * UI-05 visual evidence: Cart, Delivery address, Add address and Checkout LAYOUTS with explicitly sample state (no
 * non-prod backend is reachable; no live order is placed or faked). Every composable is the production one; the stores
 * are not involved — the states are constructed exactly as the domain would produce them, including PAYABLE_CHANGED and
 * an unresolved placement, so the review sheet shows the real surfaces for those outcomes.
 */
@RunWith(AndroidJUnit4::class)
class CheckoutUi05EvidenceTest {
    @get:Rule val rule = createComposeRule()

    private fun rs(r: Long) = Money.ofRupees(r)
    private fun item(sku: String, title: String, qty: Int, unit: Long, mrp: Long = unit, issues: List<String> = emptyList(), url: String? = "https://media.example.test/$sku.jpg") = CartItem(
        skuId = sku, quantity = qty, addedAt = null, updatedAt = null, title = title, brandCode = null, imageUrl = url,
        unitPrice = rs(unit), mrp = rs(mrp), lineTotal = rs(unit * qty), stockState = StockState.IN_STOCK, maxOrderQuantity = 10,
        serviceable = true, buyable = issues.isEmpty(), issues = issues.map { LineIssue.of(it) }
    )
    private val lines = listOf(
        item("TZP-1", "Whole Wheat Atta 5 kg", 2, 210, 240), item("TZP-2", "Farm Fresh Tomato", 1, 24, 30),
        item("TZP-3", "Organic Sona Masoori Rice Premium 5 kg Pack", 1, 489, 560), item("TZP-4", "Chana Dal", 1, 99, 110, issues = listOf("OUT_OF_STOCK"), url = null)
    )
    private val cart = ServerCart(version = 7, items = lines, itemCount = 5, distinctItemCount = 4, subtotal = rs(210 * 2 + 24 + 489 + 99), expiresAt = null)
    private val cleanCart = cart.copy(items = lines.take(3), itemCount = 4, distinctItemCount = 3, subtotal = rs(210 * 2 + 24 + 489))

    private fun ca(id: String, label: AddressLabel, line1: String, line2: String?, isDefault: Boolean, svc: AddressServiceability = AddressServiceability.SERVICEABLE) = CustomerAddress(
        addressId = id, label = label, recipientName = "Asha Rao", recipientPhone = "+91 98765 43210", addressLine1 = line1, addressLine2 = line2,
        landmark = null, city = "Bengaluru", state = "Karnataka", postalCode = Pincode.parse("560047")!!, latitude = null, longitude = null,
        isDefault = isDefault, version = 1, serviceability = svc
    )
    private val addresses = listOf(
        ca("ADDR_1", AddressLabel.HOME, "22, 14th Main, 5th Cross", "Ejipura", true),
        ca("ADDR_2", AddressLabel.WORK, "Tower B, 9th Floor, Prestige Tech Park, Outer Ring Road", "Kadubeesanahalli", false),
        ca("ADDR_3", AddressLabel.OTHER, "Flat 4, Lakeview Residency", null, false, AddressServiceability.NOT_SERVICEABLE)
    )
    private val delivery = DeliveryContent("Asha Rao", "+91 98765 43210", "22, 14th Main, 5th Cross", "Ejipura", null, "Bengaluru", "Karnataka", Pincode.parse("560047")!!)
    private fun quote(money: PayableMoney?) = CheckoutQuote(
        quoteId = "CHKQ_1abcdef", cartVersion = 7, addressId = "ADDR_1",
        items = cleanCart.items.map { CheckoutQuoteItem(it.skuId, it.quantity, it.unitPrice!!, it.lineTotal!!) },
        itemCount = 4, distinctItemCount = 3, subtotal = cleanCart.subtotal, currency = "INR", createdAtMillis = 0, expiresAtMillis = 300_000,
        benefit = BenefitPreviewState.NotApplied, money = money, requestId = "req_1"
    )
    private val ready = CheckoutState.Ready(quote(PayableMoney.fromPaise(93_300, 9_300, 84_000)!!), CheckoutSource(7, AddressStamp("ADDR_1", 1, delivery)))
    private val readyZero = CheckoutState.Ready(quote(PayableMoney.fromPaise(93_300, 93_300, 0)!!), CheckoutSource(7, AddressStamp("ADDR_1", 1, delivery)))
    private val checkoutActions = CheckoutActions({}, {}, {})
    private val cartActions = CartActions({}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {})
    private val listActions = AddressListActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    /** Stand-in product photos (tinted tiles), served through the real pipeline seam. */
    private val sampleLoader = object : ProductImageLoader {
        override suspend fun load(url: String): ProductImageState {
            val bmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888); val c = Canvas(bmp)
            val hues = listOf(Color.rgb(222, 199, 150), Color.rgb(196, 214, 180), Color.rgb(230, 190, 170))
            c.drawColor(hues[Math.floorMod(url.hashCode(), hues.size)])
            c.drawOval(45f, 40f, 160f, 165f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 20, 53, 40) })
            return ProductImageState.Ready(bmp.asImageBitmap())
        }
    }

    private fun content(body: @Composable () -> Unit) {
        TestState.resetKeepRemote()
        com.tazzzo.app.data.auth.AndroidAppContext.init(ApplicationProvider.getApplicationContext<android.content.Context>())
        rule.setContent {
            val app = remember { TazzzoAppState() }
            CompositionLocalProvider(LocalAppState provides app) { ProvideProductImageLoader(sampleLoader) { TazzzoTheme { Box(Modifier.fillMaxSize()) { body() } } } }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(2_000); rule.waitForIdle(); Thread.sleep(900)
    }

    private fun snapshot(name: String) {
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "evidence").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun cart_populated() {
        content { CartScreenLayout(CartState.Loaded(cart), emptyMap(), false, false, true, cartActions) }
        rule.onNodeWithTag("cartLines").assertIsDisplayed()
        rule.onNodeWithContentDescription("Increase quantity of Whole Wheat Atta 5 kg").assertIsDisplayed()
        rule.onNodeWithContentDescription("Remove Chana Dal").assertIsDisplayed()
        rule.onNodeWithText("Resolve items to continue").assertIsDisplayed()
        snapshot("ui05_cart_populated")
    }

    @Test fun cart_clean_checkout_enabled() {
        content { CartScreenLayout(CartState.Loaded(cleanCart), emptyMap(), false, false, true, cartActions) }
        rule.onNodeWithText("Review checkout").assertIsDisplayed()
        snapshot("ui05_cart_clean")
    }

    @Test fun cart_empty() {
        content { CartScreenLayout(CartState.Loaded(ServerCart.EMPTY), emptyMap(), false, false, true, cartActions) }
        rule.onNodeWithText("Your cart is empty").assertIsDisplayed()
        rule.onNodeWithContentDescription("Start shopping").assertIsDisplayed()
        snapshot("ui05_cart_empty")
    }

    @Test fun cart_loading() {
        content { CartScreenLayout(CartState.Loading, emptyMap(), false, false, true, cartActions) }
        rule.onNodeWithTag("cartSkeleton").assertIsDisplayed()
        snapshot("ui05_cart_loading")
    }

    @Test fun address_list() {
        content { AddressListLayout(true, BookState.Loaded(addresses), "ADDR_1", false, null, null, listActions) }
        rule.onNodeWithContentDescription("Home address, delivering here").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add new address").assertIsDisplayed()
        snapshot("ui05_address_list")
    }

    @Test fun address_list_at_limit() {
        val ten = (1..10).map { ca("ADDR_$it", AddressLabel.entries[it % 3], "Address $it, Some Road", null, it == 1) }
        content { AddressListLayout(true, BookState.Loaded(ten), "ADDR_1", false, null, null, listActions) }
        rule.onNodeWithTag("addAddress").assertIsDisplayed()
        snapshot("ui05_address_limit")
    }

    @Test fun add_address_form() {
        content {
            AddressFormLayout(false, AddressLabel.HOME, "Asha Rao", "9876543210", "22, 14th Main", "", "", "Bengaluru", "Karnataka", "56004",
                mapOf(AddressField.POSTAL_CODE to FieldError.Invalid), null, false, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
        rule.onNodeWithTag("saveAddress").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Please check this").assertIsDisplayed()
        snapshot("ui05_address_form")
    }

    @Test fun checkout_review_payable_positive() {
        content { CheckoutScreenLayout(ready, OrderState.Idle, delivery, cleanCart, PlaceOrderAvailability.Available, checkoutActions, "Expires in 4:32") }
        rule.onNodeWithText("Item subtotal").performScrollTo().assertIsDisplayed(); rule.onNodeWithText("Benefit discount").performScrollTo().assertIsDisplayed(); rule.onNodeWithText("Total").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Final amount is confirmed when you place your order.").performScrollTo().assertIsDisplayed()   // the advisory preview note
        rule.onNodeWithText("Total for review").assertIsDisplayed()
        snapshot("ui05_checkout_review")
    }

    @Test fun checkout_review_payable_zero() {
        content { CheckoutScreenLayout(readyZero, OrderState.Idle, delivery, cleanCart, PlaceOrderAvailability.Available, checkoutActions) }
        rule.onNodeWithText("Total for review").assertIsDisplayed()
        rule.onAllNodesWithText("₹0").onLast().assertIsDisplayed()   // the sticky bar headline
        snapshot("ui05_checkout_zero")
    }

    @Test fun checkout_payable_changed() {
        content { CheckoutScreenLayout(ready, OrderState.Failed(OrderFailure.PayableChanged), delivery, cleanCart, PlaceOrderAvailability.NoReadyQuote, checkoutActions) }
        rule.onNodeWithText("Your order amount changed.").assertIsDisplayed()
        rule.onNodeWithText("Review checkout again before placing your order.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Review checkout").assertIsDisplayed()
        snapshot("ui05_checkout_payable_changed")
    }

    @Test fun checkout_ambiguous_placement() {
        content { CheckoutScreenLayout(ready, OrderState.Ambiguous(OrderFailure.Network), delivery, cleanCart, PlaceOrderAvailability.NeedsCheck, checkoutActions) }
        rule.onNodeWithText("We couldn't confirm your order").assertIsDisplayed()
        rule.onNodeWithContentDescription("Check order").assertIsDisplayed()
        snapshot("ui05_checkout_ambiguous")
    }

    @Test fun checkout_placing_and_creating() {
        content { CheckoutScreenLayout(ready, OrderState.Placing, delivery, cleanCart, PlaceOrderAvailability.Placing, checkoutActions) }
        rule.onNodeWithTag("placing").assertIsDisplayed()
        snapshot("ui05_checkout_placing")
    }
}
