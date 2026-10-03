package com.tazzzo.app.checkout

import com.tazzzo.app.cart.cartOf
import com.tazzzo.app.cart.line
import com.tazzzo.app.data.address.ADDRESS_LIMIT_MESSAGE
import com.tazzzo.app.data.cart.toSummary
import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.data.checkout.CheckoutAction
import com.tazzzo.app.data.checkout.CheckoutCopy
import com.tazzzo.app.data.checkout.CheckoutFailure
import com.tazzzo.app.data.checkout.CheckoutState
import com.tazzzo.app.data.checkout.PAYABLE_CHANGED_VIEW
import com.tazzzo.app.data.checkout.StaleReason
import com.tazzzo.app.data.checkout.moneyLines
import com.tazzzo.app.data.checkout.view
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.model.PayableMoney
import com.tazzzo.app.data.order.CustomerPaymentMethod
import com.tazzzo.app.data.order.OrderFailure
import com.tazzzo.app.data.order.OrderState
import com.tazzzo.app.data.order.PlaceOrderAvailability
import com.tazzzo.app.data.order.placeOrderAvailability
import com.tazzzo.app.data.order.view
import com.tazzzo.app.ui.checkout.PurchaseCopy
import com.tazzzo.app.ui.checkout.dueHeadline
import com.tazzzo.app.ui.checkout.label
import com.tazzzo.app.ui.checkout.paymentCard
import com.tazzzo.app.ui.checkout.recoveryCopy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * UI-05: the presentation adapters over the unchanged checkout/order domain. The domain's decisions are re-asserted
 * through the adapters so a future copy change cannot quietly turn PAYABLE_CHANGED into a retry or invent a money row.
 */
class CheckoutUiModelTest {
    private fun money(sub: Long, disc: Long = 0) = PayableMoney.fromPaise(sub, disc, sub - disc)!!

    // ---- PAYABLE_CHANGED -----------------------------------------------------------------------------------------------

    @Test fun payableChangedIsTheApprovedHeadlineAndSupportWithExactlyOneNewCheckoutAction() {
        for (v in listOf(OrderFailure.PayableChanged.view(), StaleReason.PayableChanged.view(), PAYABLE_CHANGED_VIEW)) {
            val c = v.recoveryCopy()
            assertEquals(PurchaseCopy.PAYABLE_CHANGED_TITLE, c.title)
            assertEquals(PurchaseCopy.PAYABLE_CHANGED_SUPPORT, c.support)
            assertTrue(c.isPayableChanged)
            assertEquals(CheckoutAction.ReviewCheckout, c.primary)
            assertTrue(c.secondary.isEmpty())
            assertFalse(CheckoutAction.TryAgain in v.actions); assertFalse(CheckoutAction.CheckOrder in v.actions)
        }
        assertEquals("Your order amount changed.", PurchaseCopy.PAYABLE_CHANGED_TITLE)
        assertEquals("Review checkout again before placing your order.", PurchaseCopy.PAYABLE_CHANGED_SUPPORT)
    }

    @Test fun priceChangedStaysItsOwnFailureAndIsNeverConflatedWithPayableChanged() {
        val c = OrderFailure.PriceChanged.view().recoveryCopy()
        assertEquals("Some prices changed", c.title)
        assertFalse(c.isPayableChanged)
        assertEquals(CheckoutAction.ReviewCheckout, c.primary)
    }

    @Test fun anAmbiguousPlacementOffersCheckOrderAloneNeverANewOrderOrAQuoteRetry() {
        for (f in listOf(OrderFailure.Network, OrderFailure.Timeout, OrderFailure.Server, OrderFailure.Unavailable, OrderFailure.Unknown)) {
            val c = f.view().recoveryCopy()
            assertEquals(PurchaseCopy.AMBIGUOUS_TITLE, c.title)
            assertEquals(CheckoutAction.CheckOrder, c.primary)
            assertTrue(c.secondary.isEmpty())
            assertFalse(c.isPayableChanged)
        }
        assertEquals(PlaceOrderAvailability.NeedsCheck, placeOrderAvailability(true, CheckoutState.Idle, OrderState.Ambiguous(OrderFailure.Network)))
    }

    @Test fun quoteFailuresKeepTheirDomainActionsAndTheSameKeyRetryRule() {
        val same = CheckoutFailure.Network.view(canRetrySameKey = true).recoveryCopy()
        assertEquals(CheckoutAction.TryAgain, same.primary)
        val fresh = CheckoutFailure.Network.view(canRetrySameKey = false).recoveryCopy()
        assertEquals(CheckoutAction.ReviewCheckout, fresh.primary)
        val contract = CheckoutFailure.ContractViolation.view(canRetrySameKey = true).recoveryCopy()
        assertEquals(CheckoutAction.RefreshCheckout, contract.primary)                 // never "Try again" with the same key
        assertEquals(CheckoutCopy.CONTRACT_FAILURE, contract.title)
        val cartChanged = CheckoutFailure.CartChanged.view(false).recoveryCopy()
        assertEquals(CheckoutAction.GoToCart, cartChanged.primary); assertEquals(listOf(CheckoutAction.ReviewCheckout), cartChanged.secondary)
    }

