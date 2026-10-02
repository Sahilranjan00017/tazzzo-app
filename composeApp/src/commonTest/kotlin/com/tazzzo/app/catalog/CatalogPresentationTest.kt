package com.tazzzo.app.catalog

import com.tazzzo.app.data.catalog.BannerTone
import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.catalog.PurchaseAction
import com.tazzzo.app.data.catalog.RetryPolicy
import com.tazzzo.app.data.catalog.ServiceabilityResult
import com.tazzzo.app.data.catalog.ServiceabilityState
import com.tazzzo.app.data.catalog.StockState
import com.tazzzo.app.data.catalog.StockTone
import com.tazzzo.app.data.catalog.banner
import com.tazzzo.app.data.catalog.discountPercentLabel
import com.tazzzo.app.data.catalog.displayValue
import com.tazzzo.app.data.catalog.etaLabel
import com.tazzzo.app.data.catalog.hint
import com.tazzzo.app.data.catalog.mrpLabel
import com.tazzzo.app.data.catalog.priceLabel
import com.tazzzo.app.data.catalog.purchaseAction
import com.tazzzo.app.data.catalog.savingLabel
import com.tazzzo.app.data.catalog.stockLabel
import com.tazzzo.app.data.catalog.title
import com.tazzzo.app.data.model.Money
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogPresentationTest {
    private val remote = CatalogCapabilities.REMOTE
    private val pin = Pincode.LAUNCH

    // ---- money display -----------------------------------------------------------------------------------

    @Test fun fortyNineFiftyDisplaysAsFortyNineFifty() {
        val p = cp(price = Money.ofPaise(4_950), mrp = Money.ofPaise(5_500))
        assertEquals("₹49.50", p.priceLabel())
        assertEquals("₹55", p.mrpLabel())
    }

    @Test fun thereIsNoHundredFoldConversionRegression() {
        assertNotEquals("₹4,950", cp(price = Money.ofPaise(4_950)).priceLabel())
        assertNotEquals("₹0.49", cp(price = Money.ofPaise(4_950)).priceLabel())
        assertEquals("₹4,950", cp(price = Money.ofPaise(495_000)).priceLabel())
        assertEquals("₹1", cp(price = Money.ofPaise(100)).priceLabel())
    }

    @Test fun noPriceMeansNoLabelNeverAFakeZero() {
        assertNull(cp(price = null, mrp = null).priceLabel())
        assertEquals("₹0", cp(price = Money.ZERO, mrp = null).priceLabel())      // a real free price is shown
    }

    @Test fun mrpIsShownOnlyWhenBothExistAndItReallyIsHigher() {
        assertNull(cp(price = null, mrp = Money.ofPaise(5_500)).mrpLabel())
        assertNull(cp(price = Money.ofPaise(4_950), mrp = null).mrpLabel())
        assertNull(cp(price = Money.ofPaise(4_950), mrp = Money.ofPaise(4_950)).mrpLabel())
        assertNull(cp(price = Money.ofPaise(5_000), mrp = Money.ofPaise(4_950)).mrpLabel())
    }

    @Test fun discountsComeFromTheServerAndAreNeverRecomputed() {
        // Deliberately not mrp - price: the labels must echo the server's numbers.
        val p = cp(price = Money.ofPaise(1_000), mrp = Money.ofPaise(2_000), discountPercent = 7, discountAmount = Money.ofPaise(123))
        assertEquals("7% off", p.discountPercentLabel())
        assertEquals("You save ₹1.23", p.savingLabel())
        assertNull(cp(discountPercent = null, discountAmount = null).discountPercentLabel())
        assertNull(cp(discountPercent = null, discountAmount = null).savingLabel())
        assertNull(cp(discountPercent = 0, discountAmount = Money.ZERO).discountPercentLabel())
    }

    // ---- stock: UNKNOWN is its own thing ----------------------------------------------------------------------

    @Test fun allFourStockStatesHaveDistinctWordsAndTones() {
        val inStock = cp(stock = StockState.IN_STOCK).stockLabel()
        val low = cp(stock = StockState.LOW_STOCK, low = 3).stockLabel()
        val oos = cp(stock = StockState.OUT_OF_STOCK, buyable = false).stockLabel()
        val unknown = cp(stock = StockState.UNKNOWN, buyable = false, serviceable = null).stockLabel()
        assertEquals("In stock" to StockTone.Available, inStock.text to inStock.tone)
        assertEquals("Only 3 left" to StockTone.Scarce, low.text to low.tone)
        assertEquals("Out of stock" to StockTone.Unavailable, oos.text to oos.tone)
        assertEquals(StockTone.Unknown, unknown.tone)
        assertEquals(4, setOf(inStock.text, low.text, oos.text, unknown.text).size)
        assertEquals(4, setOf(inStock.tone, low.tone, oos.tone, unknown.tone).size)
    }

    @Test fun unknownIsNeverWordedLikeOutOfStock() {
        val u = cp(stock = StockState.UNKNOWN, buyable = false).stockLabel()
        assertFalse(u.text.contains("Out of stock", ignoreCase = true))
        assertNotEquals(cp(stock = StockState.OUT_OF_STOCK, buyable = false).stockLabel(), u)
    }

    @Test fun unknownDistinguishesAnUnservedLocationFromMissingData() {
        assertEquals("Not available at your location", cp(stock = StockState.UNKNOWN, serviceable = false, buyable = false).stockLabel().text)
        assertEquals("Availability unknown", cp(stock = StockState.UNKNOWN, serviceable = null, buyable = false).stockLabel().text)
    }

    @Test fun lowStockWithoutACountStillSaysSomethingTrue() {
        assertEquals("Low stock", cp(stock = StockState.LOW_STOCK, low = null).stockLabel().text)
    }

    // ---- buyable + the cart guard --------------------------------------------------------------------------------

    @Test fun buyableIsTheOnlySignalStockIsNotConsulted() {
        // stock says OUT_OF_STOCK but the server says buyable: the action is decided by buyable (and the cart guard), not stock.
        val a = purchaseAction(cp(stock = StockState.OUT_OF_STOCK, buyable = true), CatalogCapabilities.MOCK)
        assertEquals(PurchaseAction.Enabled, a)
        // stock says IN_STOCK but buyable=false: unavailable.
        val b = purchaseAction(cp(stock = StockState.IN_STOCK, buyable = false), CatalogCapabilities.MOCK)
        assertEquals(PurchaseAction.Reason.NotBuyable, assertIs<PurchaseAction.Disabled>(b).reason)
    }

    @Test fun noPriceMeansNothingToBuyEvenIfBuyable() {
        val a = purchaseAction(cp(price = null, mrp = null, buyable = true), CatalogCapabilities.MOCK)
        assertEquals(PurchaseAction.Reason.PriceUnavailable, assertIs<PurchaseAction.Disabled>(a).reason)
    }

    @Test fun inRemoteModeNoRealProductEverGetsAnEnabledPurchaseAction() {
        val stocks = StockState.entries
        for (stock in stocks) for (buyable in listOf(true, false)) for (price in listOf<Money?>(null, Money.ofPaise(4_950), Money.ZERO))
            for (serviceable in listOf<Boolean?>(null, true, false)) {
                val a = purchaseAction(cp(price = price, stock = stock, buyable = buyable, serviceable = serviceable), remote)
                assertIs<PurchaseAction.Disabled>(a, "stock=$stock buyable=$buyable price=$price")
            }
    }

    @Test fun aBuyableRemoteProductIsDisabledOnlyBecauseThereIsNoServerCart() {
        val a = assertIs<PurchaseAction.Disabled>(purchaseAction(cp(buyable = true), remote))
        assertEquals(PurchaseAction.Reason.CartNotAvailable, a.reason)
        assertEquals("Ordering isn't available yet", a.label)
    }

    @Test fun theCartFlagIsOffForRemoteAndTheRemoteCatalogueEnablesNothingElse() {
        assertFalse(remote.cartIntegration)
        assertFalse(remote.search || remote.deals || remote.bestsellers || remote.banners || remote.counts || remote.sorting)
    }

    // ---- serviceability: all five states, no invented ETA ------------------------------------------------------------

    private fun result(eta: Pair<Int?, Int?> = null to null, ok: Boolean = true) =
        ServiceabilityResult(ok, "SA-1", 1, eta.first, eta.second)

    @Test fun unknownShowsNoBanner() = assertNull(ServiceabilityState.Unknown.banner(pin))

    @Test fun loading() {
        val b = ServiceabilityState.Loading.banner(pin)!!
        assertEquals("Checking delivery to 560047…", b.text); assertEquals(BannerTone.Neutral, b.tone); assertFalse(b.retryable)
    }

    @Test fun serviceableWithoutAnEtaInventsNone() {
        val b = ServiceabilityState.Serviceable(result()).banner(pin)!!
        assertEquals("Delivering to 560047", b.text); assertEquals(BannerTone.Positive, b.tone)
        assertFalse(b.text.contains("min"))
    }

    @Test fun serviceableWithAnEtaShowsExactlyWhatWasSent() {
        assertEquals("Delivering to 560047 · 20–35 min", ServiceabilityState.Serviceable(result(20 to 35)).banner(pin)!!.text)
        assertEquals("Delivering to 560047 · 30 min", ServiceabilityState.Serviceable(result(30 to 30)).banner(pin)!!.text)
        assertEquals("Delivering to 560047 · 25 min", ServiceabilityState.Serviceable(result(25 to null)).banner(pin)!!.text)
    }

    @Test fun notServiceable() {
        val b = ServiceabilityState.NotServiceable(result(ok = false)).banner(pin)!!
        assertEquals("We don't deliver to 560047 yet", b.text); assertEquals(BannerTone.Warning, b.tone); assertFalse(b.retryable)
    }

    @Test fun failedIsRetryableForTransientFailuresOnly() {
        val transient = ServiceabilityState.Failed(CatalogFailure.Network).banner(pin)!!
        assertEquals(BannerTone.Error, transient.tone); assertTrue(transient.retryable)
        assertFalse(ServiceabilityState.Failed(CatalogFailure.InvalidRequest).banner(pin)!!.retryable)
    }

    @Test fun etaLabelIsNullWhenAbsent() = assertNull(result().etaLabel())

    // ---- failures + retry policy ---------------------------------------------------------------------------------------

    @Test fun failureCopyIsAppWrittenAndNeverLeaksCodesOrStatus() {
        val all = listOf(CatalogFailure.Network, CatalogFailure.Timeout, CatalogFailure.RateLimited(5), CatalogFailure.Unavailable,
            CatalogFailure.Server, CatalogFailure.NotFound, CatalogFailure.InvalidRequest, CatalogFailure.InvalidCursor, CatalogFailure.Unknown)
        for (f in all) for (text in listOf(f.title, f.hint)) {
            assertTrue(text.isNotBlank())
            for (bad in listOf("429", "503", "404", "400", "500", "INVALID", "RATE_LIMITED", "SERVICE_UNAVAILABLE", "NOT_FOUND", "req_")) {
                assertFalse(text.contains(bad), "'$text' contains '$bad'")
            }
        }
    }

    @Test fun rateLimitedHonoursRetryAfterWithinSaneBounds() {
        assertEquals(12, RetryPolicy.waitSeconds(CatalogFailure.RateLimited(12), 1))
        assertEquals(RetryPolicy.DEFAULT_RATE_LIMIT_WAIT_SECONDS, RetryPolicy.waitSeconds(CatalogFailure.RateLimited(null), 1))
        assertEquals(RetryPolicy.MAX_RATE_LIMIT_WAIT_SECONDS, RetryPolicy.waitSeconds(CatalogFailure.RateLimited(99_999), 1))
        assertEquals(1, RetryPolicy.waitSeconds(CatalogFailure.RateLimited(0), 1))
    }

    @Test fun serviceUnavailableBacksOffExponentiallyToACap() {
        assertEquals(listOf(2, 4, 8, 16, 30, 30), (1..6).map { RetryPolicy.waitSeconds(CatalogFailure.Unavailable, it) })
        assertEquals(RetryPolicy.waitSeconds(CatalogFailure.Unavailable, 3), RetryPolicy.waitSeconds(CatalogFailure.Server, 3))
        assertEquals(2, RetryPolicy.waitSeconds(CatalogFailure.Unavailable, 0))      // never below the first step
    }

    @Test fun aLostConnectionMayRetryAtOnce() {
        assertEquals(0, RetryPolicy.waitSeconds(CatalogFailure.Network, 5))
        assertEquals(0, RetryPolicy.waitSeconds(CatalogFailure.Timeout, 5))
    }

    @Test fun nonTransientFailuresAreNotRetryable() {
        assertFalse(CatalogFailure.NotFound.isRetryable); assertFalse(CatalogFailure.InvalidRequest.isRetryable)
        assertTrue(CatalogFailure.RateLimited(3).isRetryable && CatalogFailure.Unavailable.isRetryable)
    }

    // ---- attributes ------------------------------------------------------------------------------------------------------------

    @Test fun attributesRenderOnlyWhatHasASensibleOneLineForm() {
        assertEquals("500 g", attr("weight", JsonPrimitive(500), "g").displayValue())
        assertEquals("Yes", attr("veg", JsonPrimitive(true)).displayValue())
        assertEquals("No", attr("veg", JsonPrimitive(false)).displayValue())
        assertEquals("Amul", attr("brand", JsonPrimitive("Amul")).displayValue())
        assertEquals("a, b", attr("tags", JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b")))).displayValue())
        assertNull(attr("nested", JsonObject(mapOf("x" to JsonPrimitive(1)))).displayValue())
        assertNull(attr("nothing", JsonNull).displayValue())
        assertNull(attr("blank", JsonPrimitive("  ")).displayValue())
    }
}
