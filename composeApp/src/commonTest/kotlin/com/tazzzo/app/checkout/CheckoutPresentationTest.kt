package com.tazzzo.app.checkout

import com.tazzzo.app.cart.cartOf
import com.tazzzo.app.cart.line
import com.tazzzo.app.cart.rs
import com.tazzzo.app.data.checkout.BenefitPreviewState
import com.tazzzo.app.data.checkout.CheckoutAction
import com.tazzzo.app.data.checkout.CheckoutCopy
import com.tazzzo.app.data.checkout.CheckoutFailure
import com.tazzzo.app.data.checkout.CheckoutQuote
import com.tazzzo.app.data.checkout.CheckoutQuoteItem
import com.tazzzo.app.data.checkout.EXPIRED_VIEW
import com.tazzzo.app.data.checkout.ItemRejection
import com.tazzzo.app.data.checkout.StaleReason
import com.tazzzo.app.data.checkout.displayLines
import com.tazzzo.app.data.checkout.expiryLabel
import com.tazzzo.app.data.checkout.lineViews
import com.tazzzo.app.data.checkout.summary
import com.tazzzo.app.data.checkout.text
import com.tazzzo.app.data.checkout.view
import com.tazzzo.app.data.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class CheckoutPresentationTest {
    private fun quote(benefit: BenefitPreviewState = BenefitPreviewState.NotApplied, cartVersion: Long = 5) = CheckoutQuote(
        "CHKQ_abc123", cartVersion, "ADDR_abcdef1",
        listOf(CheckoutQuoteItem("TZP-1", 2, Money.ofPaise(4_950), Money.ofPaise(9_900))), 2, 1, Money.ofPaise(9_900), "INR", 0, 300_000, benefit, "r"
    )

    private val forbidden = listOf("total", "payable", "amount to pay", "final amount", "grand")
    /** "Item subtotal" is the one approved word containing "total"; everything else must not call the figure a total. */
    /** The one approved sentence that DENIES the subtotal is the final amount is exempt from the ban on that phrase. */
    private fun words(t: String) = t.lowercase().replace("the item subtotal isn't the final amount.", "").replace("subtotal", "")

    @Test fun theSubtotalIsAlwaysCalledItemSubtotalAndNeverATotal() {
        val s = quote().summary()
        assertEquals("Item subtotal", s.subtotalLabel); assertEquals("₹99", s.subtotalValue)
        assertEquals("The item subtotal isn't the final amount.", s.note)
        for (w in forbidden) assertFalse(w in words(s.subtotalLabel + s.note + CheckoutCopy.ORDER_CTA), w)
    }

    @Test fun noCustomerCopyAnywhereCallsTheSubtotalATotalOrPromisesTheFinalAmount() {
        val all = mutableListOf<String>()
        listOf(
            CheckoutFailure.AddressRequired, CheckoutFailure.CartUnavailable, CheckoutFailure.CartBusy, CheckoutFailure.CartHasIssues, CheckoutFailure.Unauthenticated,
            CheckoutFailure.CartChanged, CheckoutFailure.CartEmpty, CheckoutFailure.Unserviceable, CheckoutFailure.ItemsUnavailable(emptyList()), CheckoutFailure.KeyConflict,
            CheckoutFailure.NotFound, CheckoutFailure.QuoteExpired, CheckoutFailure.ClientBug, CheckoutFailure.RateLimited(3), CheckoutFailure.Unavailable,
            CheckoutFailure.Server, CheckoutFailure.Network, CheckoutFailure.Timeout, CheckoutFailure.Unknown
        ).forEach { f -> listOf(true, false).forEach { k -> f.view(k).let { all += it.title; all += it.hint } } }
        StaleReason.entries.forEach { all += it.view().title; all += it.view().hint }
        all += EXPIRED_VIEW.title; all += EXPIRED_VIEW.hint
        for (t in all) for (w in forbidden) assertFalse(w in words(t), "$w in '$t'")
    }

    @Test fun theBenefitPreviewNeverAppearsInAnyBranchAndNeverCreatesAPayableAmount() {
        val views = listOf(BenefitPreviewState.Legacy, BenefitPreviewState.NotApplied, BenefitPreviewState.Applied(rs(5), 500), BenefitPreviewState.Unreadable)
            .map { b -> quote(b).summary() to quote(b).lineViews(null) }
        assertEquals(1, views.toSet().size)                                // identical output: the preview has no presentation at all
        val text = views.first().let { (s, l) -> s.toString() + l.toString() }.lowercase()
        for (w in listOf("save", "club", "benefit", "discount", "you pay")) assertFalse(w in text, w)
        assertFalse(Regex("\\bnet\\b").containsMatchIn(text))
    }

    @Test fun lineMoneyComesFromTheQuoteAndTheTitleOnlyFromTheSameVersionCart() {
        val q = quote(cartVersion = 5)
        val sameCart = cartOf(5, line("TZP-1", 2, unit = 70, title = "Atta 1kg"))              // the cart's price differs: it must not leak in
        val v = q.lineViews(sameCart).single()
        assertEquals("Atta 1kg", v.title); assertEquals("₹49.50", v.unitPriceLabel); assertEquals("₹99", v.lineTotalLabel); assertEquals(2, v.quantity)
        assertEquals(CheckoutCopy.NEUTRAL_ITEM, q.lineViews(cartOf(6, line("TZP-1", 2, title = "Atta 1kg"))).single().title)    // different cart: neutral
        assertEquals(CheckoutCopy.NEUTRAL_ITEM, q.lineViews(null).single().title)
        assertEquals(CheckoutCopy.NEUTRAL_ITEM, q.lineViews(cartOf(5, line("TZP-9", 1))).single().title)                      // sku not in the cart
    }

    @Test fun itemCountLabelIsSingularOrPlural() {
        assertEquals("2 items", quote().summary().itemsLabel)
        assertEquals("1 item", quote().copy(itemCount = 1).summary().itemsLabel)
    }

    @Test fun tryAgainIsOfferedOnlyWhenTheSameKeyMayStillBeUsed() {
        assertEquals(listOf(CheckoutAction.TryAgain), CheckoutFailure.Timeout.view(true).actions)
        assertEquals(listOf(CheckoutAction.ReviewCheckout), CheckoutFailure.Timeout.view(false).actions)
        assertEquals(listOf(CheckoutAction.TryAgain), CheckoutFailure.Unavailable.view(true).actions)
    }

    @Test fun recoveryActionsRouteToTheRealCartAndAddressFlows() {
        assertEquals(listOf(CheckoutAction.GoToCart), CheckoutFailure.CartEmpty.view(false).actions)
        assertEquals(listOf(CheckoutAction.GoToCart), CheckoutFailure.ItemsUnavailable(emptyList()).view(false).actions)
        assertEquals(listOf(CheckoutAction.ChangeAddress), CheckoutFailure.Unserviceable.view(false).actions)
        assertEquals("This address isn't serviceable for this order.", CheckoutFailure.Unserviceable.view(false).title)
        assertEquals(listOf(CheckoutAction.ChooseAddress), CheckoutFailure.AddressRequired.view(false).actions)
        assertEquals("Your cart changed. Review it before continuing.", CheckoutFailure.CartChanged.view(false).title)
        assertEquals(listOf(CheckoutAction.ReviewCheckout), EXPIRED_VIEW.actions)
        assertTrue(StaleReason.CartChanged.view().actions.first() == CheckoutAction.ReviewCheckout)
    }

    @Test fun itemRejectionsHaveSafeCopyForEveryReasonAndUnknownCodes() {
        for (r in ItemRejection.Reason.entries) assertTrue(ItemRejection.Known("TZP-1", r).text().isNotBlank())
        assertFalse("WHAT" in ItemRejection.Unrecognized("TZP-1").text())
    }

    @Test fun deliveryLinesComeFromTheLocalProjectionOfTheSelectedAddress() {
        val d = stampOf().delivery.displayLines()
        assertEquals("Asha Rao", d.first()); assertTrue(d.last().endsWith("560102"))
    }

    @Test fun theCountdownReadsMinutesAndSeconds() {
        assertEquals("Expires in 4:32", expiryLabel(272.seconds)); assertEquals("Expires in 0:00", expiryLabel((-3).seconds))
    }
}
