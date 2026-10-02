package com.tazzzo.app.data.order

import com.tazzzo.app.data.local.PersistentStore

/** The single opaque recovery handle for an unresolved order attempt (the quote id only). */
interface PendingOrderStore {
    fun load(): String?
    fun save(quoteId: String)
    fun clear()
}

class PersistentPendingOrderStore(private val store: PersistentStore) : PendingOrderStore {
    override fun load(): String? = store.loadPendingOrderQuote()
    override fun save(quoteId: String) = store.savePendingOrderQuote(quoteId)
    override fun clear() = store.clearPendingOrderQuote()
}
