package com.tazzzo.app

import com.tazzzo.app.data.model.Address
import com.tazzzo.app.data.model.CoinTransaction
import com.tazzzo.app.data.model.DeliverySlot
import com.tazzzo.app.data.model.PaymentMethodKind
import com.tazzzo.app.data.model.PlaceOrderResult
import com.tazzzo.app.data.model.Product
import com.tazzzo.app.data.repository.CoinRepository
import com.tazzzo.app.data.repository.MockCatalogRepository
import com.tazzzo.app.data.repository.MockCheckoutRepository
import com.tazzzo.app.data.repository.MockOrderRepository
import com.tazzzo.app.order.OrderPlacement
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression tests for D-1: the demo autopilot placed orders through a
 * hand-rolled copy of the checkout screen's logic and credited coins WITHOUT
 * the `!replayed` guard, so a replayed placement handed out coins twice.
 *
 * Both call sites now go through OrderPlacement. These tests pin the money
 * behaviour of that single path: coins move exactly once per real order, no
 * matter how many times placement is attempted.
 */
class OrderPlacementTest {

    /** Counts every credit that reaches the coin ledger. */
    private class CountingCoinRepository : CoinRepository {
        var credits = 0
        var totalCredited = 0
        override suspend fun getBalance(): Int = totalCredited
        override suspend fun getLedger(): List<CoinTransaction> = emptyList()
        override suspend fun credit(amount: Int, title: String) {
            credits++
            totalCredited += amount
        }
    }

    private val address = Address("addr-1", "Home", "22, 14th Main, HSR", "", "560102", isServiceable = true)
    private val slot = DeliverySlot("slot-express", "Express", available = true)

    /** p8 exactly as the catalogue holds it, so validateCart passes. */
    private fun milk() = Product(
        id = "p8", name = "Toned Milk Pouch", brand = "Amul", emoji = "🥛", unit = "500 ml",
        price = r(29), mrp = r(30), categoryId = "dairy", subcategoryId = "milk",
        rating = 4.7, ratingCount = 8804
    )

    private fun fixture(): Triple<TazzzoAppState, CheckoutSession, MockCheckoutRepository> {
        val app = TazzzoAppState(store = null)
        app.addToCart(milk())
        app.addToCart(milk())
        val session = CheckoutSession()
        session.selectAddress(address)
        session.slot = slot
        session.payment = PaymentMethodKind.COD
        app.checkout = session
        return Triple(app, session, MockCheckoutRepository(MockCatalogRepository(), MockOrderRepository()))
    }

    private suspend fun place(
        app: TazzzoAppState,
        session: CheckoutSession,
        repo: MockCheckoutRepository,
        coins: CoinRepository
    ) = OrderPlacement.place(
        app = app, session = session, address = address, slot = slot,
        payment = PaymentMethodKind.COD, checkout = repo, coins = coins, navigate = false
    )

    @Test fun first_placement_credits_coins_exactly_once() = runTest {
        val (app, session, repo) = fixture()
        val coins = CountingCoinRepository()
        val expected = app.bill(app.cartLines()).coinsEarned
        val startingBalance = app.user.coinBalance

        val result = place(app, session, repo, coins)

        assertIs<PlaceOrderResult.Placed>(result)
        assertEquals(false, result.replayed)
        assertEquals(1, coins.credits)
        assertEquals(expected, coins.totalCredited)
        assertEquals(startingBalance + expected, app.user.coinBalance)
        assertTrue(expected > 0, "fixture must earn coins or the test proves nothing")
    }

    @Test fun replayed_placement_does_not_credit_again() = runTest {
        val (app, session, repo) = fixture()
        val coins = CountingCoinRepository()

        val first = place(app, session, repo, coins)
        assertIs<PlaceOrderResult.Placed>(first)
        val balanceAfterFirst = app.user.coinBalance

        // Simulate the in-flight UI guard being bypassed (broken UI, restored
        // process, double dispatch): the ledger must still hold the line.
        session.placement = CheckoutSession.Placement.Idle
        val second = place(app, session, repo, coins)

        assertIs<PlaceOrderResult.Placed>(second)
        assertTrue(second.replayed)
        assertEquals(first.order.id, second.order.id)
        assertEquals(1, coins.credits)
        assertEquals(balanceAfterFirst, app.user.coinBalance)
    }

    @Test fun failure_then_retry_credits_exactly_once() = runTest {
        val (app, session, repo) = fixture()
        val coins = CountingCoinRepository()
        repo.failNextPlacement = true

        val failed = place(app, session, repo, coins)
        assertIs<PlaceOrderResult.Failed>(failed)
        assertEquals(0, coins.credits)
        assertIs<CheckoutSession.Placement.Failed>(session.placement)
        assertEquals(2, app.cartItemCount, "a failed placement must leave the cart untouched")

        val retry = place(app, session, repo, coins)
        assertIs<PlaceOrderResult.Placed>(retry)
        assertEquals(false, retry.replayed)
        assertEquals(1, coins.credits)
    }

    @Test fun repeated_placement_cannot_create_duplicate_coin_credit() = runTest {
        val (app, session, repo) = fixture()
        val coins = CountingCoinRepository()
        val orderIds = mutableSetOf<String>()

        repeat(5) {
            session.placement = CheckoutSession.Placement.Idle   // defeat the UI guard
            val r = place(app, session, repo, coins)
            assertIs<PlaceOrderResult.Placed>(r)
            orderIds += r.order.id
        }

        assertEquals(1, orderIds.size, "one idempotency key must yield exactly one order")
        assertEquals(1, coins.credits, "coins must be credited exactly once per order")
    }

    @Test fun in_flight_placement_is_refused_without_reaching_the_repository() = runTest {
        val (app, session, repo) = fixture()
        val coins = CountingCoinRepository()
        session.placement = CheckoutSession.Placement.InFlight

        assertNull(place(app, session, repo, coins))
        assertEquals(0, coins.credits)
    }
}
