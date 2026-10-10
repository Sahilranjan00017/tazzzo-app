package com.tazzzo.app.order

import com.tazzzo.app.checkout.bindingOf
import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.data.checkout.CheckoutCopy
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.model.PayableMoney
import com.tazzzo.app.data.order.CustomerOrderStatus
import com.tazzzo.app.data.order.OrderFailure
import com.tazzzo.app.data.order.OrderPaymentCondition
import com.tazzzo.app.data.order.OrderState
import com.tazzzo.app.data.order.view
import com.tazzzo.app.ui.order.OrderCopy
import com.tazzzo.app.ui.order.amountHeadline
import com.tazzzo.app.ui.order.confirmedOrder
import com.tazzzo.app.ui.order.label
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** UI-06: the presentation bindings of the order surfaces over the unchanged order domain. */
class OrderUiModelTest {

    // ---- confirmation gate ------------------------------------------------------------------------------------------------

    @Test fun theConfirmationExistsOnlyForAConfirmedOrderHeldByTheStore() {
        val o = orderOf()
        assertEquals(o, OrderState.Placed(o).confirmedOrder())
        for (s in listOf(OrderState.Idle, OrderState.Placing, OrderState.SignedOut, OrderState.Ambiguous(OrderFailure.Network), OrderState.Ambiguous(OrderFailure.Timeout), OrderState.Failed(OrderFailure.PayableChanged), OrderState.Failed(OrderFailure.PriceChanged)))
            assertNull(s.confirmedOrder(), s.toString())
    }

    @Test fun aReplayedOrDuplicateKeyWinnerIsWhateverTheStoreHoldsNotALocalModel() {
        // The screen takes `OrderState.Placed.order` (and `OrderStore.recent`) verbatim; nothing is rebuilt from the cart or quote.
        val winner = orderOf(id = "ORD_winner01", subtotal = 4_950)
        assertEquals("ORD_winner01", OrderState.Placed(winner).confirmedOrder()!!.orderId)
        assertEquals("₹49.50", OrderState.Placed(winner).confirmedOrder()!!.view().headlineMoney.value)
    }

    // ---- id and status -----------------------------------------------------------------------------------------------------

    @Test fun theOrderIdShownIsTheBackendsPublicOrderIdOnly() {
        val v = orderOf(id = "ORD_abc123xyz").view()
        assertEquals("ORD_abc123xyz", v.orderId)
        assertFalse(v.orderId.startsWith("CHKQ_")); assertFalse(v.orderId.contains("quote", ignoreCase = true))
    }

    @Test fun statusLabelsExistOnlyForTheContractsCustomerVisibleStatuses() {
        assertEquals("Confirmed", CustomerOrderStatus.CONFIRMED.label())
        assertEquals("Out for delivery", CustomerOrderStatus.OUT_FOR_DELIVERY.label())
        assertEquals("Delivered", CustomerOrderStatus.DELIVERED.label())
        assertEquals("Cancelled", CustomerOrderStatus.CANCELLED.label())
        assertEquals("Received", CustomerOrderStatus.UNRECOGNIZED.label())
        assertEquals(5, CustomerOrderStatus.entries.size)
        // Steps the backend does not have are never invented.
        for (fake in listOf("Packed", "On the way", "Shipped", "Preparing")) assertFalse(CustomerOrderStatus.entries.any { it.label() == fake })
    }

    // ---- money -------------------------------------------------------------------------------------------------------------

    @Test fun codNeverReadsPaid() {
        for (o in listOf(orderOf(), orderOf(money = bindingOf(Money.ofPaise(9_900), 9_900)), orderOf(money = null))) {
            val v = o.view(); val a = o.amountHeadline()
            val all = (listOfNotNull(v.title, v.paymentLine, v.dueLine, a.amount, a.caption) + v.moneyLines.map { it.label }).joinToString(" ").lowercase()
            assertFalse("paid" in all, all); assertFalse("payment successful" in all)
        }
    }

