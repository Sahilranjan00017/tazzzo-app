package com.tazzzo.app.data.address

import com.tazzzo.app.data.catalog.LocationPin
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.local.PersistentStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where the one non-sensitive selection id is kept (an opaque `ADDR_…`; no address content). */
interface SelectionStore {
    var selectedAddressId: String?
}

class PersistentSelectionStore(private val store: PersistentStore) : SelectionStore {
    override var selectedAddressId: String?
        get() = store.selectedAddressId
        set(v) { store.selectedAddressId = v }
}

/**
 * The default address to OFFER, or null. Offered only while: the book is loaded, a default exists,
 * nothing is selected yet, and the customer has not declined. Logging in never selects it.
 */
fun deliverySuggestion(state: BookState, selectedId: String?, dismissed: Boolean): CustomerAddress? =
    if (dismissed || selectedId != null) null else (state as? BookState.Loaded)?.addresses?.firstOrNull { it.isDefault }

/**
 * The single owner of "which saved address is the delivery location". There is still exactly ONE
 * active location source: the [LocationPin] (LaunchContext) that the catalogue and serviceability
 * already read. This class only decides when that PIN is written:
 *
 *  - no selected address          -> the current (manual / launch) PIN, untouched;
 *  - [selectAddress]              -> selectedAddressId = it, PIN = its postal code;
 *  - [setManualPin]               -> selection cleared, PIN = the typed one;
 *  - the selected address edited  -> after the refetch, PIN follows its latest postal code;
 *  - the selected address deleted -> selection cleared, PIN kept until the customer picks again;
 *  - [onSignedOut]                -> selection cleared, PIN back to the launch PIN.
 *
 * A backend DEFAULT address and a SELECTED delivery address are different things: login and
 * default-promotion never change the PIN by themselves.
 */
class DeliveryLocation(
    scope: CoroutineScope,
    private val pins: LocationPin,
    private val selection: SelectionStore,
    private val book: AddressBook
) {
    private val _selected = MutableStateFlow(selection.selectedAddressId)
    val selectedAddressId: StateFlow<String?> = _selected

    private val _dismissed = MutableStateFlow(false)

    val suggestion: StateFlow<CustomerAddress?> =
        combine(book.state, _selected, _dismissed) { s, sel, d -> deliverySuggestion(s, sel, d) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch { book.state.collect { if (it is BookState.Loaded) reconcile(it.addresses) } }
    }

    /** The customer chose this address as the delivery location. */
    fun selectAddress(a: CustomerAddress) {
        setSelected(a.addressId)
        pins.setPin(a.postalCode.value)
    }

    /** "Keep current location": stop offering the default for this session. */
    fun keepCurrentLocation() { _dismissed.value = true }

    /** @return false (nothing changes) if [raw] is not a valid PIN. */
    fun setManualPin(raw: String): Boolean {
        if (!Pincode.isValid(raw.trim())) return false
        setSelected(null)
        return pins.setPin(raw.trim())
    }

    fun onSignedIn() { _dismissed.value = false }

    fun onSignedOut() {
        setSelected(null)
        _dismissed.value = false
        pins.setPin(Pincode.LAUNCH_VALUE)
    }

    /** A guest cannot have an address-backed selection (a stale id left from a previous session). */
    fun clearSelection() = setSelected(null)

    private fun reconcile(addresses: List<CustomerAddress>) {
        val id = _selected.value ?: return
        val a = addresses.firstOrNull { it.addressId == id }
        if (a == null) { setSelected(null); return }                 // gone: clear the id, keep the PIN
        if (a.postalCode.value != pins.pin.value.value) pins.setPin(a.postalCode.value)
    }

    private fun setSelected(id: String?) {
        _selected.value = id
        selection.selectedAddressId = id
    }
}

/**
 * Connects the auth session to the address surfaces:
 *  - signed in  -> the book loads (so a default can be offered and a persisted selection validated);
 *  - signed out (logout OR a definitive rejection) -> the book is wiped and the location resets to 560047;
 *  - the initial "not signed in" value of a cold start is NOT a logout and changes nothing.
 */
class SessionLocationBinding(
    private val scope: CoroutineScope,
    private val active: kotlinx.coroutines.flow.Flow<Boolean>,
    private val book: AddressBook,
    private val location: DeliveryLocation
) {
    fun start(initiallyAuthenticated: Boolean) {
        if (!initiallyAuthenticated) location.clearSelection()
        scope.launch {
            var was = false
            active.collect { now ->
                if (now && !was) { location.onSignedIn(); book.load() }
                else if (!now && was) { book.signOut(); location.onSignedOut() }
                was = now
            }
        }
    }
}
