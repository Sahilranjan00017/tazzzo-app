package com.tazzzo.app.cart

import com.tazzzo.app.address.http
import com.tazzzo.app.data.cart.CartItem
import com.tazzzo.app.data.cart.CartSource
import com.tazzzo.app.data.cart.LineIssue
import com.tazzzo.app.data.cart.ServerCart
import com.tazzzo.app.data.catalog.StockState
import com.tazzzo.app.data.model.Money
import kotlinx.coroutines.CompletableDeferred

fun rs(rupees: Long) = Money.ofRupees(rupees)

fun line(
    sku: String = "TZP-1", qty: Int = 1, unit: Long = 50, max: Int = 10, stock: StockState = StockState.IN_STOCK,
    serviceable: Boolean? = true, buyable: Boolean = true, issues: List<String> = emptyList(), title: String? = "Atta 1kg"
) = CartItem(
    skuId = sku, quantity = qty, addedAt = null, updatedAt = null, title = title, brandCode = null, imageUrl = null,
    unitPrice = rs(unit), mrp = rs(unit), lineTotal = rs(unit * qty), stockState = stock, maxOrderQuantity = max,
    serviceable = serviceable, buyable = buyable, issues = issues.map { LineIssue.of(it) }
)

fun cartOf(version: Long, vararg lines: CartItem) = ServerCart(
    version = version, items = lines.toList(), itemCount = lines.sumOf { it.quantity }, distinctItemCount = lines.size,
    subtotal = Money.ofPaise(lines.sumOf { it.lineTotal?.paise ?: 0L }), expiresAt = null
)

/**
 * A scripted in-memory backend. It enforces If-Match exactly like the real one (412 on any version but the
 * current), bumps the version on every mutation, and lets a test fail the next call (optionally AFTER applying
 * it — a lost response) or hold it open with a gate.
 */
class FakeCartSource : CartSource {
    val lines = LinkedHashMap<String, CartItem>()
    var version = 0L
    val calls = mutableListOf<String>()
    val versionsSeen = mutableListOf<Long>()
    val addressIds = mutableListOf<String?>()
    private val failures = ArrayDeque<Pair<Throwable, Boolean>>()
    var gate: CompletableDeferred<Unit>? = null
    var getError: Throwable? = null

    fun failNext(error: Throwable, applied: Boolean = false) { failures.addLast(error to applied) }
    fun externalChange() { version++ }                       // someone else (another device) changed the cart
    fun snapshot() = ServerCart(
        version, lines.values.toList(), lines.values.sumOf { it.quantity }, lines.size,
        Money.ofPaise(lines.values.sumOf { it.lineTotal?.paise ?: 0L }), null
    )
    val mutations get() = calls.count { it.startsWith("PUT") || it.startsWith("DELETE") }

    private suspend fun enter(call: String, v: Long?, addressId: String?) {
        calls += call; addressIds += addressId
        v?.let { versionsSeen += it }
        gate?.let { g -> if (call != "GET") g.await() }
    }

    private fun mutate(v: Long, applyChange: () -> Unit): ServerCart {
        if (v != version) throw http(412, "PRECONDITION_FAILED")
        val f = failures.removeFirstOrNull()
        if (f != null) {
            if (f.second) { applyChange(); version++ }
            throw f.first
        }
        applyChange(); version++
        return snapshot()
    }

    override suspend fun get(addressId: String?): ServerCart {
        calls += "GET"; addressIds += addressId
        getError?.let { throw it }
        return snapshot()
    }

    override suspend fun setQuantity(skuId: String, quantity: Int, version: Long, addressId: String?): ServerCart {
        enter("PUT $skuId=$quantity", version, addressId)
        return mutate(version) { lines[skuId] = (lines[skuId] ?: line(skuId, quantity)).let { it.copy(quantity = quantity, lineTotal = Money.ofPaise((it.unitPrice?.paise ?: 0L) * quantity)) } }
    }

    override suspend fun removeItem(skuId: String, version: Long, addressId: String?): ServerCart {
        enter("DELETE $skuId", version, addressId)
        return mutate(version) { lines.remove(skuId) }
    }

    override suspend fun clear(version: Long, addressId: String?): ServerCart {
        enter("DELETE ALL", version, addressId)
        return mutate(version) { lines.clear() }
    }
}
