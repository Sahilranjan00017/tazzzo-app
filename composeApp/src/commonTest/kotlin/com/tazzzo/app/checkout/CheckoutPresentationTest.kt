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
import com.tazzzo.app.data.model.PayableMoney
import com.tazzzo.app.data.checkout.CheckoutSummaryView
import com.tazzzo.app.data.checkout.NO_BINDING_MONEY_VIEW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class CheckoutPresentationTest {
    private fun quote(
        benefit: BenefitPreviewState = BenefitPreviewState.NotApplied, cartVersion: Long = 5,
        money: PayableMoney? = bindingOf(Money.ofPaise(9_900))
    ) = CheckoutQuote(
        "CHKQ_abc123", cartVersion, "ADDR_abcdef1",
        listOf(CheckoutQuoteItem("TZP-1", 2, Money.ofPaise(4_950), Money.ofPaise(9_900))), 2, 1, Money.ofPaise(9_900), "INR", 0, 300_000, benefit, money, "r"
    )

    private fun m(s: Long, d: Long, p: Long) = PayableMoney.fromPaise(s, d, p)!!
    private fun texts(v: CheckoutSummaryView) = v.lines.map { "${it.label} ${it.value}" }

    /** Every piece of customer copy the checkout and order flows can show. */
    private fun allCopy(): List<String> {
        val all = mutableListOf<String>()
        listOf(
            CheckoutFailure.AddressRequired, CheckoutFailure.CartUnavailable, CheckoutFailure.CartBusy, CheckoutFailure.CartHasIssues, CheckoutFailure.Unauthenticated,
            CheckoutFailure.CartChanged, CheckoutFailure.CartEmpty, CheckoutFailure.Unserviceable, CheckoutFailure.ItemsUnavailable(emptyList()), CheckoutFailure.KeyConflict,
            CheckoutFailure.NotFound, CheckoutFailure.QuoteExpired, CheckoutFailure.ClientBug, CheckoutFailure.RateLimited(3), CheckoutFailure.Unavailable,
            CheckoutFailure.Server, CheckoutFailure.Network, CheckoutFailure.Timeout, CheckoutFailure.Unknown, CheckoutFailure.ContractViolation
        ).forEach { f -> listOf(true, false).forEach { k -> f.view(k).let { all += it.title; all += it.hint } } }
        StaleReason.entries.forEach { all += it.view().title; all += it.view().hint }
        all += listOf(EXPIRED_VIEW.title, EXPIRED_VIEW.hint, NO_BINDING_MONEY_VIEW.title, NO_BINDING_MONEY_VIEW.hint)
        all += listOf(CheckoutCopy.SUBTOTAL_LABEL, CheckoutCopy.DISCOUNT_LABEL, CheckoutCopy.AMOUNT_DUE_LABEL, CheckoutCopy.NOTHING_DUE,
            CheckoutCopy.ORDER_CTA, CheckoutCopy.ORDERING_PAUSED, CheckoutCopy.PAYABLE_CHANGED, CheckoutCopy.CONTRACT_FAILURE)
        listOf(m(10_000, 1_000, 9_000), m(4_950, 0, 4_950), m(10_000, 10_000, 0)).forEach { mm ->
            quote(money = mm).summary().let { all += texts(it); all += it.dueNote.orEmpty() }
        }
        all += texts(quote(money = null).summary())
        return all
    }

    @Test fun theSummaryShowsTheItemSubtotalAndTheAmountDueFromTheBindingMoney() {
        val s = quote(money = m(9_900, 0, 9_900)).summary()
        assertEquals(listOf("Item subtotal ₹99", "Amount due ₹99"), texts(s))
        assertEquals(listOf(false, true), s.lines.map { it.emphasised })
        assertEquals("₹99 due on delivery", s.dueNote)
    }

    @Test fun theDiscountRowAppearsOnlyWhenTheBindingDiscountIsPositive() {
        assertEquals(listOf("Item subtotal ₹100", "Benefit discount -₹10", "Amount due ₹90"), texts(quote(money = m(10_000, 1_000, 9_000)).summary()))
        assertEquals("₹90 due on delivery", quote(money = m(10_000, 1_000, 9_000)).summary().dueNote)
        assertFalse(texts(quote(money = m(9_900, 0, 9_900)).summary()).any { "discount" in it.lowercase() })
    }

    @Test fun paiseAreShownExactly() {
        assertEquals(listOf("Item subtotal ₹49.50", "Amount due ₹49.50"), texts(quote(money = m(4_950, 0, 4_950)).summary()))
        assertEquals(listOf("Item subtotal ₹99.25", "Benefit discount -₹0.25", "Amount due ₹99"), texts(quote(money = m(9_925, 25, 9_900)).summary()))
    }

    @Test fun aZeroAmountDueReadsNothingDueOnDeliveryNeverPaymentDue() {
        val s = quote(money = m(10_000, 10_000, 0)).summary()
        assertEquals(listOf("Item subtotal ₹100", "Benefit discount -₹100", "Amount due ₹0"), texts(s))
        assertEquals("Nothing due on delivery", s.dueNote)
        assertFalse("payment due" in s.dueNote!!.lowercase())
    }

    @Test fun aQuoteWithoutBindingMoneyShowsTheItemSubtotalAloneAndNoAmountDue() {
        val s = quote(money = null).summary()
        assertEquals(listOf("Item subtotal ₹99"), texts(s))
        assertNull(s.dueNote)
        assertEquals(listOf(CheckoutAction.RefreshCheckout), NO_BINDING_MONEY_VIEW.actions)
    }

    @Test fun noCustomerCopyIsAdvisoryOrInventsAChargeOrSaysPaid() {
        val banned = listOf("estimated", "estimate", "approximate", "approx", "provisional", "may change", "might change", "final amount",
            "calculated later", "delivery fee", "platform fee", "handling", "tax", "cod fee", "coupon", "coins", "wallet", "paid",
            "payment successful", "payment completed", "total")
        val all = allCopy()
        for (t in all) for (w in banned) assertFalse(w in t.lowercase().replace("subtotal", ""), "'$w' in '$t'")
    }

    @Test fun theBenefitPreviewIsNeverPresentedTheDiscountComesOnlyFromTheBindingMoney() {
        val money = m(9_900, 500, 9_400)
        val views = listOf(BenefitPreviewState.Legacy, BenefitPreviewState.NotApplied, BenefitPreviewState.Applied(rs(5), 500), BenefitPreviewState.Unreadable)
            .map { b -> quote(b, money = money).summary() to quote(b, money = money).lineViews(null) }
        assertEquals(1, views.toSet().size)                                // identical output: the preview itself has no presentation
        val text = views.first().let { (s, l) -> s.toString() + l.toString() }.lowercase()
        for (w in listOf("save", "club", "%", "bps", "you pay")) assertFalse(w in text, w)
        // An Applied preview with NO binding discount shows no discount row: the preview never creates one.
        assertFalse(texts(quote(BenefitPreviewState.Applied(rs(5), 500), money = m(9_900, 0, 9_900)).summary()).any { "discount" in it.lowercase() })
    }

    @Test fun aContractFailureOffersOnlyRefreshCheckoutNeverTheSameKey() {
        for (k in listOf(true, false)) {
            val v = CheckoutFailure.ContractViolation.view(k)
            assertEquals("Checkout couldn't be loaded correctly.", v.title)
            assertEquals(listOf(CheckoutAction.RefreshCheckout), v.actions)
        }
        assertFalse(CheckoutFailure.ContractViolation.isAmbiguous)
    }

    @Test fun payableChangedReadsTheApprovedSentenceAndOffersOnlyReviewCheckout() {
        val v = StaleReason.PayableChanged.view()
        assertEquals("Your order amount changed. Review checkout again.", v.title)
        assertEquals(listOf(CheckoutAction.ReviewCheckout), v.actions)
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
