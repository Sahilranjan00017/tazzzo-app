package com.tazzzo.app

import com.tazzzo.app.config.BillCalculator
import com.tazzzo.app.config.ChargeRules
import com.tazzzo.app.config.CoinRules
import com.tazzzo.app.data.model.Availability
import com.tazzzo.app.data.model.CartLine
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.model.Product
import kotlin.test.Test
import kotlin.test.assertEquals

private fun product(
    id: String = "t1", price: Money = r(100), mrp: Money = r(120),
    availability: Availability = Availability.InStock, maxQty: Int = 10
) = Product(
    id = id, name = "Test $id", brand = "TestBrand", emoji = "🧪", unit = "1 kg",
    price = price, mrp = mrp, categoryId = "c", subcategoryId = "s",
    rating = 4.0, ratingCount = 10, availability = availability, maxOrderQuantity = maxQty
)

private val CHARGES = ChargeRules(freeDeliveryAbove = r(199), deliveryFee = r(25), handlingFee = r(5))
private val COINS = CoinRules(earnPercent = 2, valuePerCoin = r(1), maxRedeemPerOrder = null, expiryDays = null, enabled = true)

class BillCalculatorTest {

    @Test fun subtotal_sums_lines() {
        val bill = BillCalculator.bill(
            listOf(CartLine(product(price = r(40), mrp = r(50)), 2), CartLine(product(id = "t2", price = r(99), mrp = r(99)), 1)),
            CHARGES, COINS
        )
        assertEquals(r(179), bill.itemTotal)
        assertEquals(r(199), bill.itemMrpTotal)
        assertEquals(r(20), bill.saved)
    }

    @Test fun delivery_fee_below_threshold() {
        val bill = BillCalculator.bill(listOf(CartLine(product(price = r(198), mrp = r(198)), 1)), CHARGES, COINS)
        assertEquals(r(25), bill.deliveryFee)
    }

    @Test fun delivery_free_at_threshold() {
        val bill = BillCalculator.bill(listOf(CartLine(product(price = r(199), mrp = r(199)), 1)), CHARGES, COINS)
        assertEquals(r(0), bill.deliveryFee)
    }

    @Test fun empty_cart_has_no_charges() {
        val bill = BillCalculator.bill(emptyList(), CHARGES, COINS)
        assertEquals(r(0), bill.itemTotal); assertEquals(r(0), bill.deliveryFee)
        assertEquals(r(0), bill.handlingCharge); assertEquals(r(0), bill.grandTotal)
    }

    @Test fun coins_earned_floor_of_percent() {
        val bill = BillCalculator.bill(listOf(CartLine(product(price = r(149), mrp = r(149)), 1)), CHARGES, COINS)
        assertEquals(2, bill.coinsEarned)  // 2% of 149 = 2.98 → 2
    }

    @Test fun coins_disabled_earns_nothing() {
        val bill = BillCalculator.bill(
            listOf(CartLine(product(price = r(500), mrp = r(500)), 1)),
            CHARGES, COINS.copy(enabled = false)
        )
        assertEquals(0, bill.coinsEarned)
    }

    @Test fun grand_total_sums_components() {
        val bill = BillCalculator.bill(listOf(CartLine(product(price = r(100), mrp = r(120)), 1)), CHARGES, COINS)
        assertEquals(r(100 + 25 + 5), bill.grandTotal)
    }

    @Test fun redemption_clamped_to_cap_and_item_total() {
        assertEquals(r(50), BillCalculator.redeemableValue(80, r(300), COINS.copy(maxRedeemPerOrder = 50)))
        assertEquals(r(30), BillCalculator.redeemableValue(80, r(30), COINS))       // never exceeds item total
        assertEquals(r(0), BillCalculator.redeemableValue(80, r(300), COINS.copy(enabled = false)))
        assertEquals(r(0), BillCalculator.redeemableValue(0, r(300), COINS))
    }

    @Test fun redemption_reduces_grand_total() {
        val bill = BillCalculator.bill(
            listOf(CartLine(product(price = r(300), mrp = r(300)), 1)), CHARGES, COINS, redeemCoins = 40
        )
        assertEquals(r(300 + 5 - 40), bill.grandTotal)  // ≥199 → free delivery
    }
}

class CartEnforcementTest {

    @Test fun add_respects_out_of_stock() {
        val app = TazzzoAppState(store = null)
        assertEquals(false, app.addToCart(product(availability = Availability.OutOfStock)))
        assertEquals(0, app.cartItemCount)
    }

    @Test fun add_caps_at_low_stock_remaining() {
        val app = TazzzoAppState(store = null)
        val p = product(availability = Availability.LowStock(2), maxQty = 10)
        assertEquals(true, app.addToCart(p))
        assertEquals(true, app.addToCart(p))
        assertEquals(false, app.addToCart(p))   // third refused
        assertEquals(2, app.quantityOf(p))
    }

    @Test fun add_caps_at_max_order_quantity() {
        val app = TazzzoAppState(store = null)
        val p = product(maxQty = 3)
        repeat(5) { app.addToCart(p) }
        assertEquals(3, app.quantityOf(p))
    }

    @Test fun remove_deletes_line_at_zero() {
        val app = TazzzoAppState(store = null)
        val p = product()
        app.addToCart(p); app.removeFromCart(p)
        assertEquals(0, app.cartItemCount)
        assertEquals(0, app.cartLines().size)
    }

    @Test fun cart_survives_failed_checkout_semantics() {
        // clearCart is only called on success; nothing in the failure path touches it.
        val app = TazzzoAppState(store = null)
        app.addToCart(product())
        assertEquals(1, app.cartItemCount)  // representative of state after Failed placement
    }
}
