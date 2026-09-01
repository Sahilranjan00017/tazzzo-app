package com.tazzzo.app

import com.tazzzo.app.data.model.Availability
import com.tazzzo.app.data.model.Address
import com.tazzzo.app.data.model.CartIssue
import com.tazzzo.app.data.model.CartLine
import com.tazzzo.app.data.model.DeliverySlot
import com.tazzzo.app.data.model.OrderRequest
import com.tazzzo.app.data.model.PaymentMethodKind
import com.tazzzo.app.data.model.PlaceOrderResult
import com.tazzzo.app.data.model.Product
import com.tazzzo.app.config.BillCalculator
import com.tazzzo.app.data.repository.MockCheckoutRepository
import com.tazzzo.app.data.repository.MockOrderRepository
import com.tazzzo.app.data.repository.MockCatalogRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CheckoutSessionTest {

    private val serviceable = Address("a1", "Home", "22, 14th Main, HSR", "", "560102", isServiceable = true)
    private val unserviceable = Address("a3", "Parents", "42 MG Road, Mysuru", "", "570001", isServiceable = false)
    private val slot = DeliverySlot("s1", "Express", available = true)

    @Test fun cannot_advance_without_serviceable_address() {
        val s = CheckoutSession()
        s.next()
        assertEquals(CheckoutSession.Step.ADDRESS, s.step)
        s.selectAddress(unserviceable); s.next()
        assertEquals(CheckoutSession.Step.ADDRESS, s.step)
        s.selectAddress(serviceable); s.next()
        assertEquals(CheckoutSession.Step.SLOT, s.step)
    }

    @Test fun full_forward_path() {
        val s = CheckoutSession()
        s.selectAddress(serviceable); s.next()
        s.slot = slot; s.next()
        s.payment = PaymentMethodKind.COD; s.next()
        assertEquals(CheckoutSession.Step.REVIEW, s.step)
        s.next()  // REVIEW never advances via next()
        assertEquals(CheckoutSession.Step.REVIEW, s.step)
    }

    @Test fun back_steps_through_and_exits_only_from_address() {
        val s = CheckoutSession()
        s.selectAddress(serviceable); s.next(); s.slot = slot; s.next()
        assertTrue(s.backStep());  assertEquals(CheckoutSession.Step.SLOT, s.step)
        assertTrue(s.backStep());  assertEquals(CheckoutSession.Step.ADDRESS, s.step)
        assertEquals(false, s.backStep())   // signals "exit checkout"
    }

    @Test fun back_is_blocked_while_placing() {
        val s = CheckoutSession()
        s.placement = CheckoutSession.Placement.InFlight
        assertTrue(s.backStep())            // consumed, no navigation
    }

    @Test fun changing_address_resets_slot() {
        val s = CheckoutSession()
        s.selectAddress(serviceable); s.slot = slot
        s.selectAddress(unserviceable)
        assertEquals(null, s.slot)
    }
}

class CheckoutRepositoryTest {

    private fun repo() = MockCheckoutRepository(MockCatalogRepository(), MockOrderRepository())

    private fun line(p: Product, qty: Int) = CartLine(p, qty)

    private fun inStock() = Product(
        id = "p8", name = "Toned Milk Pouch", brand = "Amul", emoji = "🥛", unit = "500 ml",
        price = 29, mrp = 30, categoryId = "dairy", subcategoryId = "milk",
        rating = 4.7, ratingCount = 8804
    )

    private fun request(lines: List<CartLine>, key: String) = OrderRequest(
        idempotencyKey = key, lines = lines,
        bill = BillCalculator.bill(lines),
        addressId = "addr-1", addressText = "HSR Layout, Bengaluru",
        slotId = "slot-express", payment = PaymentMethodKind.COD
    )

