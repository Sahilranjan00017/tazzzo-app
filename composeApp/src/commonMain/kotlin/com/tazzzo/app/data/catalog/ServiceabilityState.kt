package com.tazzzo.app.data.catalog

import com.tazzzo.app.data.local.PersistentStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The ONE active delivery PIN the catalogue and serviceability read. */
interface LocationPin {
    val pin: StateFlow<Pincode>
    fun setPin(raw: String): Boolean
}

sealed interface ServiceabilityState {
    /** Nothing checked yet. */
    data object Unknown : ServiceabilityState
    data object Loading : ServiceabilityState
    /** ETA may be absent and is never required. */
    data class Serviceable(val result: ServiceabilityResult) : ServiceabilityState
    data class NotServiceable(val result: ServiceabilityResult) : ServiceabilityState
    data class Failed(val failure: CatalogFailure) : ServiceabilityState
}

/** Where the launch PIN is kept (non-secret app state). */
interface PinStore {
    fun load(): String?
    fun save(pin: String)
}

class PersistentPinStore(private val store: PersistentStore) : PinStore {
    override fun load(): String? = store.launchPin
    override fun save(pin: String) { store.launchPin = pin }
}

/**
 * The customer's current delivery PIN and what the backend says about it.
 *
 * Starts at the launch PIN (`560047`) unless a valid PIN was persisted. Product
 * and PDP reads take [pin]; changing it ([setPin]) persists it, resets
 * [state] and re-checks, and a product pager keyed on it restarts automatically.
 */
class LaunchContext(
    private val pins: PinStore,
    private val source: ServiceabilityChecker,
    private val scope: CoroutineScope
) : LocationPin {
    private val _pin = MutableStateFlow(Pincode.parse(pins.load()) ?: Pincode.LAUNCH)
    override val pin: StateFlow<Pincode> = _pin

    private val _state = MutableStateFlow<ServiceabilityState>(ServiceabilityState.Unknown)
    val state: StateFlow<ServiceabilityState> = _state

    private var job: Job? = null
    private var generation = 0

    /** Checks the current PIN. A newer check (or PIN change) supersedes an older one. */
    fun refresh() {
        job?.cancel()
        val gen = ++generation
        val target = _pin.value
        _state.value = ServiceabilityState.Loading
        job = scope.launch {
            try {
                val r = source.check(target)
                if (gen != generation) return@launch
                _state.value = if (r.serviceable) ServiceabilityState.Serviceable(r) else ServiceabilityState.NotServiceable(r)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (gen == generation) _state.value = ServiceabilityState.Failed(e.toCatalogFailure())
            }
        }
    }

    /** @return false (and changes nothing) if [raw] is not a valid PIN. */
    override fun setPin(raw: String): Boolean {
        val parsed = Pincode.parse(raw) ?: return false
        if (parsed == _pin.value) return true
        pins.save(parsed.value)
        _pin.value = parsed
        refresh()
        return true
    }
}
