package com.tazzzo.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.model.PayableMoney
import com.tazzzo.app.data.order.CustomerOrder
import com.tazzzo.app.data.order.CustomerOrderItem
import com.tazzzo.app.data.order.CustomerOrderStatus
import com.tazzzo.app.data.order.CustomerPaymentMethod
import com.tazzzo.app.data.order.OrderDeliveryAddress
import com.tazzzo.app.data.order.OrderPaymentCondition
import com.tazzzo.app.theme.TazzzoTheme
import com.tazzzo.app.ui.home.FloatingNavBar
import com.tazzzo.app.ui.order.OrderActions
import com.tazzzo.app.ui.order.OrderConfirmationLayout
import com.tazzzo.app.ui.order.OrderDetailLayout
import com.tazzzo.app.ui.order.OrderLoad
import com.tazzzo.app.ui.order.OrdersActions
import com.tazzzo.app.ui.order.OrdersTabLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * UI-06 visual evidence: order confirmation, Orders tab and order detail LAYOUTS with an explicitly SAMPLE order (no
 * non-prod backend is reachable; no real order was placed and none is claimed). Every composable is the production one;
 * only the `CustomerOrder` is constructed, exactly as the domain would hold it after placement.
 */
@RunWith(AndroidJUnit4::class)
class OrdersUi06EvidenceTest {
    @get:Rule val rule = createComposeRule()

    private fun rs(r: Long) = Money.ofRupees(r)
    private fun item(sku: String, title: String, qty: Int, unit: Long) = CustomerOrderItem(sku, title, null, qty, rs(unit), rs(unit * qty))
    private val items = listOf(item("TZP-1", "Whole Wheat Atta 5 kg", 2, 210), item("TZP-2", "Farm Fresh Tomato", 1, 24), item("TZP-3", "Organic Sona Masoori Rice Premium 5 kg Pack", 1, 489))
    private val address = OrderDeliveryAddress("HOME", "Asha Rao", "+91 98765 43210", "22, 14th Main, 5th Cross", "Ejipura", null, "Bengaluru", "Karnataka", "560047")
    private fun order(id: String, money: PayableMoney?, status: CustomerOrderStatus = CustomerOrderStatus.CONFIRMED) = CustomerOrder(
        id, status, CustomerPaymentMethod.COD, OrderPaymentCondition.COD_DUE, items, 4, rs(933), "INR", money, address, 1L, 2L
    )
    private val positive = order("ORD_9f3kq2m7xv1", PayableMoney.fromPaise(93_300, 9_300, 84_000)!!)
    private val zero = order("ORD_zero0001abc", PayableMoney.fromPaise(93_300, 93_300, 0)!!)
    private val legacy = order("ORD_legacy001xyz_longer_identifier_sample", null)
    private val orderActions = OrderActions({}, {}, {}, {})
    private val ordersActions = OrdersActions({}, {}, {})

    private fun content(tab: Boolean = false, body: @Composable () -> Unit) {
        TestState.resetKeepRemote()
        com.tazzzo.app.data.auth.AndroidAppContext.init(ApplicationProvider.getApplicationContext<android.content.Context>())
        rule.setContent {
            val app = remember { TazzzoAppState().also { it.homeTab = HomeTab.ORDERS } }
            CompositionLocalProvider(LocalAppState provides app) { TazzzoTheme { Box(Modifier.fillMaxSize()) { body(); if (tab) FloatingNavBar(Modifier.align(Alignment.BottomCenter)) } } }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1_500); rule.waitForIdle(); Thread.sleep(800)
    }

    private fun snapshot(name: String) {
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "evidence").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun confirmation_payable_positive() {
        content { OrderConfirmationLayout(positive.orderId, OrderLoad.Loaded(positive), orderActions) }
        rule.onNodeWithText("Order placed").assertIsDisplayed()
        rule.onNodeWithTag("orderId").assertIsDisplayed()
        rule.onAllNodesWithText("₹840 due on delivery").onFirst().assertIsDisplayed()
        rule.onNodeWithTag("continueShopping").assertIsDisplayed()
        snapshot("ui06_confirmation_positive")
    }

    @Test fun confirmation_payable_zero() {
        content { OrderConfirmationLayout(zero.orderId, OrderLoad.Loaded(zero), orderActions) }
        rule.onAllNodesWithText("Nothing due on delivery").onFirst().assertIsDisplayed()
        snapshot("ui06_confirmation_zero")
    }

    @Test fun confirmation_loading_and_failed() {
        content { OrderConfirmationLayout("ORD_9f3kq2m7xv1", OrderLoad.Missing, orderActions) }
        rule.onNodeWithText("Your order is placed. We couldn't load its details right now.").assertIsDisplayed()
        snapshot("ui06_confirmation_failed")
    }

    @Test fun orders_tab_history_unavailable() {
        content(tab = true) { OrdersTabLayout(null, true, false, ordersActions) }
        rule.onNodeWithText("Order history isn't available yet").assertIsDisplayed()
        snapshot("ui06_orders_unavailable")
    }

    @Test fun orders_tab_with_session_order() {
        content(tab = true) { OrdersTabLayout(positive, true, false, ordersActions) }
        rule.onNodeWithContentDescription("Order ORD_9f3kq2m7xv1").assertIsDisplayed()
        rule.onNodeWithText("Full order history isn't available yet.").assertIsDisplayed()
        snapshot("ui06_orders_recent")
    }

    @Test fun orders_tab_real_history_empty() {
        // Only reachable once a history integration exists; rendered to show the DIFFERENT copy it would use.
        content(tab = true) { OrdersTabLayout(null, true, true, ordersActions) }
        rule.onNodeWithText("No orders yet").assertIsDisplayed()
        snapshot("ui06_orders_empty")
    }

    @Test fun detail_populated() {
        content { OrderDetailLayout(positive.orderId, OrderLoad.Loaded(positive), orderActions) }
        rule.onNodeWithText("Item subtotal").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Benefit discount").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Amount due").performScrollTo().assertIsDisplayed()
        snapshot("ui06_detail")
    }

    @Test fun detail_legacy_missing_money() {
        content { OrderDetailLayout(legacy.orderId, OrderLoad.Loaded(legacy), orderActions) }
        rule.onNodeWithTag("amountUnavailable").performScrollTo().assertIsDisplayed()
        snapshot("ui06_detail_legacy")
    }

    @Test fun detail_loading() {
        content { OrderDetailLayout("ORD_9f3kq2m7xv1", OrderLoad.Loading, orderActions) }
        rule.onNodeWithTag("orderSkeleton").assertIsDisplayed()
        snapshot("ui06_detail_loading")
    }

    @Test fun detail_failed() {
        content { OrderDetailLayout("ORD_9f3kq2m7xv1", OrderLoad.Missing, orderActions) }
        rule.onNodeWithText("We couldn't load this order").assertIsDisplayed()
        snapshot("ui06_detail_failed")
    }
}
