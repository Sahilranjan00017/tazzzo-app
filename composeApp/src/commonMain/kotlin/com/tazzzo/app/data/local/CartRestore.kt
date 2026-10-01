package com.tazzzo.app.data.local

import com.tazzzo.app.data.model.Product

/**
 * Reconciles a persisted cart against the CURRENT catalogue.
 *
 * Pure function — fully unit-tested. The restored cart always uses current
 * products (current prices); the outcome reports every deviation so the UI
 * can tell the customer instead of silently restoring invalid state.
 */
object CartRestore {

    data class Outcome(
        val restored: List<Pair<Product, Int>>,   // product (current) + quantity
        val removedNames: List<String>,           // gone or unavailable
        val limitedNames: List<String>,           // quantity clamped to stock
        val repricedNames: List<String>           // price differs from last seen
    ) {
        val hasChanges: Boolean
            get() = removedNames.isNotEmpty() || limitedNames.isNotEmpty() || repricedNames.isNotEmpty()

        /** Customer-facing one-liner; null when nothing to disclose. */
        fun notice(): String? {
            if (!hasChanges) return null
            val parts = mutableListOf<String>()
            if (removedNames.isNotEmpty())
                parts += "${removedNames.joinToString(limit = 2)} no longer available"
            if (limitedNames.isNotEmpty())
                parts += "quantity reduced for ${limitedNames.joinToString(limit = 2)}"
            if (repricedNames.isNotEmpty())
                parts += "prices updated for ${repricedNames.joinToString(limit = 2)}"
            return "We restored your cart — " + parts.joinToString("; ") + "."
        }
    }

    fun reconcile(
        saved: List<PersistentStore.SavedCartLine>,
        lookup: (String) -> Product?
    ): Outcome {
        val restored = mutableListOf<Pair<Product, Int>>()
        val removed = mutableListOf<String>()
        val limited = mutableListOf<String>()
        val repriced = mutableListOf<String>()

        for (line in saved) {
            if (line.qty <= 0) continue
            val current = lookup(line.id)
            if (current == null) { removed += "an item"; continue }
            if (!current.isPurchasable) { removed += current.name; continue }

            val allowed = minOf(line.qty, current.purchasableLimit)
            if (allowed < line.qty) limited += current.name
            if (current.price.paise != line.priceAtSavePaise) repriced += current.name
            if (allowed > 0) restored += current to allowed
        }
        return Outcome(restored, removed, limited, repriced)
    }
}
