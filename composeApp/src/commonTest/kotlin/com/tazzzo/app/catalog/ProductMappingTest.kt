package com.tazzzo.app.catalog

import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.catalog.StockState
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductMappingTest {
    private val pin = Pincode.LAUNCH

    private suspend fun map(card: String) =
        dataSource { respond(pageJson(listOf(card)), HttpStatusCode.OK, JSON) }.products("TZC-000010", pin).items.single()

    @Test fun paiseArePreservedExactly() = runTest {
        val p = map(cardJson(price = 4950, mrp = 5500))
        assertEquals(Money.ofPaise(4950), p.sellingPrice); assertEquals(Money.ofPaise(5500), p.mrp)
        assertEquals(Money.ofPaise(550), p.discountAmount)
        assertEquals(10, p.discountPercent) // server-supplied (floor), not recomputed
        assertEquals("₹49.50", p.sellingPrice.toString())
        assertEquals("TZP-1", p.skuId); assertEquals("BR1", p.brandCode); assertEquals("TZV-000225", p.verticalId)
        assertEquals("https://media.example.test/TZP-1.jpg", p.thumbnailUrl)
    }

    @Test fun serverAmountsAreNotRecomputed() = runTest {
        // Deliberately inconsistent with mrp-selling: the app must pass the server's numbers through.
        val p = map(cardJson(price = 1000, mrp = 2000, extra = "").replace("\"discountPercent\":50", "\"discountPercent\":7")
            .replace("\"discountAmountPaise\":1000", "\"discountAmountPaise\":123"))
        assertEquals(7, p.discountPercent); assertEquals(Money.ofPaise(123), p.discountAmount)
    }

    @Test fun nullPriceMeansUnavailableAndNotBuyableEvenIfTheServerSaidBuyable() = runTest {
        val p = map(cardJson(price = null, mrp = null, buyable = true))
        assertNull(p.sellingPrice); assertNull(p.mrp); assertNull(p.discountPercent); assertNull(p.discountAmount)
        assertFalse(p.hasPrice); assertFalse(p.buyable)
    }

    @Test fun zeroPriceIsAPriceNotAbsence() = runTest {
        val p = map(cardJson(price = 0, mrp = 100))
        assertEquals(Money.ZERO, p.sellingPrice); assertTrue(p.hasPrice); assertTrue(p.buyable)
    }

    @Test fun anegativeAmountIsAContractViolation() = runTest {
        val e = assertFailsWith<ApiException> { map(cardJson(price = -5, mrp = 10)) }
        assertIs<ApiError.Decoding>(e.error)
    }

    @Test fun allFourStockStatesAreDistinct() = runTest {
        assertEquals(StockState.IN_STOCK, map(cardJson(stock = "IN_STOCK")).stockState)
        val low = map(cardJson(stock = "LOW_STOCK")); assertEquals(StockState.LOW_STOCK, low.stockState); assertEquals(3, low.lowStockRemaining)
        assertEquals(StockState.OUT_OF_STOCK, map(cardJson(stock = "OUT_OF_STOCK", buyable = false)).stockState)
        assertEquals(StockState.UNKNOWN, map(cardJson(stock = "UNKNOWN", buyable = false)).stockState)
        assertNull(map(cardJson(stock = "IN_STOCK")).lowStockRemaining)
    }

    @Test fun unknownIsNeverCollapsedIntoOutOfStock() = runTest {
        val p = map(cardJson(stock = "UNKNOWN", buyable = false, serviceable = "null", max = 0))
        assertEquals(StockState.UNKNOWN, p.stockState); assertTrue(p.stockState != StockState.OUT_OF_STOCK)
    }

    @Test fun buyableIsTheServersDecisionNotDerivedFromStock() = runTest {
        // The server is authoritative in both directions; the app never second-guesses it from stockState.
        assertTrue(map(cardJson(stock = "LOW_STOCK", buyable = true)).buyable)
        assertFalse(map(cardJson(stock = "IN_STOCK", buyable = false)).buyable)
        assertFalse(map(cardJson(stock = "OUT_OF_STOCK", buyable = false)).buyable)
    }

    @Test fun serviceableIsNullableAndNullMeansNoLocation() = runTest {
        assertNull(map(cardJson(serviceable = "null", stock = "UNKNOWN", buyable = false, max = 0)).serviceable)
        assertEquals(true, map(cardJson(serviceable = "true")).serviceable)
        assertEquals(false, map(cardJson(serviceable = "false", stock = "UNKNOWN", buyable = false, max = 0)).serviceable)
    }

    @Test fun maxOrderQuantityZeroIsPreserved() = runTest {
        val p = map(cardJson(stock = "UNKNOWN", buyable = false, max = 0))
        assertEquals(0, p.maxOrderQuantity); assertEquals(1, p.minimumOrderQuantity)
    }

    @Test fun absentOptionalFieldsStayAbsent() = runTest {
        val bare = """{"skuId":"TZP-9","productId":"TZP-9","name":"Bare","stockState":"UNKNOWN","maxOrderQuantity":0,"minimumOrderQuantity":1,"buyable":false}"""
        val p = map(bare)
        assertNull(p.brandCode); assertNull(p.thumbnailUrl); assertNull(p.verticalId); assertNull(p.serviceable)
        assertNull(p.sellingPrice); assertFalse(p.buyable)
    }

    @Test fun onlyAbsoluteHttpsImagesAreKept() = runTest {
        assertNull(map(cardJson().replace("https://media.example.test/TZP-1.jpg", "http://x.test/a.jpg")).thumbnailUrl)
        assertNull(map(cardJson().replace("https://media.example.test/TZP-1.jpg", "/relative/a.jpg")).thumbnailUrl)
        assertNull(map(cardJson().replace("https://media.example.test/TZP-1.jpg", "javascript:alert(1)")).thumbnailUrl)
    }

    @Test fun cursorWithoutHasMoreAndHasMoreWithoutCursorAreBothTerminal() = runTest {
        val noMore = dataSource { respond("""{"resolvedReleaseId":"r","items":[],"nextCursor":"C","hasMore":false}""", HttpStatusCode.OK, JSON) }
            .products("TZC-000010", pin)
        assertFalse(noMore.hasMore)
        val noCursor = dataSource { respond("""{"resolvedReleaseId":"r","items":[],"hasMore":true}""", HttpStatusCode.OK, JSON) }
            .products("TZC-000010", pin)
        assertFalse(noCursor.hasMore, "a 'more' with no cursor cannot be followed")
    }
}
