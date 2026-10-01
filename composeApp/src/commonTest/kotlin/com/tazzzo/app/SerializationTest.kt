package com.tazzzo.app

import com.tazzzo.app.data.model.Address
import com.tazzzo.app.data.model.Availability
import com.tazzzo.app.data.model.BillSummary
import com.tazzzo.app.data.model.CartIssue
import com.tazzzo.app.data.model.CartLine
import com.tazzzo.app.data.model.CartValidation
import com.tazzzo.app.data.model.Category
import com.tazzzo.app.data.model.CoinTransaction
import com.tazzzo.app.data.model.DeliverySlot
import com.tazzzo.app.data.model.FaqItem
import com.tazzzo.app.data.model.Order
import com.tazzzo.app.data.model.OrderRequest
import com.tazzzo.app.data.model.OrderStatus
import com.tazzzo.app.data.model.PaymentMethod
import com.tazzzo.app.data.model.PaymentMethodKind
import com.tazzzo.app.data.model.Product
import com.tazzzo.app.data.model.PromoBanner
import com.tazzzo.app.data.model.Subcategory
import com.tazzzo.app.data.model.UserProfile
import com.tazzzo.app.data.repository.MockCatalogRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Round-trip and wire-shape tests for every model that will cross the network.
 *
 * Round-tripping alone is not enough: a symmetric pair of bugs round-trips
 * perfectly and still disagrees with the backend. So the sealed types are also
 * asserted against their literal documented JSON.
 */
class SerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val strict = Json

    private fun product(
        availability: Availability = Availability.InStock,
        emoji: String = "🥛"
    ) = Product(
        id = "p8", name = "Toned Milk Pouch", brand = "Amul", emoji = emoji,
        unit = "500 ml", price = r(29), mrp = r(30), categoryId = "dairy",
        subcategoryId = "milk", rating = 4.7, ratingCount = 8804,
        tags = listOf("Bestseller"), highlights = listOf("Chilled"),
        availability = availability, maxOrderQuantity = 10,
        imageUrl = "https://cdn.tazzzo.in/p/p8.webp"
    )

    private inline fun <reified T> roundTrip(value: T): T =
        json.decodeFromString<T>(json.encodeToString(value))

    // ----------------------------------------------------------- round trips

    @Test fun product_round_trips() = assertEquals(product(), roundTrip(product()))

    @Test fun product_round_trips_in_every_availability() {
        listOf(
            Availability.InStock,
            Availability.LowStock(3),
            Availability.OutOfStock,
            Availability.NotServiceable
        ).forEach { assertEquals(product(it), roundTrip(product(it))) }
    }

    @Test fun category_round_trips() {
        val c = Category(
            "dairy", "Dairy, Bread & Eggs", "🥛", 0xFFFFF8E1, "Grocery & Kitchen",
            listOf(Subcategory("milk", "Milk", "🥛"))
        )
        assertEquals(c, roundTrip(c))
    }

    @Test fun banner_faq_coin_profile_round_trip() {
        val b = PromoBanner("b1", "Save more", "every day", "🎉", dark = true)
        assertEquals(b, roundTrip(b))
        val f = FaqItem("Where is my order?", "Track it from Account.")
        assertEquals(f, roundTrip(f))
        val t = CoinTransaction("c1", "Order cashback", 3, "1 Sep")
        assertEquals(t, roundTrip(t))
        val u = UserProfile("Asha", "9876543210", isGuest = false, coinBalance = 41, address = "HSR")
        assertEquals(u, roundTrip(u))
    }

    @Test fun bill_cart_and_order_round_trip() {
        val bill = BillSummary(r(58), r(60), r(0), r(4), 1, r(62))
        assertEquals(bill, roundTrip(bill))
        val line = CartLine(product(), 2)
        assertEquals(line, roundTrip(line))
        val order = Order(
            "TZ-1", listOf(line), bill, OrderStatus.ON_THE_WAY,
            "Today, 6:20 PM", "HSR Layout", PaymentMethodKind.COD
        )
        assertEquals(order, roundTrip(order))
    }

    @Test fun order_without_payment_round_trips() {
        // payment is nullable so historical orders stay valid.
        val order = Order(
            "TZ-0", emptyList(), BillSummary(r(0), r(0), r(0), r(0), 0, r(0)),
            OrderStatus.DELIVERED, "Yesterday", "HSR", payment = null
        )
        assertEquals(order, roundTrip(order))
    }

    @Test fun checkout_models_round_trip() {
        val a = Address("a1", "Home", "22, 14th Main, HSR", "", "560102", isServiceable = true)
        assertEquals(a, roundTrip(a))
        val s = DeliverySlot("s1", "Today, 6–8 PM", available = true, etaMinutes = null)
        assertEquals(s, roundTrip(s))
        val m = PaymentMethod(PaymentMethodKind.UPI, "UPI", enabled = false, note = "Coming soon")
        assertEquals(m, roundTrip(m))
        val r = OrderRequest(
            "chk-123456789012", listOf(CartLine(product(), 1)), BillSummary(r(29), r(30), r(0), r(4), 0, r(33)),
            "a1", "Home — 22, 14th Main", "s1", PaymentMethodKind.COD
        )
        assertEquals(r, roundTrip(r))
    }

    @Test fun cart_validation_round_trips_every_issue_type() {
        val v = CartValidation(
            listOf(
                CartIssue.OutOfStock("p23", "Rohu Fish"),
                CartIssue.QuantityReduced("p2", "Fresh Tomato", requested = 5, available = 3),
                CartIssue.PriceChanged("p8", "Toned Milk", oldPrice = r(29), newPrice = r(32))
            )
        )
        assertEquals(v, roundTrip(v))
        assertEquals(false, roundTrip(v).ok)
    }

    @Test fun whole_mock_catalogue_round_trips() = runTest {
        // The strongest available round-trip: every real fixture, not a sample.
        val repo = MockCatalogRepository()
        val categories = repo.getCategories()
        assertEquals(categories, roundTrip(categories))
        val products = categories.flatMap { repo.getProducts(it.id) }
        assertTrue(products.size > 50, "expected the full fixture catalogue")
        assertEquals(products, roundTrip(products))
    }

    // ------------------------------------------------- documented wire shapes

    @Test fun availability_uses_the_documented_flat_shape() {
        assertEquals("""{"status":"IN_STOCK"}""", strict.encodeToString<Availability>(Availability.InStock))
        assertEquals(
            """{"status":"LOW_STOCK","remaining":3}""",
            strict.encodeToString<Availability>(Availability.LowStock(3))
        )
        assertEquals(
            """{"status":"OUT_OF_STOCK"}""",
            strict.encodeToString<Availability>(Availability.OutOfStock)
        )
        assertEquals(
            """{"status":"NOT_SERVICEABLE"}""",
            strict.encodeToString<Availability>(Availability.NotServiceable)
        )
    }

    @Test fun availability_decodes_the_documented_flat_shape() {
        assertEquals(
            Availability.LowStock(2),
            strict.decodeFromString<Availability>("""{"status":"LOW_STOCK","remaining":2}""")
        )
        assertEquals(
            Availability.OutOfStock,
            strict.decodeFromString<Availability>("""{"status":"OUT_OF_STOCK"}""")
        )
    }

    @Test fun cart_issue_uses_the_documented_flat_shape() {
        assertEquals(
            """{"type":"OUT_OF_STOCK","productId":"p23","productName":"Rohu Fish"}""",
            strict.encodeToString<CartIssue>(CartIssue.OutOfStock("p23", "Rohu Fish"))
        )
        assertEquals(
            """{"type":"QUANTITY_REDUCED","productId":"p2","productName":"Tomato","requested":5,"available":3}""",
            strict.encodeToString<CartIssue>(
                CartIssue.QuantityReduced("p2", "Tomato", requested = 5, available = 3)
            )
        )
        assertEquals(
            """{"type":"PRICE_CHANGED","productId":"p8","productName":"Milk","oldPricePaise":2900,"newPricePaise":3200}""",
            strict.encodeToString<CartIssue>(
                CartIssue.PriceChanged("p8", "Milk", oldPrice = r(29), newPrice = r(32))
            )
        )
    }

    // ------------------------------------------------------- failure handling

    @Test fun unknown_availability_status_fails_loudly() {
        assertFailsWith<SerializationException> {
            strict.decodeFromString<Availability>("""{"status":"BACKORDERED"}""")
        }
    }

    @Test fun low_stock_without_a_count_is_rejected() {
        // "A bit in stock, amount unknown" must never become a purchasable state.
        assertFailsWith<SerializationException> {
            strict.decodeFromString<Availability>("""{"status":"LOW_STOCK"}""")
        }
    }

    @Test fun price_changed_without_prices_is_rejected() {
        assertFailsWith<SerializationException> {
            strict.decodeFromString<CartIssue>(
                """{"type":"PRICE_CHANGED","productId":"p8","productName":"Milk"}"""
            )
        }
    }

    @Test fun unknown_cart_issue_type_fails_loudly() {
        assertFailsWith<SerializationException> {
            strict.decodeFromString<CartIssue>(
                """{"type":"RECALLED","productId":"p8","productName":"Milk"}"""
            )
        }
    }

    // --------------------------------------------------------- specific fields

    @Test fun product_deserializes_without_an_emoji() {
        // emoji is a temporary placeholder; a payload that omits it must not
        // take the whole catalogue down.
        val body = """
            {"id":"p1","name":"Atta","brand":"Shudh","unit":"5 kg","price":249,
             "mrp":299,"categoryId":"atta","subcategoryId":"atta-s",
             "rating":4.5,"ratingCount":100}
        """.trimIndent()
        val p = json.decodeFromString<Product>(body)
        assertEquals("", p.emoji)
        assertEquals(Availability.InStock, p.availability)
        assertEquals(null, p.imageUrl)
    }

    @Test fun money_is_encoded_as_integer_rupees() {
        val obj = strict.encodeToString(BillSummary(r(58), r(60), r(0), r(4), 1, r(62))).let {
            strict.parseToJsonElement(it).jsonObject
        }
        listOf("itemTotal", "itemMrpTotal", "deliveryFee", "handlingCharge", "grandTotal")
            .forEach { key ->
                val raw = obj.getValue(key).jsonPrimitive.content
                assertTrue(raw.toIntOrNull() != null, "$key must be an integer, was '$raw'")
                assertTrue(!raw.contains('.'), "$key must not be a decimal")
            }
    }

    @Test fun derived_values_are_not_part_of_the_payload() {
        // discountPercent / purchasableLimit / saved / lineTotal / ok are
        // computed on the client and must never be sent or expected.
        val encoded = strict.encodeToString(product())
        listOf("discountPercent", "purchasableLimit", "isPurchasable").forEach {
            assertTrue(!encoded.contains(it), "$it leaked into the Product payload")
        }
        assertTrue(!strict.encodeToString(BillSummary(r(1), r(2), r(0), r(0), 0, r(1))).contains("saved"))
        assertTrue(!strict.encodeToString(CartLine(product(), 1)).contains("lineTotal"))
        assertTrue(!strict.encodeToString(CartValidation(emptyList())).contains("\"ok\""))
    }

    @Test fun order_status_encodes_only_the_four_known_states() {
        OrderStatus.entries.forEach { assertEquals(it, roundTrip(it)) }
        assertEquals("\"ON_THE_WAY\"", strict.encodeToString(OrderStatus.ON_THE_WAY))
        assertEquals(4, OrderStatus.entries.size)
    }
}
