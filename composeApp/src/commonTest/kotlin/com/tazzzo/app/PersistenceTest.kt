package com.tazzzo.app

import com.tazzzo.app.data.local.CartRestore
import com.tazzzo.app.data.local.PersistentStore
import com.tazzzo.app.data.model.Availability
import com.tazzzo.app.data.model.Product
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

private fun product(
    id: String, price: Int = 100,
    availability: Availability = Availability.InStock, maxQty: Int = 10
) = Product(
    id = id, name = "Item $id", brand = "B", emoji = "🧪", unit = "1 kg",
    price = price, mrp = price + 20, categoryId = "c", subcategoryId = "s",
    rating = 4.0, ratingCount = 5, availability = availability, maxOrderQuantity = maxQty
)

class CartRestoreTest {

    private fun saved(id: String, qty: Int, price: Int) =
        PersistentStore.SavedCartLine(id, qty, price)

    @Test fun clean_restore_keeps_lines_and_reports_nothing() {
        val out = CartRestore.reconcile(listOf(saved("a", 2, 100))) { product(it) }
        assertEquals(1, out.restored.size)
        assertEquals(2, out.restored.first().second)
        assertNull(out.notice())
    }

    @Test fun missing_product_is_removed_and_disclosed() {
        val out = CartRestore.reconcile(listOf(saved("gone", 1, 50))) { null }
        assertTrue(out.restored.isEmpty())
        assertTrue(out.notice()!!.contains("no longer available"))
    }

    @Test fun out_of_stock_product_is_removed_and_disclosed() {
        val out = CartRestore.reconcile(listOf(saved("a", 1, 100))) {
            product(it, availability = Availability.OutOfStock)
        }
        assertTrue(out.restored.isEmpty())
        assertTrue(out.notice()!!.contains("no longer available"))
    }

    @Test fun quantity_clamped_to_current_stock_and_disclosed() {
        val out = CartRestore.reconcile(listOf(saved("a", 5, 100))) {
            product(it, availability = Availability.LowStock(2))
        }
        assertEquals(2, out.restored.first().second)
        assertTrue(out.notice()!!.contains("quantity reduced"))
    }

    @Test fun price_drift_restores_at_current_price_and_discloses() {
        val out = CartRestore.reconcile(listOf(saved("a", 1, 80))) { product(it, price = 95) }
        assertEquals(95, out.restored.first().first.price)   // current price wins
        assertTrue(out.notice()!!.contains("prices updated"))
    }

    @Test fun zero_or_negative_quantities_are_dropped() {
        val out = CartRestore.reconcile(listOf(saved("a", 0, 100), saved("b", -2, 100))) { product(it) }
        assertTrue(out.restored.isEmpty())
    }
}

class PersistentStoreTest {

    private fun store() = PersistentStore(MapSettings())

    @Test fun cart_round_trip() {
        val st = store()
        st.saveCart(listOf(PersistentStore.SavedCartLine("p1", 2, 29)))
        assertEquals(listOf(PersistentStore.SavedCartLine("p1", 2, 29)), st.loadCart())
        st.clearCart()
        assertTrue(st.loadCart().isEmpty())
    }

    @Test fun session_round_trip() {
        val st = store()
        st.saveSession(PersistentStore.SavedSession("Asha", "+91 9", false, 46, "HSR"))
        assertEquals("Asha", st.loadSession()!!.name)
        assertEquals(46, st.loadSession()!!.coinBalance)
    }

    @Test fun searches_are_bounded_to_eight() {
        val st = store()
        st.saveRecentSearches((1..20).map { "q$it" })
        assertEquals(8, st.loadRecentSearches().size)
    }

    @Test fun corrupt_payload_degrades_to_empty_not_crash() {
        val settings = MapSettings()
        settings.putString("tazzzo.cart.v1", "{definitely not json]")
        assertTrue(PersistentStore(settings).loadCart().isEmpty())
    }

    @Test fun addresses_bounded_to_twenty() {
        val st = store()
        st.saveAddresses((1..30).map {
            PersistentStore.SavedAddress("a$it", "L", "Line one long enough", "", "560102", true)
        })
        assertEquals(20, st.loadAddresses().size)
    }
}
