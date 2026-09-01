package com.tazzzo.app.data.repository

import com.tazzzo.app.data.model.Address
import com.tazzzo.app.data.model.CartIssue
import com.tazzzo.app.data.model.CartLine
import com.tazzzo.app.data.model.CartValidation
import com.tazzzo.app.data.model.DeliverySlot
import com.tazzzo.app.data.model.Order
import com.tazzzo.app.data.model.OrderRequest
import com.tazzzo.app.data.model.OrderStatus
import com.tazzzo.app.data.model.PaymentMethod
import com.tazzzo.app.data.model.PaymentMethodKind
import com.tazzzo.app.data.model.PlaceOrderResult
import kotlinx.coroutines.delay

/**
 * Checkout contracts. Backend endpoints these map to are documented in
 * docs/BACKEND_CONTRACTS.md — nothing here invents server behaviour, the
 * mocks implement the documented consumer-side contract in memory.
 */
interface AddressRepository {
    suspend fun getAddresses(): List<Address>
    suspend fun addAddress(label: String, line1: String, line2: String, pincode: String): Address
}

interface CheckoutRepository {
    /** Serviceability-aware slots for an address. Empty = not serviceable. */
    suspend fun getSlots(addressId: String): List<DeliverySlot>

    suspend fun getPaymentMethods(): List<PaymentMethod>

    /** Re-checks every line against current stock and price. */
    suspend fun validateCart(lines: List<CartLine>): CartValidation

    /**
     * Places the order exactly once per idempotency key. Submitting the same
     * key again returns the SAME order with replayed=true — duplicate taps
     * and retries after timeouts cannot create duplicate orders.
     */
    suspend fun placeOrder(request: OrderRequest): PlaceOrderResult
}

// ---------------------------------------------------------------------------
// Mock implementations — in-memory, explicitly labelled development stand-ins.
// ---------------------------------------------------------------------------

class MockAddressRepository(
    private val store: com.tazzzo.app.data.local.PersistentStore? =
        com.tazzzo.app.data.local.PersistentStore()
) : AddressRepository {
    private val addresses = mutableListOf(
        Address("addr-1", "Home", "22, 14th Main, HSR Layout", "Sector 6", "560102", isServiceable = true),
        Address("addr-2", "Work", "Tower B, Ecospace", "Bellandur", "560103", isServiceable = true),
        // Deliberately unserviceable: exercises the blocked-slot path honestly.
        Address("addr-3", "Parents", "42, MG Road", "Mysuru", "570001", isServiceable = false)
    )

    private var restored = false

    override suspend fun getAddresses(): List<Address> {
        delay(300)
        if (!restored) {
            restored = true
            store?.loadAddresses()?.forEach { saved ->
                if (addresses.none { it.id == saved.id }) {
                    addresses.add(Address(saved.id, saved.label, saved.line1, saved.line2, saved.pincode, saved.isServiceable))
                }
            }
        }
        return addresses.toList()
    }

    override suspend fun addAddress(label: String, line1: String, line2: String, pincode: String): Address {
        delay(400)
        // Mock serviceability rule: Bengaluru pincodes (560xxx) only.
        val serviceable = pincode.startsWith("560")
        val addr = Address("addr-${addresses.size + 1}", label.trim(), line1.trim(), line2.trim(), pincode, serviceable)
        addresses.add(addr)
        store?.saveAddresses(
            addresses.filter { it.id !in setOf("addr-1", "addr-2", "addr-3") }.map {
                com.tazzzo.app.data.local.PersistentStore.SavedAddress(
                    it.id, it.label, it.line1, it.line2, it.pincode, it.isServiceable
                )
            }
        )
        return addr
    }
}

class MockCheckoutRepository(
    private val catalog: CatalogRepository,
    private val orders: OrderRepository
) : CheckoutRepository {

    /** Idempotency ledger: key → order already created for that key. */
    private val placedByKey = mutableMapOf<String, Order>()

    /** Dev hook: force the next placement to fail (exercises failure/retry UI). */
    var failNextPlacement: Boolean = false

    override suspend fun getSlots(addressId: String): List<DeliverySlot> {
        delay(350)
        // Mock: slots exist only for serviceable addresses (addr-3 is not).
        if (addressId == "addr-3") return emptyList()
        return listOf(
            DeliverySlot("slot-express", "Express — as soon as possible", available = true),
            DeliverySlot("slot-morning", "Tomorrow, 7–9 AM", available = true),
            DeliverySlot("slot-evening", "Tomorrow, 6–8 PM", available = true),
            DeliverySlot("slot-full", "Today, 6–8 PM", available = false)
        )
    }

    override suspend fun getPaymentMethods(): List<PaymentMethod> {
        delay(200)
        return listOf(
            PaymentMethod(PaymentMethodKind.COD, "Cash on Delivery", enabled = true),
            PaymentMethod(PaymentMethodKind.UPI, "UPI", enabled = false, note = "Coming soon"),
            PaymentMethod(PaymentMethodKind.CARD, "Credit / Debit Card", enabled = false, note = "Coming soon")
        )
    }

    override suspend fun validateCart(lines: List<CartLine>): CartValidation {
        delay(300)
        val issues = mutableListOf<CartIssue>()
        for (line in lines) {
            val current = catalog.getProduct(line.product.id)
            when {
                current == null || !current.isPurchasable ->
                    issues += CartIssue.OutOfStock(line.product.id, line.product.name)
                line.quantity > current.purchasableLimit ->
                    issues += CartIssue.QuantityReduced(
                        line.product.id, line.product.name,
                        requested = line.quantity, available = current.purchasableLimit
                    )
                current.price != line.product.price ->
                    issues += CartIssue.PriceChanged(
                        line.product.id, line.product.name,
                        oldPrice = line.product.price, newPrice = current.price
                    )
            }
        }
        return CartValidation(issues)
    }

    override suspend fun placeOrder(request: OrderRequest): PlaceOrderResult {
        delay(500)

        // Idempotent replay — the same key never creates a second order.
        placedByKey[request.idempotencyKey]?.let {
            return PlaceOrderResult.Placed(it, replayed = true)
        }

        if (failNextPlacement) {
            failNextPlacement = false
            return PlaceOrderResult.Failed(
                "We couldn't reach the order service.", retryable = true
            )
        }

        // Final line-level defence: never place an invalid order.
        val validation = validateCart(request.lines)
        if (!validation.ok) return PlaceOrderResult.Rejected(validation)

        val order = orders.placeOrder(request.lines, request.bill, request.addressText, request.payment)
        placedByKey[request.idempotencyKey] = order
        return PlaceOrderResult.Placed(order, replayed = false)
    }
}
