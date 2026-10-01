package com.tazzzo.app

import com.tazzzo.app.config.BillCalculator
import com.tazzzo.app.config.MembershipConfig
import com.tazzzo.app.config.PromotionConfig
import com.tazzzo.app.data.local.PersistentStore
import com.tazzzo.app.data.model.CartIssue
import com.tazzzo.app.data.model.CartLine
import com.tazzzo.app.data.model.DeliverySlot
import com.tazzzo.app.data.model.MembershipEligibility
import com.tazzzo.app.data.model.MembershipState
import com.tazzzo.app.data.model.MembershipTransaction
import com.tazzzo.app.data.model.MembershipStatus
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.model.Order
import com.tazzzo.app.data.model.OrderStatus
import com.tazzzo.app.data.model.Product
import com.tazzzo.app.data.model.PromotionResolution
import com.russhwolf.settings.MapSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * PR-04B: the UNIT of every raw monetary number that leaves the process is in its name.
 * `price = 4999` would be ₹4,999 or ₹49.99; `pricePaise = 4999` cannot be misread.
 * The Kotlin property stays `price: Money` (the type carries the unit); only the serialized
 * name says `Paise`.
 */
class MoneySerializationUnitsTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun product(price: Money, mrp: Money = price) = Product(
        id = "p1", name = "Atta", brand = "B", unit = "5 kg", price = price, mrp = mrp,
        categoryId = "c", subcategoryId = "s", rating = 4.5, ratingCount = 3
    )

    // ---- ₹49.50 ------------------------------------------------------------------------------------

    @Test fun fortyNineFiftySerializesAsExplicitPaiseAndRoundTripsLosslessly() {
        val original = product(Money.ofPaise(4_950), Money.ofPaise(5_500))
        val text = json.encodeToString(original)
        assertTrue("\"pricePaise\":4950" in text, text)
        assertTrue("\"mrpPaise\":5500" in text, text)
        assertFalse("\"price\"" in text || "\"mrp\"" in text, "no unit-less money key: $text")
        val back = json.decodeFromString<Product>(text)
        assertEquals(original, back)
        assertEquals("₹49.50", back.price.format())
    }

    @Test fun wholeRupeeValuesAreStillPaiseOnTheWire() {
        val text = json.encodeToString(product(r(99)))
        assertTrue("\"pricePaise\":9900" in text, text)   // never 99
    }

    // ---- an old rupee-valued representation cannot be read as paise ---------------------------------------

    @Test fun anOldRupeeProductPayloadIsRejectedNotReadAsPaise() {
        val oldRupees = """{"id":"p1","name":"A","brand":"B","unit":"1 kg","price":249,"mrp":299,
            "categoryId":"c","subcategoryId":"s","rating":4.5,"ratingCount":1}"""
        assertFailsWith<Exception>("249 rupees must never become 249 paise") { json.decodeFromString<Product>(oldRupees) }
    }

    @Test fun anOldRupeeFieldOnAPayloadWithDefaultsIsIgnoredNotReadAsPaise() {
        // BillSummary's discount/tip fields default to zero; a legacy `clubDiscount: 33` (rupees) must not
        // turn into 33 paise. It is an unknown key now, so it is ignored — it can only be zero, never misread.
        val legacy = """{"itemTotalPaise":10000,"itemMrpTotalPaise":10000,"deliveryFeePaise":0,"handlingChargePaise":0,
            "coinsEarned":0,"grandTotalPaise":10000,"clubDiscount":33,"tip":10}"""
        val bill = json.decodeFromString<com.tazzzo.app.data.model.BillSummary>(legacy)
        assertEquals(Money.ZERO, bill.clubDiscount)
        assertEquals(Money.ZERO, bill.tip)
    }

    // ---- PRICE_CHANGED -----------------------------------------------------------------------------------------

    @Test fun priceChangedUsesExplicitPaiseKeys() {
        val text = json.encodeToString<CartIssue>(CartIssue.PriceChanged("p8", "Milk", Money.ofPaise(2_950), Money.ofPaise(3_200)))
        assertTrue("\"oldPricePaise\":2950" in text && "\"newPricePaise\":3200" in text, text)
        assertFalse("\"oldPrice\"" in text || "\"newPrice\"" in text, text)
        assertEquals(CartIssue.PriceChanged("p8", "Milk", Money.ofPaise(2_950), Money.ofPaise(3_200)), json.decodeFromString<CartIssue>(text))
    }

    // ---- cart + membership persistence -----------------------------------------------------------------------------

    @Test fun cartPersistenceNamesThePaiseUnit() {
        val settings = MapSettings()
        PersistentStore(settings).saveCart(listOf(PersistentStore.SavedCartLine("TZP-1", 2, 4_950L)))
        val raw = settings.getString("tazzzo.cart.v2", "")
        assertTrue("\"priceAtSavePaise\":4950" in raw, raw)
        assertFalse("\"priceAtSave\"" in raw, raw)
    }

    @Test fun membershipPersistenceNamesThePaiseUnit() {
        val settings = MapSettings()
        PersistentStore(settings).saveMembership(
            MembershipState(status = MembershipStatus.ACTIVE, cumulativeSpend = Money.ofPaise(160_050), cumulativeSavings = Money.ofPaise(8_005))
        )
        val raw = settings.getString("tazzzo.membership.v2", "")
        assertTrue("\"cumulativeSpendPaise\":160050" in raw && "\"cumulativeSavingsPaise\":8005" in raw, raw)
        assertFalse("\"cumulativeSpend\"" in raw || "Rupees" in raw, raw)
    }

    @Test fun membershipV1RupeesConvertExactlyTimesHundredIntoThePaiseNames() {
        val settings = MapSettings().apply {
            putString("tazzzo.membership.v1", """{"status":"ACTIVE","planId":"club","cumulativeSpendRupees":1600,"cumulativeSavingsRupees":80}""")
        }
        val loaded = PersistentStore(settings).loadMembership()!!
        assertEquals(160_000L, loaded.cumulativeSpend.paise)
        assertEquals(8_000L, loaded.cumulativeSavings.paise)
        val raw = settings.getString("tazzzo.membership.v2", "")
        assertTrue("\"cumulativeSpendPaise\":160000" in raw && "\"cumulativeSavingsPaise\":8000" in raw, raw)
    }

    @Test fun rupeeNamedFieldsInsideAV2EntryAreNeverReadAsPaise() {
        val settings = MapSettings().apply {
            putString("tazzzo.membership.v2", """{"status":"ACTIVE","cumulativeSpendRupees":1600,"cumulativeSpend":1600}""")
        }
        val loaded = PersistentStore(settings).loadMembership()!!
        assertEquals(Money.ZERO, loaded.cumulativeSpend, "1600 rupees (or an unlabelled 1600) must not become 1600 paise")
    }

    @Test fun anObsoleteCartKeyStillNeverReadsAsPaise() {
        val settings = MapSettings().apply { putString("tazzzo.cart.v1", """[{"id":"p8","qty":1,"priceAtSave":29}]""") }
        assertTrue(PersistentStore(settings).loadCart().isEmpty())
        // and a v1-shaped row sitting under the v2 key (unit-less price) decodes to nothing, not to 29 paise
        val bad = MapSettings().apply { putString("tazzzo.cart.v2", """[{"id":"p8","qty":1,"priceAtSave":29}]""") }
        assertTrue(PersistentStore(bad).loadCart().isEmpty())
    }

    // ---- guard: no unit-less numeric key in any money-bearing structure ------------------------------------------------

    /** Numeric keys that are legitimately NOT money. Anything else numeric must end in `Paise`. */
    private val notMoney = setOf(
        "quantity", "qty", "coinsEarned", "percent", "rating", "ratingCount", "etaMinutes", "maxOrderQuantity",
        "eligibleOrderCount", "requiredOrders", "expiryDaysAfterUnlock", "periodDays", "priority", "buyQuantity",
        "getQuantity", "usageLimitPerUser", "tint", "remaining", "requested", "available"
    )

    private fun unitlessNumericKeys(e: JsonElement, path: String = "$", out: MutableList<String> = mutableListOf()): List<String> {
        when (e) {
            is JsonObject -> e.forEach { (k, v) ->
                if (v is JsonPrimitive && !v.isString && v.content != "null" && v.content != "true" && v.content != "false" &&
                    !k.endsWith("Paise") && k !in notMoney) out += "$path.$k=${v.content}"
                unitlessNumericKeys(v, "$path.$k", out)
            }
            is JsonArray -> e.forEachIndexed { i, v -> unitlessNumericKeys(v, "$path[$i]", out) }
            else -> Unit
        }
        return out
    }

    @Test fun everySerializedMoneyStructureNamesItsUnit() {
        val p = product(Money.ofPaise(4_950), Money.ofPaise(5_500))
        val bill = BillCalculator.bill(listOf(CartLine(p, 3)), isClubMember = true, couponCode = "TAZZZO50", slot = DeliverySlot("s", "late", true, fee = r(15)))
        val samples: Map<String, JsonElement> = mapOf(
            "Product" to json.encodeToJsonElement(Product.serializer(), p),
            "BillSummary" to json.encodeToJsonElement(com.tazzzo.app.data.model.BillSummary.serializer(), bill),
            "Order" to json.encodeToJsonElement(Order.serializer(), Order("TZ1", listOf(CartLine(p, 2)), bill, OrderStatus.PLACED, "now", "addr")),
            "DeliverySlot" to json.encodeToJsonElement(DeliverySlot.serializer(), DeliverySlot("s", "late", true, fee = r(15))),
            "Promotions" to json.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(com.tazzzo.app.data.model.Promotion.serializer()), PromotionConfig.active),
            "PromotionResolution" to json.encodeToJsonElement(PromotionResolution.serializer(),
                PromotionResolution(bill.appliedPromotions, bill.declinedPromotions, bill.promotionDiscount, false, true, bill.clubDiscount)),
            "MembershipPlan" to json.encodeToJsonElement(com.tazzzo.app.data.model.MembershipPlan.serializer(), MembershipConfig.plan),
            "MembershipState" to json.encodeToJsonElement(MembershipState.serializer(),
                MembershipState(status = MembershipStatus.ACTIVE, cumulativeSpend = r(10), cumulativeSavings = r(1))),
            "MembershipEligibility" to json.encodeToJsonElement(MembershipEligibility.serializer(),
                MembershipEligibility(true, true, r(3), r(0), MembershipConfig.plan.discountRule)),
            "MembershipTransaction" to json.encodeToJsonElement(MembershipTransaction.serializer(),
                MembershipTransaction("t", "club", r(99), MembershipStatus.ACTIVE, null, true, "now"))
        )
        val offenders = samples.flatMap { (name, el) -> unitlessNumericKeys(el).map { "$name $it" } }
        assertTrue(offenders.isEmpty(), "numeric keys without a unit in their name: $offenders")
    }

    @Test fun theGuardActuallyCatchesAnUnlabelledMoneyKey() {
        val offenders = unitlessNumericKeys(Json.parseToJsonElement("""{"price":4999,"pricePaise":4999,"quantity":2}"""))
        assertEquals(listOf("$.price=4999"), offenders)
    }
}