    @Test fun oos_between_cart_and_checkout_is_reported() = runTest {
        // p23 (fish) is OutOfStock in the catalogue — a stale cart line must surface it.
        val staleFish = inStock().copy(id = "p23", name = "Rohu Fish Curry Cut")
        val validation = repo().validateCart(listOf(line(staleFish, 1)))
        assertEquals(1, validation.issues.size)
        assertIs<CartIssue.OutOfStock>(validation.issues.first())
    }

    @Test fun quantity_over_current_limit_is_reported() = runTest {
        // p2 (tomato) is LowStock(3) in the catalogue.
        val staleTomato = inStock().copy(id = "p2", name = "Fresh Tomato")
        val validation = repo().validateCart(listOf(line(staleTomato, 5)))
        assertEquals(1, validation.issues.size)
        val issue = validation.issues.first()
        assertIs<CartIssue.QuantityReduced>(issue)
        assertEquals(3, issue.available)
    }

    @Test fun price_drift_is_reported() = runTest {
        val stale = inStock().copy(price = 25)   // catalogue says 29
        val validation = repo().validateCart(listOf(line(stale, 1)))
        assertIs<CartIssue.PriceChanged>(validation.issues.first())
    }

    @Test fun clean_cart_validates_ok() = runTest {
        val validation = repo().validateCart(listOf(line(inStock(), 2)))
        assertTrue(validation.ok)
    }

    @Test fun successful_order_creation() = runTest {
        val result = repo().placeOrder(request(listOf(line(inStock(), 2)), "key-1"))
        assertIs<PlaceOrderResult.Placed>(result)
        assertEquals(false, result.replayed)
        assertEquals(58, result.order.bill.itemTotal)
    }

    @Test fun duplicate_key_replays_same_order_not_a_new_one() = runTest {
        val r = repo()
        val first = r.placeOrder(request(listOf(line(inStock(), 1)), "key-dup"))
        val second = r.placeOrder(request(listOf(line(inStock(), 1)), "key-dup"))
        assertIs<PlaceOrderResult.Placed>(first)
        assertIs<PlaceOrderResult.Placed>(second)
        assertTrue(second.replayed)
        assertEquals(first.order.id, second.order.id)
    }

    @Test fun failure_then_retry_with_same_key_succeeds_once() = runTest {
        val r = repo()
        r.failNextPlacement = true
        val fail = r.placeOrder(request(listOf(line(inStock(), 1)), "key-retry"))
        assertIs<PlaceOrderResult.Failed>(fail)
        assertTrue(fail.retryable)
        val retry = r.placeOrder(request(listOf(line(inStock(), 1)), "key-retry"))
        assertIs<PlaceOrderResult.Placed>(retry)
        assertEquals(false, retry.replayed)   // first real placement
        // and a THIRD tap replays rather than duplicating:
        val third = r.placeOrder(request(listOf(line(inStock(), 1)), "key-retry"))
        assertIs<PlaceOrderResult.Placed>(third)
        assertTrue(third.replayed)
    }

    @Test fun invalid_cart_is_rejected_at_placement() = runTest {
        val staleFish = inStock().copy(id = "p23", name = "Rohu Fish")
        val result = repo().placeOrder(request(listOf(line(staleFish, 1)), "key-bad"))
        assertIs<PlaceOrderResult.Rejected>(result)
    }

    @Test fun unserviceable_address_has_no_slots() = runTest {
        assertTrue(repo().getSlots("addr-3").isEmpty())
        assertTrue(repo().getSlots("addr-1").isNotEmpty())
    }
}

class AddressValidationTest {
    @Test fun rejects_bad_input() {
        assertTrue(Address.validate("", "22 14th Main HSR", "560102") != null)
        assertTrue(Address.validate("Home", "short", "560102") != null)
        assertTrue(Address.validate("Home", "22, 14th Main, HSR", "5601") != null)
        assertTrue(Address.validate("Home", "22, 14th Main, HSR", "56010a") != null)
    }
    @Test fun accepts_good_input() {
        assertEquals(null, Address.validate("Home", "22, 14th Main, HSR Layout", "560102"))
    }
}
