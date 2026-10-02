package com.tazzzo.app.cart

import com.tazzzo.app.catalog.cp
import com.tazzzo.app.data.cart.CartNotice
import com.tazzzo.app.data.cart.KnownIssue
import com.tazzzo.app.data.cart.LineIssue
import com.tazzzo.app.data.cart.PendingTarget
import com.tazzzo.app.data.cart.PurchaseControl
import com.tazzzo.app.data.cart.badgeLabel
import com.tazzzo.app.data.cart.message
import com.tazzzo.app.data.cart.purchaseControl
import com.tazzzo.app.data.cart.text
import com.tazzzo.app.data.cart.toSummary
import com.tazzzo.app.data.cart.toView
import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.data.catalog.StockState
import com.tazzzo.app.data.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CartPresentationTest {
    private val remote = CatalogCapabilities.REMOTE

    // ---- issues ---------------------------------------------------------------------------------------------------

    @Test fun everyKnownIssueHasDistinctCustomerCopyAndNoRawCode() {
        val msgs = KnownIssue.entries.map { LineIssue.Known(it).message() }
        assertEquals(msgs.size, msgs.toSet().size)
        for (m in msgs) assertTrue(m.isNotBlank() && m.none { it == '_' }, m)
    }

    @Test fun anUnrecognizedIssueIsNeutralAndNeverEchoesTheServersCode() {
        val m = LineIssue.Unrecognized("SOME_NEW_CODE").message()
        assertFalse("SOME_NEW_CODE" in m)
    }

    @Test fun everyNoticeHasCopyAndNoneNamesTheHiddenCap() {
        for (n in CartNotice.entries) { val t = n.text(); assertTrue(t.isNotBlank()); assertFalse("20" in t, n.name) }
        assertEquals("Your cart changed. Review it and try again.", CartNotice.Stale.text())
    }

    // ---- line view ------------------------------------------------------------------------------------------------

    @Test fun anIssueBlocksALineEvenWhenTheServerSaysBuyable() {
        val v = line("TZP-1", issues = listOf("OUT_OF_STOCK"), buyable = true).toView()
        assertTrue(v.blocked); assertFalse(v.canIncrease); assertEquals(1, v.issues.size)
    }

    @Test fun anUnknownIssueBlocksTheLine() {
        assertTrue(line("TZP-1", issues = listOf("BRAND_NEW")).toView().blocked)
    }

    @Test fun notBuyableBlocksWithNoIssue() {
        assertTrue(line("TZP-1", buyable = false).toView().blocked)
    }

    @Test fun aMissingTitleIsAGenericLabelNotBlank() {
        assertEquals("Unavailable item", line("TZP-1", title = null).toView().title)
    }

    @Test fun plusIsOfferedOnlyWhenAvailabilityIsKnownAndBelowMax() {
        assertTrue(line(qty = 2, max = 5).toView().canIncrease)
        assertFalse(line(qty = 5, max = 5).toView().canIncrease)
        assertFalse(line(qty = 1, serviceable = null).toView().canIncrease)
        assertFalse(line(qty = 1, serviceable = false).toView().canIncrease)
        assertFalse(line(qty = 1, stock = StockState.UNKNOWN).toView().canIncrease)
        assertFalse(line(qty = 1, stock = StockState.OUT_OF_STOCK).toView().canIncrease)
    }

    @Test fun maxIsExposedOnlyWhenAvailabilityIsKnown() {
        assertEquals(5, line(max = 5).toView().maxQuantity)
        assertNull(line(max = 5, serviceable = null).toView().maxQuantity)
    }

    @Test fun moneyIsFormattedFromPaiseAndMrpOnlyWhenHigher() {
        val v = line(qty = 2, unit = 50).copy(mrp = rs(60)).toView()
        assertEquals("₹50", v.unitPriceLabel); assertEquals("₹60", v.mrpLabel); assertEquals("₹100", v.lineTotalLabel)
        assertNull(line(unit = 50).toView().mrpLabel)
        assertNull(line().copy(unitPrice = null, lineTotal = null).toView().unitPriceLabel)
    }

    // ---- summary --------------------------------------------------------------------------------------------------

    @Test fun summaryIsAnItemSubtotalWithNoDeliveryOrTaxClaim() {
        val s = cartOf(2, line("TZP-1", 2, unit = 50), line("TZP-2", 1, unit = 25)).toSummary()
        assertEquals("3 items", s.itemsLabel); assertEquals("₹125", s.subtotalLabel)
        assertTrue("Delivery" in s.subtotalCaption && "checkout" in s.subtotalCaption)
        assertFalse(s.hasBlockedLines)
    }

    @Test fun oneItemIsSingular() = assertEquals("1 item", cartOf(1, line(qty = 1)).toSummary().itemsLabel)

    @Test fun blockedLinesAreCounted() {
        val s = cartOf(1, line("TZP-1"), line("TZP-2", issues = listOf("UNSERVICEABLE"))).toSummary()
        assertEquals(1, s.blockedLines); assertTrue(s.hasBlockedLines)
    }

    @Test fun theBadgeCapsAt99PlusAndHidesWhenEmpty() {
        assertNull(cartOf(1).badgeLabel())
        assertEquals("5", cartOf(1, line(qty = 5)).badgeLabel())
        assertEquals("99+", cartOf(1, line(qty = 150)).badgeLabel())
    }

    // ---- ADD / stepper decision -----------------------------------------------------------------------------------

    @Test fun aBuyablePricedProductShowsAdd() =
        assertEquals(PurchaseControl.Add, purchaseControl(cp(), remote, 0, null))

    @Test fun aProductWithoutAPriceOrNotBuyableIsDisabledWithCopy() {
        assertEquals("Price unavailable", assertIs<PurchaseControl.Disabled>(purchaseControl(cp(price = null), remote, 0, null)).label)
        assertEquals("Currently unavailable", assertIs<PurchaseControl.Disabled>(purchaseControl(cp(buyable = false), remote, 0, null)).label)
    }

    @Test fun stockStateIsNotConsultedOnlyTheServersBuyable() {
        assertEquals(PurchaseControl.Add, purchaseControl(cp(stock = StockState.UNKNOWN, buyable = true), remote, 0, null))
    }

    @Test fun aLineInTheCartShowsItsConfirmedQuantity() {
        val c = assertIs<PurchaseControl.Stepper>(purchaseControl(cp(), remote, 2, null))
        assertEquals(2, c.quantity); assertFalse(c.pending)
    }

    @Test fun aPendingTargetIsShownInstantlyAndMarkedPending() {
        val c = assertIs<PurchaseControl.Stepper>(purchaseControl(cp(), remote, 2, PendingTarget.Quantity(5)))
        assertEquals(5, c.quantity); assertTrue(c.pending)
    }

    @Test fun aPendingRemoveOfTheLastUnitShowsAddAgain() {
        assertEquals(PurchaseControl.Add, purchaseControl(cp(), remote, 1, PendingTarget.Removing))
    }

    @Test fun theFirstAddRespectsTheCardsMaxAndALineAtMaxCannotIncrease() {
        assertFalse(assertIs<PurchaseControl.Stepper>(purchaseControl(cp(max = 3), remote, 3, null)).canIncrease)
        assertTrue(assertIs<PurchaseControl.Stepper>(purchaseControl(cp(max = 3), remote, 2, null)).canIncrease)
    }

    @Test fun aLineAlreadyInTheCartStaysAStepperEvenIfTheCardTurnedUnbuyable() {
        // so the customer can still reduce or remove it
        val c = assertIs<PurchaseControl.Stepper>(purchaseControl(cp(buyable = false), remote, 2, null))
        assertFalse(c.canIncrease)
    }

    @Test fun withoutCartIntegrationNothingIsAddable() {
        assertIs<PurchaseControl.Disabled>(purchaseControl(cp(), remote.copy(cartIntegration = false), 0, null))
    }

    @Test fun moneyZeroPriceIsStillAPriceTheServerDecidesBuyable() {
        assertEquals(PurchaseControl.Add, purchaseControl(cp(price = Money.ZERO), remote, 0, null))
    }
}