    @Test fun everyActionHasACustomerLabelWithNoTechnicalWords() {
        for (a in listOf(CheckoutAction.TryAgain, CheckoutAction.ReviewCheckout, CheckoutAction.RefreshCheckout, CheckoutAction.ChooseAddress, CheckoutAction.ChangeAddress, CheckoutAction.GoToCart, CheckoutAction.SignIn, CheckoutAction.CheckOrder)) {
            val l = a.label(); assertTrue(l.isNotBlank()); assertFalse("retry" in l.lowercase() && "key" in l.lowercase())
        }
        assertEquals("Check order", CheckoutAction.CheckOrder.label())
    }

    // ---- money rows -------------------------------------------------------------------------------------------------------

    @Test fun theReviewRowsAreExactlyTheV1BindingRows() {
        val withDiscount = moneyLines(money(10_000, 1_500), Money.ofPaise(10_000))
        assertEquals(listOf("Item subtotal" to "₹100", "Benefit discount" to "-₹15", "Amount due" to "₹85"), withDiscount.map { it.label to it.value })
        assertTrue(withDiscount.last().emphasised)
        val noDiscount = moneyLines(money(4_950), Money.ofPaise(4_950))
        assertEquals(listOf("Item subtotal", "Amount due"), noDiscount.map { it.label })        // no discount row at 0
        for (l in withDiscount + noDiscount) for (w in listOf("fee", "tax", "cod", "coupon", "coin", "wallet")) assertFalse(w in l.label.lowercase(), l.label)
        assertEquals(listOf("Item subtotal"), moneyLines(null, Money.ofPaise(4_950)).map { it.label })   // legacy: subtotal alone, no amount due derived
    }

    @Test fun zeroPayableReadsAsIntentionalNotAsAnError() {
        val zero = money(2_000, 2_000)
        val due = dueHeadline(zero)
        assertEquals("₹0", due.amount); assertEquals(CheckoutCopy.NOTHING_DUE, due.caption)
        assertEquals("Nothing due on delivery", paymentCard(zero).dueLine)
        assertEquals("₹0", moneyLines(zero, Money.ofPaise(2_000)).last().value)
    }

    @Test fun positivePayableSaysDueOnDeliveryAndNeverPaid() {
        val m = money(9_000)
        assertEquals("₹90", dueHeadline(m).amount); assertEquals("due on delivery", dueHeadline(m).caption)
        assertEquals("₹90 due on delivery", paymentCard(m).dueLine)
        assertFalse("paid" in paymentCard(m).dueLine!!.lowercase())
    }

    @Test fun codIsTheOnlyPaymentMethodAndIsAFactNotAChoice() {
        val card = paymentCard(money(9_000))
        assertEquals("Cash on delivery", card.method)
        assertEquals(setOf(CustomerPaymentMethod.COD, CustomerPaymentMethod.UNRECOGNIZED), CustomerPaymentMethod.entries.toSet())   // no UPI/card in the contract
    }

    // ---- cart summary and address limit ---------------------------------------------------------------------------------------

    @Test fun theCartSummaryIsTheServersItemSubtotalAndSaysItIsNotTheFinalAmount() {
        val s = cartOf(3, line("TZP-1", 2, unit = 50), line("TZP-2", 1, unit = 25)).toSummary()
        assertEquals("₹125", s.subtotalLabel); assertEquals("3 items", s.itemsLabel)
        assertTrue("isn't the final amount" in s.subtotalCaption)
    }

    @Test fun theAddressLimitCopyIsTheBackendsConfiguredLimit() {
        assertEquals("You can save up to 10 addresses.", ADDRESS_LIMIT_MESSAGE)
        assertTrue(PurchaseCopy.ADDRESS_LIMIT_TITLE.isNotBlank())
    }

    @Test fun placementIsOnlyAvailableForAReadyBindingQuoteOnALaunchEnabledBuild() {
        assertEquals(PlaceOrderAvailability.LaunchGated, placeOrderAvailability(false, CheckoutState.Idle, OrderState.Idle))
        assertEquals(PlaceOrderAvailability.NoReadyQuote, placeOrderAvailability(true, CheckoutState.Idle, OrderState.Idle))
        assertEquals(PlaceOrderAvailability.Placing, placeOrderAvailability(true, CheckoutState.Idle, OrderState.Placing))
        assertFalse(CatalogCapabilities.REMOTE.orderIntegration)
    }

    @Test fun nullMoneyNeverProducesADueLine() { assertNull(paymentCard(null).dueLine) }
}
