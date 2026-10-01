package com.tazzzo.app.catalog

import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.CatalogProductDetail
import com.tazzzo.app.data.catalog.PdpState
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.catalog.ProductDetailHolder
import com.tazzzo.app.data.catalog.StockState
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductDetailHolderTest {
    private class Backend {
        val calls = mutableListOf<Pair<String, String?>>()
        var answer: (String) -> CatalogProductDetail? = { id -> detailOf(cp(product = id)) }
        var error: Throwable? = null
        val gates = HashMap<String, CompletableDeferred<Unit>>()
    }

    private fun TestScope.holder(b: Backend, pins: MutableStateFlow<Pincode> = MutableStateFlow(Pincode.LAUNCH)) =
        ProductDetailHolder(backgroundScope, { id, pin ->
            b.calls += id to pin?.value
            b.gates[id]?.await()
            b.error?.let { throw it }
            b.answer(id)
        }, pins)

    @Test fun successLoadsTheProductForTheCurrentPin() = runTest {
        val b = Backend(); val h = holder(b)
        h.open("TZP-1"); assertEquals(PdpState.Loading, h.state.value); runCurrent()
        assertEquals(listOf<Pair<String, String?>>("TZP-1" to "560047"), b.calls)
        assertEquals("TZP-1", assertIs<PdpState.Content>(h.state.value).detail.product.productId)
    }

    @Test fun aMissingProductIsNotFoundNotAFailure() = runTest {
        val b = Backend().apply { answer = { null } }; val h = holder(b)
        h.open("TZP-404"); runCurrent()
        assertEquals(PdpState.NotFound, h.state.value)
    }

    @Test fun failuresAreTypedAndRetryable() = runTest {
        val b = Backend().apply { error = ApiException(ApiError.Http(503, "SERVICE_UNAVAILABLE")) }; val h = holder(b)
        h.open("TZP-1"); runCurrent()
        assertEquals(PdpState.Failed(CatalogFailure.Unavailable), h.state.value)
        b.error = null; h.retry(); runCurrent()
        assertIs<PdpState.Content>(h.state.value); assertEquals(2, b.calls.size)
    }

    @Test fun retryIsANoOpUnlessFailed() = runTest {
        val b = Backend(); val h = holder(b); h.retry(); h.open("TZP-1"); runCurrent(); h.retry(); runCurrent()
        assertEquals(1, b.calls.size)
    }

    @Test fun absentFieldsStayAbsentAndPriceUnavailableIsRepresentedHonestly() = runTest {
        val bare = cp(price = null, mrp = null, discountPercent = null, discountAmount = null, stock = StockState.UNKNOWN,
            serviceable = null, buyable = false, max = 0, thumb = null)
        val b = Backend().apply { answer = { detailOf(bare, gallery = emptyList(), attributes = emptyList()) } }; val h = holder(b)
        h.open("TZP-1"); runCurrent()
        val d = assertIs<PdpState.Content>(h.state.value).detail
        assertNull(d.product.sellingPrice); assertFalse(d.product.buyable); assertTrue(d.gallery.isEmpty()); assertTrue(d.attributes.isEmpty())
        assertNull(d.product.thumbnailUrl)
    }

    @Test fun galleryUrlsAreRetainedInState() = runTest {
        val b = Backend(); val h = holder(b)
        h.open("TZP-1"); runCurrent()
        val d = assertIs<PdpState.Content>(h.state.value).detail
        assertEquals(listOf("https://media.example.test/a-0.jpg", "https://media.example.test/a-1.jpg"), d.gallery.map { it.url })
        assertEquals("https://media.example.test/a.jpg", d.product.thumbnailUrl)
    }

    @Test fun navigatingToAnotherProductDiscardsTheLateAnswerForTheFirst() = runTest {
        val b = Backend().apply { gates["TZP-1"] = CompletableDeferred() }
        val h = holder(b)
        h.open("TZP-1"); runCurrent()
        h.open("TZP-2"); runCurrent()
        b.gates.getValue("TZP-1").complete(Unit); runCurrent()
        assertEquals("TZP-2", assertIs<PdpState.Content>(h.state.value).detail.product.productId)
    }

    @Test fun aPinChangeReloadsTheOpenProductForTheNewPin() = runTest {
        val pins = MutableStateFlow(Pincode.LAUNCH); val b = Backend(); val h = holder(b, pins)
        h.open("TZP-1"); runCurrent()
        pins.value = Pincode.parse("560102")!!; runCurrent()
        assertEquals(listOf<Pair<String, String?>>("TZP-1" to "560047", "TZP-1" to "560102"), b.calls)
        assertIs<PdpState.Content>(h.state.value)
    }

    @Test fun aPinChangeBeforeAnyProductIsOpenDoesNothing() = runTest {
        val pins = MutableStateFlow(Pincode.LAUNCH); val b = Backend(); holder(b, pins)
        pins.value = Pincode.parse("560102")!!; runCurrent()
        assertTrue(b.calls.isEmpty())
    }

    @Test fun reopeningTheSameProductDoesNotRefetch() = runTest {
        val b = Backend(); val h = holder(b)
        h.open("TZP-1"); runCurrent(); h.open("TZP-1"); runCurrent()
        assertEquals(1, b.calls.size)
    }
}
