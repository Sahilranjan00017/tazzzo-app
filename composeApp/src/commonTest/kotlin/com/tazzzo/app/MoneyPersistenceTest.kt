package com.tazzzo.app

import com.russhwolf.settings.MapSettings
import com.tazzzo.app.data.local.PersistentStore
import com.tazzzo.app.data.model.MembershipStatus
import com.tazzzo.app.data.model.Money
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PR-04B persistence versioning.
 *
 *  - Cart: v1 stored whole RUPEES against MOCK product ids. It is never read as paise and never
 *    converted: it is discarded once and the customer is told. v2 stores integer paise.
 *  - Membership: v1 stored whole rupees under `...Rupees` names. Whole rupees convert to paise
 *    exactly (x100), so it IS migrated losslessly, once.
 */
class MoneyPersistenceTest {
    private val v1Cart = """[{"id":"p8","qty":2,"priceAtSave":29}]"""
    private val v1Membership = """{"status":"ACTIVE","planId":"club","cumulativeSpendRupees":1600,"cumulativeSavingsRupees":80,"eligibleOrderCount":2,"countedOrderIds":["TZ-1","TZ-2"]}"""

    // ---- cart ------------------------------------------------------------------------------------

    @Test fun cartRoundTripsIntegerPaiseUnderTheV2Key() {
        val settings = MapSettings(); val st = PersistentStore(settings)
        st.saveCart(listOf(PersistentStore.SavedCartLine("TZP-1", 3, 4_950L)))
        assertEquals(listOf(PersistentStore.SavedCartLine("TZP-1", 3, 4_950L)), st.loadCart())
        assertTrue("tazzzo.cart.v2" in settings.keys && "tazzzo.cart.v1" !in settings.keys)
        assertTrue(settings.getString("tazzzo.cart.v2", "").contains("priceAtSavePaise"))
    }

    @Test fun anObsoleteRupeeCartIsNeverReadAsPaise() {
        val settings = MapSettings().apply { putString("tazzzo.cart.v1", v1Cart) }
        val st = PersistentStore(settings)
        assertTrue(st.loadCart().isEmpty(), "29 rupees must not surface as 29 paise")
    }

    @Test fun anObsoleteCartIsDiscardedOnceAndReported() {
        val settings = MapSettings().apply { putString("tazzzo.cart.v1", v1Cart) }
        val st = PersistentStore(settings)
        assertTrue(st.discardObsoleteCart())
        assertFalse("tazzzo.cart.v1" in settings.keys)
        assertFalse(st.discardObsoleteCart(), "only reported once")
    }

    @Test fun aCurrentCartIsNotTouchedByTheObsoleteCheck() {
        val st = PersistentStore(MapSettings())
        st.saveCart(listOf(PersistentStore.SavedCartLine("TZP-1", 1, 100L)))
        assertFalse(st.discardObsoleteCart()); assertEquals(1, st.loadCart().size)
    }

    @Test fun restoreFromDiskDropsAnObsoleteCartWithTheExistingNotice() = runTest {
        val settings = MapSettings().apply { putString("tazzzo.cart.v1", v1Cart) }
        val app = TazzzoAppState(store = PersistentStore(settings))
        app.restoreFromDisk()
        assertTrue(app.cartLines().isEmpty())
        assertNotNull(app.restoreNotice)
        assertTrue(app.restoreNotice!!.contains("earlier version"))
        assertFalse("tazzzo.cart.v1" in settings.keys)
    }

    @Test fun nothingToRestoreProducesNoNotice() = runTest {
        val app = TazzzoAppState(store = PersistentStore(MapSettings()))
        app.restoreFromDisk()
        assertNull(app.restoreNotice)
    }

    @Test fun persistingTheLiveCartWritesPaiseAndRestoresIt() = runTest {
        val settings = MapSettings(); val st = PersistentStore(settings)
        val app = TazzzoAppState(store = st)
        val product = com.tazzzo.app.data.MockCatalog.products.first { it.id == "p8" }
        app.addToCart(product)
        val saved = st.loadCart().single()
        assertEquals(product.price.paise, saved.priceAtSavePaise)
        assertEquals(2_900L, saved.priceAtSavePaise)
    }

    // ---- membership --------------------------------------------------------------------------------

    @Test fun membershipRoundTripsPaiseUnderV2() {
        val settings = MapSettings(); val st = PersistentStore(settings)
        val state = com.tazzzo.app.data.model.MembershipState(
            status = MembershipStatus.ACTIVE, planId = "club",
            cumulativeSpend = Money.ofPaise(160_050), cumulativeSavings = Money.ofPaise(8_005)
        )
        st.saveMembership(state)
        assertEquals(state, PersistentStore(settings).loadMembership())
        assertTrue(settings.getString("tazzzo.membership.v2", "").contains("160050"))
    }

    @Test fun membershipV1IsMigratedLosslesslyToPaiseAndRemoved() {
        val settings = MapSettings().apply { putString("tazzzo.membership.v1", v1Membership) }
        val loaded = PersistentStore(settings).loadMembership()!!
        assertEquals(MembershipStatus.ACTIVE, loaded.status)
        assertEquals(Money.ofRupees(1_600), loaded.cumulativeSpend)
        assertEquals(Money.ofRupees(80), loaded.cumulativeSavings)
        assertEquals(2, loaded.eligibleOrderCount)
        assertEquals(setOf("TZ-1", "TZ-2"), loaded.countedOrderIds)
        assertFalse("tazzzo.membership.v1" in settings.keys)
        assertTrue("tazzzo.membership.v2" in settings.keys)
        assertEquals(loaded, PersistentStore(settings).loadMembership(), "the migrated state is what v2 now holds")
    }

    @Test fun unreadableMembershipV1IsDroppedNotGuessed() {
        val settings = MapSettings().apply { putString("tazzzo.membership.v1", "{not json") }
        assertNull(PersistentStore(settings).loadMembership())
        assertFalse("tazzzo.membership.v1" in settings.keys)
    }

    @Test fun v2WinsOverALeftoverV1() {
        val settings = MapSettings().apply { putString("tazzzo.membership.v1", v1Membership) }
        val st = PersistentStore(settings)
        st.saveMembership(com.tazzzo.app.data.model.MembershipState(status = MembershipStatus.NOT_MEMBER))
        assertEquals(MembershipStatus.NOT_MEMBER, st.loadMembership()!!.status)
    }

    @Test fun clearMembershipClearsBothVersions() {
        val settings = MapSettings().apply { putString("tazzzo.membership.v1", v1Membership) }
        val st = PersistentStore(settings)
        st.clearMembership()
        assertNull(st.loadMembership())
    }
}
