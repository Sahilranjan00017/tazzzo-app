package com.tazzzo.app.order

import com.tazzzo.app.checkout.stampOf
import com.tazzzo.app.data.checkout.CheckoutAction
import com.tazzzo.app.data.checkout.CheckoutCopy
import com.tazzzo.app.data.checkout.CheckoutFailure
import com.tazzzo.app.data.checkout.CheckoutState
import com.tazzzo.app.data.checkout.StaleReason
import com.tazzzo.app.data.order.CustomerOrderStatus
import com.tazzzo.app.data.order.OrderFailure
import com.tazzzo.app.data.order.OrderPaymentCondition
import com.tazzzo.app.data.order.OrderState
import com.tazzzo.app.data.order.PlaceOrderAvailability
import com.tazzzo.app.data.order.placeOrderAvailability
import com.tazzzo.app.data.order.view
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrderPresentationTest {
    private val paidWords = listOf("paid", "payment successful", "payment completed", "amount paid", "total paid", "paid by", "collected")
    private val allFailures = listOf(
        OrderFailure.NotLaunched, OrderFailure.QuoteNotReady, OrderFailure.Unauthenticated, OrderFailure.QuoteExpired, OrderFailure.NotFound, OrderFailure.AddressChanged,
        OrderFailure.NotServiceable, OrderFailure.PriceChanged, OrderFailure.ProductUnavailable, OrderFailure.StockUnavailable, OrderFailure.ReservationExpired,
        OrderFailure.CartAlreadyPurchased, OrderFailure.ClientBug, OrderFailure.Unavailable, OrderFailure.Server, OrderFailure.Network, OrderFailure.Timeout, OrderFailure.Unknown
    )

    // ---- COD wording -----------------------------------------------------------------------------------------------

    @Test fun codDueReadsCashOnDeliveryAndPaymentDueOnDelivery() {
        val v = orderOf().view()
        assertEquals("Order confirmed", v.title); assertEquals("Cash on delivery", v.paymentLine); assertEquals("Payment due on delivery", v.dueLine)
    }

    @Test fun noOrderCopyEverSaysPaid() {
        val texts = mutableListOf<String>()
        for (o in listOf(orderOf(), orderOf(condition = OrderPaymentCondition.UNRECOGNIZED))) {
            val v = o.view(); texts += listOf(v.title, v.paymentLine, v.dueLine.orEmpty(), v.subtotalLabel, v.note, "Payment details are on your order.")
        }
        for (f in allFailures) f.view().let { texts += it.title; texts += it.hint }
        for (t in texts) for (w in paidWords) assertFalse(w in t.lowercase(), "'$w' in '$t'")
    }

    @Test fun anUnknownPaymentConditionFailsClosedWithNoDueLineAndNothingElseGuessed() {
        val v = orderOf(condition = OrderPaymentCondition.UNRECOGNIZED).view()
        assertNull(v.dueLine); assertEquals("Cash on delivery", v.paymentLine)
    }

    @Test fun anUnrecognizedStatusIsReceivedNotConfirmed() {
        assertEquals("Order received", orderOf().copy(status = CustomerOrderStatus.UNRECOGNIZED).view().title)
    }

    // ---- money ---------------------------------------------------------------------------------------------------------

    @Test fun onlyAnItemSubtotalIsShownAndItIsNeverCalledATotalOrPayable() {
        val v = orderOf().view()
        assertEquals("Item subtotal", v.subtotalLabel); assertEquals("₹99", v.subtotalValue); assertEquals("The item subtotal isn't the final amount.", v.note)
        for (w in listOf("payable", "amount to pay", "final amount due", "grand")) assertFalse(w in (v.subtotalLabel + v.note).lowercase(), w)
        assertEquals("₹49.50", v.lines.single().unitPriceLabel); assertEquals("₹99", v.lines.single().lineTotalLabel)
    }

    @Test fun noCopyTellsTheCustomerTheAmountIsConfirmedOnDelivery() {
        val all = allFailures.flatMap { listOf(it.view().title, it.view().hint) } + listOf(CheckoutCopy.LAUNCH_GATED, CheckoutCopy.CHARGES_NOTE) + orderOf().view().note
        for (t in all) assertFalse("confirmed on delivery" in t.lowercase() || "on the door" in t.lowercase(), t)
    }

    @Test fun theLaunchGatedMessageIsTheApprovedNeutralCopy() {
        assertEquals("Ordering will be available once the final amount is confirmed.", CheckoutCopy.LAUNCH_GATED)
        assertEquals(CheckoutCopy.LAUNCH_GATED, OrderFailure.NotLaunched.view().title)
    }

    @Test fun benefitNeverAppearsInOrderCopy() {
        val v = orderOf().view(); val text = v.toString().lowercase()
        for (w in listOf("save", "club", "benefit", "discount", "coin")) assertFalse(w in text, w)
    }

    // ---- address -------------------------------------------------------------------------------------------------------

    @Test fun theDeliveryAddressComesFromTheFrozenSnapshot() {
        val a = orderOf().view().addressLines
        assertEquals("Asha Rao", a.first()); assertEquals("Bengaluru Karnataka 560102", a.last())
        assertTrue(orderOf().copy(deliveryAddress = null).view().addressLines.isEmpty())
    }

    // ---- availability / production gate --------------------------------------------------------------------------------

    private val ready = readyState()

    @Test fun availabilityIsAvailableOnlyForAReadyQuoteWithAnOpenGateAndNoOrderInProgress() {
        assertEquals(PlaceOrderAvailability.Available, placeOrderAvailability(true, ready, OrderState.Idle))
        assertTrue(placeOrderAvailability(true, ready, OrderState.Idle).enabled)
    }

    @Test fun aClosedGateDisablesPlacingEvenForAReadyQuote() {
        assertEquals(PlaceOrderAvailability.LaunchGated, placeOrderAvailability(false, ready, OrderState.Idle))
        assertFalse(placeOrderAvailability(false, ready, OrderState.Idle).enabled)
    }

    @Test fun anyOtherQuoteStateDisablesPlacing() {
        for (q in listOf(CheckoutState.Idle, CheckoutState.Creating, CheckoutState.Expired, CheckoutState.Stale(StaleReason.AddressChanged), CheckoutState.SignedOut,
            CheckoutState.Failed(CheckoutFailure.Timeout, true))) {
            assertEquals(PlaceOrderAvailability.NoReadyQuote, placeOrderAvailability(true, q, OrderState.Idle), q.toString())
        }
    }

    @Test fun placingAndAmbiguousStatesDisablePlacingAndOfferCheckOrder() {
        assertEquals(PlaceOrderAvailability.Placing, placeOrderAvailability(true, ready, OrderState.Placing))
        assertEquals(PlaceOrderAvailability.NeedsCheck, placeOrderAvailability(true, ready, OrderState.Ambiguous(OrderFailure.Timeout)))
        assertEquals(PlaceOrderAvailability.NeedsCheck, placeOrderAvailability(false, ready, OrderState.Ambiguous(OrderFailure.Timeout)))
        assertEquals(listOf(CheckoutAction.CheckOrder), OrderFailure.Timeout.view().actions)
    }

    @Test fun aPlacedOrderCannotBePlacedAgain() {
        assertFalse(placeOrderAvailability(true, ready, OrderState.Placed(orderOf())).enabled)
    }

    // ---- recovery actions ----------------------------------------------------------------------------------------------

    @Test fun eachBackendRejectionRoutesToItsApprovedRecovery() {
        assertEquals(listOf(CheckoutAction.ReviewCheckout), OrderFailure.QuoteExpired.view().actions)
        assertEquals(CheckoutAction.ReviewCheckout, OrderFailure.AddressChanged.view().actions.first())
        assertEquals(listOf(CheckoutAction.ChangeAddress), OrderFailure.NotServiceable.view().actions)
        assertEquals(listOf(CheckoutAction.ReviewCheckout), OrderFailure.PriceChanged.view().actions)
        assertEquals(listOf(CheckoutAction.GoToCart), OrderFailure.ProductUnavailable.view().actions)
        assertEquals(listOf(CheckoutAction.GoToCart), OrderFailure.StockUnavailable.view().actions)
        assertEquals(listOf(CheckoutAction.ReviewCheckout), OrderFailure.ReservationExpired.view().actions)
        assertEquals(listOf(CheckoutAction.GoToCart), OrderFailure.CartAlreadyPurchased.view().actions)
        assertEquals(emptyList(), OrderFailure.NotLaunched.view().actions)
    }

    @Test fun everyFailureHasAppWrittenCopy() {
        for (f in allFailures) assertTrue(f.view().title.isNotBlank(), f.toString())
        assertEquals("We couldn't confirm your order", OrderFailure.Network.view().title)
    }
}