    @Test fun positivePayableSaysDueOnDelivery() {
        val a = orderOf(subtotal = 9_900).amountHeadline()
        assertEquals("₹99", a.amount); assertEquals("due on delivery", a.caption)
        assertEquals("₹99 due on delivery", orderOf(subtotal = 9_900).view().dueLine)
    }

    @Test fun zeroPayableIsANormalSuccessState() {
        val a = orderOf(money = bindingOf(Money.ofPaise(9_900), 9_900)).amountHeadline()
        assertEquals("₹0", a.amount); assertEquals(CheckoutCopy.NOTHING_DUE, a.caption)
        assertFalse(a.isUnavailable)
    }

    @Test fun aLegacyOrderWithoutMoneyIsNeverZeroAndNeverInferred() {
        val legacy = orderOf(money = null)
        val a = legacy.amountHeadline()
        assertTrue(a.isUnavailable); assertNull(a.amount); assertEquals(OrderCopy.AMOUNT_UNAVAILABLE, a.caption)
        assertEquals(listOf("Item subtotal"), legacy.view().moneyLines.map { it.label })   // subtotal alone, no amount due derived
        assertEquals("Payment due on delivery", legacy.view().dueLine)
    }

    @Test fun theMoneySummaryIsThePersistedSnapshotWithTheDiscountRowOnlyWhenPositive() {
        val withDiscount = orderOf(subtotal = 10_000, money = PayableMoney.fromPaise(10_000, 1_500, 8_500)!!).view()
        assertEquals(listOf("Item subtotal" to "₹100", "Benefit discount" to "-₹15", "Amount due" to "₹85"), withDiscount.moneyLines.map { it.label to it.value })
        val noDiscount = orderOf(subtotal = 10_000).view()
        assertEquals(listOf("Item subtotal", "Amount due"), noDiscount.moneyLines.map { it.label })
        for (l in withDiscount.moneyLines) for (w in listOf("fee", "tax", "cod charge", "coupon", "coin", "wallet")) assertFalse(w in l.label.lowercase())
    }

    @Test fun orderItemsAndMoneyComeFromThePersistedSnapshotNotTheCatalogue() {
        // The view is a pure function of CustomerOrder: there is no catalogue, cart or price input anywhere in it.
        val o = orderOf(subtotal = 9_900)
        val v = o.view()
        assertEquals(listOf("Atta 1kg"), v.lines.map { it.title })
        assertEquals("2 × ₹49.50" , "${v.lines[0].quantity} × ${v.lines[0].unitPriceLabel}")
        assertEquals("₹99", v.lines[0].lineTotalLabel)
        assertEquals(o.money!!.payable.format(), v.headlineMoney.value)
    }

    @Test fun anUnrecognizedPaymentConditionShowsTheAmountNeutrallyAndNoDueLine() {
        val o = orderOf(condition = OrderPaymentCondition.UNRECOGNIZED)
        assertNull(o.view().dueLine)
        assertEquals("Amount", o.amountHeadline().caption)
    }

    // ---- Orders tab truth ------------------------------------------------------------------------------------------------------

    @Test fun remoteHasRealOrderHistoryAndAnEmptyHistorySaysNoOrdersYet() {
        assertTrue(CatalogCapabilities.REMOTE.orderHistoryIntegration)
        assertEquals("No orders yet", OrderCopy.NO_ORDERS_TITLE)
        assertEquals("Start shopping", OrderCopy.START_SHOPPING)
    }

    @Test fun noCopyPromisesAnEtaOrADeliveryTime() {
        val all = listOf(OrderCopy.CONFIRMATION_TITLE, OrderCopy.CONFIRMATION_SUPPORT, OrderCopy.SIGNED_OUT_BODY, OrderCopy.NO_ORDERS_BODY, orderOf().view().title).joinToString(" ").lowercase()
        for (w in listOf("min", "arriving", "eta", "today", "tomorrow", "slot")) assertFalse(w in all, w)
        assertNotNull(OrderCopy.CONTINUE_SHOPPING)
    }

    @Test fun productionOrderingIsOn() { assertTrue(CatalogCapabilities.REMOTE.orderIntegration) }
}
