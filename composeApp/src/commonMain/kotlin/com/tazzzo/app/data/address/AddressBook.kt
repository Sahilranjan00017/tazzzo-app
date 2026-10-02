package com.tazzzo.app.data.address

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

sealed interface BookState {
    /** Guest, logged out, or session ended. Nothing is held and nothing is requested. */
    data object SignedOut : BookState
    data object Idle : BookState
    data object Loading : BookState
    /** The backend's collection exactly as it sent it: server ordering, server default, server serviceability. */
    data class Loaded(val addresses: List<CustomerAddress>) : BookState {
        val defaultId: String? get() = addresses.firstOrNull { it.isDefault }?.addressId
        val atLimit: Boolean get() = addresses.size >= AddressRules.MAX_ADDRESSES
        override fun toString(): String = "Loaded(${addresses.size} addresses)"
    }
    data class Failed(val failure: AddressFailure) : BookState
}

/** A one-shot explanation to show after the book was refreshed behind the customer's back. */
enum class AddressNotice {
    /** 412: "This address changed. Review the latest details and try again." */
    StaleRefreshed,
    /** 404: it was already removed. */
    NotFoundRefreshed,
    /** A create may or may not have been applied. */
    AmbiguousCreate
}

sealed interface AddressActionResult {
    data object Success : AddressActionResult
    /** Client-side validation: nothing was sent. */
    data class ValidationFailed(val errors: Map<AddressField, FieldError>) : AddressActionResult
    /** The server answered 400. */
    data object Rejected : AddressActionResult
    data object LimitReached : AddressActionResult
    /** 412: the book was refetched and [AddressNotice.StaleRefreshed] set; nothing is retried automatically. */
    data object Stale : AddressActionResult
    data object NotFound : AddressActionResult
    /** The create may have been applied. The list was refetched; the customer decides whether to submit again. */
    data object AmbiguousCreate : AddressActionResult
    data object AuthRequired : AddressActionResult
    /** Another mutation is already running (double-submit guard). */
    data object Busy : AddressActionResult
    data class Failed(val failure: AddressFailure) : AddressActionResult
}

/**
 * The signed-in customer's addresses, in memory only. The backend is the authority:
 *  - after EVERY successful mutation the whole collection is refetched (ordering, default promotion,
 *    live serviceability and versions are the server's to decide);
 *  - mutations are serialized — a second one while one runs is [AddressActionResult.Busy];
 *  - nothing is written to disk, and [signOut] wipes everything.
 * Composables observe [state]; they never call the data source.
 */
class AddressBook(
    private val scope: CoroutineScope,
    private val source: AddressSource,
    private val isAuthenticated: () -> Boolean
) {
    private val _state = MutableStateFlow<BookState>(BookState.Idle)
    val state: StateFlow<BookState> = _state

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _notice = MutableStateFlow<AddressNotice?>(null)
    val notice: StateFlow<AddressNotice?> = _notice

    private val lock = Mutex()
    private var generation = 0

    fun dismissNotice() { _notice.value = null }

    /** Loads when nothing usable is held. */
    fun load() {
        if (!isAuthenticated()) { _state.value = BookState.SignedOut; return }
        when (_state.value) {
            BookState.Idle, BookState.SignedOut, is BookState.Failed -> startReload()
            else -> Unit
        }
    }

    /** Forces a reload (pull-to-refresh, retry). */
    fun refresh() {
        if (!isAuthenticated()) { _state.value = BookState.SignedOut; return }
        startReload()
    }

    /** Session ended: drop everything, discard anything still in flight. */
    fun signOut() {
        generation++
        _state.value = BookState.SignedOut
        _notice.value = null
        _busy.value = false
    }

    private fun startReload() {
        val gen = generation
        if (_state.value !is BookState.Loaded) _state.value = BookState.Loading
        scope.launch {
            val result = fetch()
            if (gen == generation) _state.value = result
        }
    }

    private suspend fun fetch(): BookState = try {
        BookState.Loaded(source.list())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        BookState.Failed(e.toAddressFailure())
    }

    /** Authoritative refetch after a mutation. A failure here leaves a retryable error state, not a wrong list. */
    private suspend fun refetch(gen: Int) {
        val result = fetch()
        if (gen == generation) _state.value = result
    }

    // ---- mutations ---------------------------------------------------------------------------------------

    suspend fun create(input: AddressInput): AddressActionResult {
        val valid = when (val v = AddressValidator.validate(input)) {
            is AddressValidation.Invalid -> return AddressActionResult.ValidationFailed(v.errors)
            is AddressValidation.Valid -> v.address
        }
        return mutate { gen ->
            try {
                source.create(valid)
                refetch(gen)
                AddressActionResult.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val f = e.toAddressFailure()
                when {
                    f is AddressFailure.LimitReached -> { refetch(gen); AddressActionResult.LimitReached }
                    f is AddressFailure.InvalidRequest -> AddressActionResult.Rejected
                    f is AddressFailure.Unauthenticated -> AddressActionResult.AuthRequired
                    f.mayHaveReachedServer -> {
                        // Create is NOT idempotent and duplicates are legal, so matching content proves nothing.
                        // Do not retry, do not infer success: show the real list and let the customer decide.
                        refetch(gen)
                        if (gen == generation) _notice.value = AddressNotice.AmbiguousCreate
                        AddressActionResult.AmbiguousCreate
                    }
                    else -> AddressActionResult.Failed(f)
                }
            }
        }
    }

    suspend fun update(address: CustomerAddress, input: AddressInput): AddressActionResult {
        val valid = when (val v = AddressValidator.validate(input)) {
            is AddressValidation.Invalid -> return AddressActionResult.ValidationFailed(v.errors)
            is AddressValidation.Valid -> v.address
        }
        val body = AddressBodies.patch(address, valid) ?: return AddressActionResult.Success   // nothing changed: no request
        return mutate { gen ->
            guarded(gen) { source.update(address.addressId, address.version, body) }
        }
    }

    suspend fun delete(address: CustomerAddress): AddressActionResult = mutate { gen ->
        guarded(gen) { source.delete(address.addressId, address.version) }
    }

    /** No `If-Match` and no version change: the list is refetched, not patched locally. */
    suspend fun setDefault(address: CustomerAddress): AddressActionResult = mutate { gen ->
        guarded(gen) { source.setDefault(address.addressId) }
    }

    /** Shared outcome mapping for update / delete / set-default. */
    private suspend fun guarded(gen: Int, call: suspend () -> Unit): AddressActionResult = try {
        call()
        refetch(gen)
        AddressActionResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        when (val f = e.toAddressFailure()) {
            AddressFailure.PreconditionFailed -> {
                refetch(gen)
                if (gen == generation) _notice.value = AddressNotice.StaleRefreshed
                AddressActionResult.Stale
            }
            AddressFailure.NotFound -> {
                refetch(gen)
                if (gen == generation) _notice.value = AddressNotice.NotFoundRefreshed
                AddressActionResult.NotFound
            }
            AddressFailure.InvalidRequest -> AddressActionResult.Rejected
            AddressFailure.Unauthenticated -> AddressActionResult.AuthRequired
            else -> AddressActionResult.Failed(f)       // includes 428: a client bug, never silently retried
        }
    }

    private suspend fun mutate(block: suspend (generation: Int) -> AddressActionResult): AddressActionResult {
        if (!isAuthenticated()) return AddressActionResult.AuthRequired
        if (!lock.tryLock()) return AddressActionResult.Busy
        val gen = generation
        _busy.value = true
        try {
            return block(gen)
        } finally {
            if (gen == generation) _busy.value = false
            lock.unlock()
        }
    }
}
