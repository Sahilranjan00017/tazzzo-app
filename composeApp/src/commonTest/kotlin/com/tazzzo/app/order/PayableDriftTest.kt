package com.tazzzo.app.order

import com.tazzzo.app.checkout.FakeCartAccess
import com.tazzzo.app.checkout.bindingOf
import com.tazzzo.app.data.checkout.payableChangeNotice
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.order.CustomerOrderStatus
import com.tazzzo.app.data.order.OrderState
import com.tazzzo.app.data.order.OrderStore
import com.tazzzo.app.data.order.view
import com.tazzzo.app.ui.order.amountHeadline
import com.tazzzo.app.ui.order.opensConfirmation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The backend's quote `moneyPreview` is ADVISORY: Order placement computes its own authoritative money and may differ (placed
 * with 200 — not an error, the order's money holds). This replaces the old "keep ordering gated until the payable is binding"
 * guard: ordering is on, and the app must (1) always display the ORDER's money and (2) say so when it differs from the total
 * the customer reviewed — "Your total changed from ₹X to ₹Y" (the storefront's order-total-changed wording).
 *
 * Mutation notes: displaying the quote's amount fails [theOrdersMoneyIsAlwaysTheOneDisplayed]; not recording the reviewed
 * payable fails [aDifferentOrderTotalIsRememberedForTheNotice]; showing a notice for equal totals fails [equalTotalsShowNoNotice];
 * routing a cancelled reconciled order to "Order placed" fails [anAlreadyCancelledOrderOpensItsDetailNotTheConfirmation].
 */
class PayableDriftTest {
    private fun TestScope.store(server: FakeOrderSource, quotePaise: Long = 9_500) =
        OrderStore(backgroundScope, server, FakeQuoteAccess(readyState(money = bindingOf(Money.ofPaise(quotePaise)))), FakeCartAccess(), FakePending(), { true }, { true }, {})

    @Test fun aDifferentOrderTotalIsRememberedForTheNotice() = runTest {
        val server = FakeOrderSource().apply { orderPaise = 10_000 }
        val s = store(server, quotePaise = 9_500)
        s.place(); runCurrent()
        val placed = assertIs<OrderState.Placed>(s.state.value).order
        assertEquals(Money.ofPaise(9_500), s.reviewedPayable(placed.orderId))
        assertEquals(
            "Your total changed from ₹95 to ₹100. Benefits and prices are checked again when an order is placed, and this is the amount of your order.",
            payableChangeNotice(s.reviewedPayable(placed.orderId), placed.money?.payable)
        )
    }

    @Test fun equalTotalsShowNoNotice() = runTest {
        val server = FakeOrderSource().apply { orderPaise = 9_900 }
        val s = store(server, quotePaise = 9_900)
        s.place(); runCurrent()
        val placed = assertIs<OrderState.Placed>(s.state.value).order
        assertNull(s.reviewedPayable(placed.orderId))
        assertNull(payableChangeNotice(Money.ofPaise(9_900), placed.money?.payable))
        assertNull(payableChangeNotice(null, Money.ofPaise(10_000)))             // reviewed total unknown (e.g. after a restart)
        assertNull(payableChangeNotice(Money.ofPaise(9_500), null))               // a legacy order without money: nothing to compare
    }

    @Test fun theOrdersMoneyIsAlwaysTheOneDisplayed() {
        val order = orderOf(subtotal = 10_000)                                    // reviewed ₹95, order ₹100
        assertEquals("₹100", order.amountHeadline().amount)
        assertEquals("₹100", order.view().headlineMoney.value)
        assertEquals("₹100 due on delivery", order.view().dueLine)
        assertFalse("₹95" in order.view().toString())
    }

    @Test fun signOutForgetsTheReviewedTotals() = runTest {
        val server = FakeOrderSource().apply { orderPaise = 10_000 }
        val s = store(server)
        s.place(); runCurrent()
        val id = assertIs<OrderState.Placed>(s.state.value).order.orderId
        s.signOut(); runCurrent()
        assertNull(s.reviewedPayable(id))
    }

    @Test fun anAlreadyCancelledOrderOpensItsDetailNotTheConfirmation() {
        assertTrue(orderOf().opensConfirmation())
        assertFalse(orderOf().copy(status = CustomerOrderStatus.CANCELLED).opensConfirmation())
        assertTrue(orderOf().copy(status = CustomerOrderStatus.OUT_FOR_DELIVERY).opensConfirmation())
    }
}
