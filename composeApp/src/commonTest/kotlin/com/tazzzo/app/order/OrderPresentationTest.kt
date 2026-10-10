package com.tazzzo.app.order

import com.tazzzo.app.checkout.stampOf
import com.tazzzo.app.data.checkout.CheckoutAction
import com.tazzzo.app.data.checkout.CheckoutCopy
import com.tazzzo.app.data.checkout.CheckoutFailure
import com.tazzzo.app.data.checkout.CheckoutState
import com.tazzzo.app.data.checkout.StaleReason
import com.tazzzo.app.data.model.PayableMoney
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
        OrderFailure.PayableChanged, OrderFailure.CartAlreadyPurchased, OrderFailure.ClientBug, OrderFailure.Unavailable, OrderFailure.Server, OrderFailure.Network, OrderFailure.Timeout, OrderFailure.Unknown,
        OrderFailure.SlotUnavailable, OrderFailure.StaleVersion, OrderFailure.InvalidTransition, OrderFailure.NotCancellable, OrderFailure.CancellationWindowClosed
    )

    // ---- COD wording -----------------------------------------------------------------------------------------------

    private fun m(s: Long, d: Long, p: Long) = PayableMoney.fromPaise(s, d, p)!!
    private fun lines(o: com.tazzzo.app.data.order.CustomerOrder) = o.view().moneyLines.map { "${it.label} ${it.value}" }

    @Test fun codDueWithAuthoritativeMoneyReadsCashOnDeliveryAndTheAmountDueOnDelivery() {
        val v = orderOf().view()
        assertEquals("Order confirmed", v.title); assertEquals("Cash on delivery", v.paymentLine); assertEquals("₹99 due on delivery", v.dueLine)
    }

    @Test fun aZeroAmountDueReadsNothingDueOnDeliveryNeverPaymentDue() {
        val v = orderOf(subtotal = 10_000, money = m(10_000, 10_000, 0)).view()
        assertEquals("Nothing due on delivery", v.dueLine)
        assertEquals(listOf("Item subtotal ₹100", "Benefit discount -₹100", "Amount due ₹0"), lines(orderOf(subtotal = 10_000, money = m(10_000, 10_000, 0))))
        assertFalse("payment due" in v.dueLine!!.lowercase())
    }

    @Test fun aLegacyOrderWithoutMoneyKeepsTheAmountFreeDueLine() {
        assertEquals("Payment due on delivery", orderOf(money = null).view().dueLine)
    }

    @Test fun noOrderCopyEverSaysPaid() {
        val texts = mutableListOf<String>()
        for (o in listOf(orderOf(), orderOf(condition = OrderPaymentCondition.UNRECOGNIZED), orderOf(money = null),
                orderOf(subtotal = 10_000, money = m(10_000, 1_000, 9_000)), orderOf(subtotal = 10_000, money = m(10_000, 10_000, 0)))) {
            val v = o.view(); texts += listOf(v.title, v.paymentLine, v.dueLine.orEmpty(), "Payment details are on your order.") + v.moneyLines.map { it.label + " " + it.value }
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

    @Test fun theAuthoritativeMoneyShowsItemSubtotalAndAmountDue() {
        assertEquals(listOf("Item subtotal ₹99", "Amount due ₹99"), lines(orderOf()))
        assertEquals(listOf(false, true), orderOf().view().moneyLines.map { it.emphasised })
        assertEquals("Amount due", orderOf().view().headlineMoney.label)
        val v = orderOf().view(); assertEquals("₹49.50", v.lines.single().unitPriceLabel); assertEquals("₹99", v.lines.single().lineTotalLabel)
    }

    @Test fun theDiscountRowAppearsOnlyWhenTheAuthoritativeDiscountIsPositive() {
        assertEquals(listOf("Item subtotal ₹100", "Benefit discount -₹10", "Amount due ₹90"), lines(orderOf(subtotal = 10_000, money = m(10_000, 1_000, 9_000))))
        assertEquals("₹90 due on delivery", orderOf(subtotal = 10_000, money = m(10_000, 1_000, 9_000)).view().dueLine)
        assertFalse(lines(orderOf()).any { "discount" in it.lowercase() })
    }

    @Test fun aLegacyOrderShowsTheItemSubtotalAloneAndNeverCallsItTheAmountDue() {
        assertEquals(listOf("Item subtotal ₹99"), lines(orderOf(money = null)))
        assertEquals("Item subtotal", orderOf(money = null).view().headlineMoney.label)
        assertFalse(orderOf(money = null).view().toString().contains("Amount due"))
    }

    @Test fun noCopyTellsTheCustomerTheAmountIsConfirmedOnDeliveryOrIsAdvisory() {
        val all = allFailures.flatMap { listOf(it.view().title, it.view().hint) } + CheckoutCopy.LAUNCH_GATED + lines(orderOf()) + lines(orderOf(money = null))
        for (t in all) for (w in listOf("confirmed on delivery", "on the door", "final amount", "estimated", "approximate", "provisional", "may change"))
            assertFalse(w in t.lowercase(), "'$w' in '$t'")
    }

    @Test fun theLaunchGatedMessageIsTheApprovedNeutralCopyAndNeverAboutTheAmount() {
        assertEquals("Ordering isn't available yet.", CheckoutCopy.LAUNCH_GATED)
        assertEquals(CheckoutCopy.LAUNCH_GATED, OrderFailure.NotLaunched.view().title)
        assertFalse("amount" in CheckoutCopy.LAUNCH_GATED.lowercase())
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

    @Test fun aQuoteWithoutBindingMoneyIsNeverOrderableWhateverTheGateSays() {
        for (gate in listOf(true, false)) {
            val v = placeOrderAvailability(gate, readyState(money = null), OrderState.Idle)
            assertEquals(PlaceOrderAvailability.NeedsRefresh, v); assertFalse(v.enabled)
        }
    }

    @Test fun anyOtherQuoteStateDisablesPlacing() {
        for (q in listOf(CheckoutState.Idle, CheckoutState.Creating, CheckoutState.Expired, CheckoutState.Stale(StaleReason.AddressChanged),
            CheckoutState.Stale(StaleReason.PayableChanged), CheckoutState.SignedOut, CheckoutState.Failed(CheckoutFailure.ContractViolation, false),
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
        // PAYABLE_CHANGED: only a NEW quote — never "Check order".
        assertEquals(listOf(CheckoutAction.ReviewCheckout), OrderFailure.PayableChanged.view().actions)
        assertEquals("Your order amount changed. Review checkout again.", OrderFailure.PayableChanged.view().title)
        assertEquals(emptyList(), OrderFailure.NotLaunched.view().actions)
    }

    @Test fun everyFailureHasAppWrittenCopy() {
        for (f in allFailures) assertTrue(f.view().title.isNotBlank(), f.toString())
        assertEquals("We couldn't confirm your order", OrderFailure.Network.view().title)
    }
}
