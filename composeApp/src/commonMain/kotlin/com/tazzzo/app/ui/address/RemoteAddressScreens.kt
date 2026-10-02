package com.tazzzo.app.ui.address

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.data.address.ADDRESS_LIMIT_MESSAGE
import com.tazzzo.app.data.address.AddressActionResult
import com.tazzzo.app.data.address.AddressField
import com.tazzzo.app.data.address.AddressInput
import com.tazzzo.app.data.address.AddressLabel
import com.tazzzo.app.data.address.AddressServiceability
import com.tazzzo.app.data.address.BookState
import com.tazzzo.app.data.address.CustomerAddress
import com.tazzzo.app.data.address.FieldError
import com.tazzzo.app.data.address.badge
import com.tazzzo.app.data.address.hint
import com.tazzzo.app.data.address.message
import com.tazzzo.app.data.address.suggestionText
import com.tazzzo.app.data.address.title
import com.tazzzo.app.data.address.userMessage
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.ChipTone
import com.tazzzo.app.ui.common.EmptyState
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.SkeletonBlock
import com.tazzzo.app.ui.common.TazChip
import com.tazzzo.app.ui.common.TazTopBar
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import com.tazzzo.app.ui.onboarding.tazFieldColors
import kotlinx.coroutines.launch

/*
 * REAL customer addresses (PR-05). Functional integration only; existing components and tokens.
 * Composables observe AddressBook / DeliveryLocation — they never call the data source. Nothing here
 * logs or persists address content.
 */

/**
 * "Deliver to Home · 560047?" — offered after login, never applied automatically. Accepting makes the
 * saved address the delivery location (and its PIN the catalogue PIN); declining keeps the current one.
 */
@Composable
fun DeliverySuggestionBanner(modifier: Modifier = Modifier) {
    val location = ServiceLocator.deliveryLocation
    val suggestion by location.suggestion.collectAsState()
    val a = suggestion ?: return
    Column(modifier.fillMaxWidth().background(TazColors.GreenSoft).padding(horizontal = TazSpace.lg, vertical = TazSpace.sm)) {
        Text(a.suggestionText(), fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold, color = TazColors.TextPrimary)
        Spacer(Modifier.height(TazSpace.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(TazSpace.md)) {
            TextAction("Use this address") { location.selectAddress(a) }
            TextAction("Keep current location", TazColors.TextSecondary) { location.keepCurrentLocation() }
        }
    }
}

@Composable
private fun TextAction(text: String, ink: androidx.compose.ui.graphics.Color = TazColors.Green, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text, fontSize = TazType.captionSize, fontWeight = FontWeight.Bold, color = if (enabled) ink else TazColors.TextDisabled,
        modifier = Modifier.defaultMinSize(minHeight = 36.dp).clip(TazRadius.chip)
            .tazPressable(onClick = onClick, enabled = enabled, pressScale = TazPress.compact)
            .padding(horizontal = TazSpace.xs, vertical = TazSpace.sm)
    )
}

// ---------------------------------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------------------------------

@Composable
fun RemoteAddressesScreen() {
    val app = LocalAppState.current
    val book = ServiceLocator.addressBook
    val location = ServiceLocator.deliveryLocation
    val scope = rememberCoroutineScope()
    val state by book.state.collectAsState()
    val busy by book.busy.collectAsState()
    val notice by book.notice.collectAsState()
    val selected by location.selectedAddressId.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(app.isAuthenticated) { if (app.isAuthenticated) book.load() }

    fun run(block: suspend () -> AddressActionResult) {
        scope.launch { message = block().userMessage() }
    }

    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        TazTopBar("Saved addresses", onBack = { app.back() })
        if (!app.isAuthenticated) {
            EmptyState("📍", "Log in to manage addresses", "Your saved addresses live in your account.", "Log in", onAction = { app.navigate(Screen.Login) })
            return@Column
        }
        when (val s = state) {
            BookState.SignedOut, BookState.Idle, BookState.Loading -> Column(Modifier.padding(TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                repeat(3) { SkeletonBlock(height = 96.dp, corner = 14.dp) }
            }
            is BookState.Failed -> Column(Modifier.fillMaxWidth().padding(TazSpace.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.failure.title, fontSize = TazType.h2Size, fontWeight = TazType.h2Weight, color = TazColors.TextPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(TazSpace.xs))
                Text(s.failure.hint, fontSize = TazType.bodySize, color = TazColors.TextSecondary, textAlign = TextAlign.Center)
                if (s.failure.isRetryable) { Spacer(Modifier.height(TazSpace.lg)); PillButton("Try again", onClick = { book.refresh() }) }
            }
            is BookState.Loaded -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = TazSpace.xxxl)) {
                notice?.let { n -> item { InfoRow(n.message) { book.dismissNotice() } } }
                message?.let { m -> item { InfoRow(m) { message = null } } }
                item { DeliverySuggestionBanner() }
                item {
                    Column(Modifier.fillMaxWidth().padding(TazSpace.lg)) {
                        PillButton(
                            "Add address", onClick = { app.navigate(Screen.AddressForm(null)) },
                            modifier = Modifier.fillMaxWidth(), enabled = !s.atLimit && !busy
                        )
                        if (s.atLimit) Text(ADDRESS_LIMIT_MESSAGE, fontSize = TazType.captionSize, color = TazColors.TextSecondary, modifier = Modifier.padding(top = TazSpace.sm))
                    }
                }
                if (s.addresses.isEmpty()) item {
                    EmptyState("📍", "No saved addresses", "Add an address to keep your delivery details handy.")
                } else items(s.addresses, key = { it.addressId }) { a ->
                    AddressCard(
                        a, isSelected = selected == a.addressId, busy = busy, confirmingDelete = confirmDelete == a.addressId,
                        onSelect = { location.selectAddress(a) },
                        onSetDefault = { run { book.setDefault(a) } },
                        onEdit = { app.navigate(Screen.AddressForm(a.addressId)) },
                        onAskDelete = { confirmDelete = a.addressId },
                        onCancelDelete = { confirmDelete = null },
                        onDelete = { confirmDelete = null; run { book.delete(a) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(text: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(TazColors.WarningSoft).padding(horizontal = TazSpace.lg, vertical = TazSpace.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = TazType.captionSize, color = TazColors.Warning, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        TextAction("OK", TazColors.Warning, onClick = onDismiss)
    }
}

@Composable
private fun AddressCard(
    a: CustomerAddress, isSelected: Boolean, busy: Boolean, confirmingDelete: Boolean,
    onSelect: () -> Unit, onSetDefault: () -> Unit, onEdit: () -> Unit,
    onAskDelete: () -> Unit, onCancelDelete: () -> Unit, onDelete: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = TazSpace.lg, vertical = TazSpace.xs)
            .clip(TazRadius.card).background(TazColors.Surface).padding(TazSpace.lg),
        verticalArrangement = Arrangement.spacedBy(TazSpace.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)) {
            Text(a.label.display, fontSize = TazType.titleSize, fontWeight = TazType.titleWeight, color = TazColors.TextPrimary)
            if (a.isDefault) TazChip("Default", ChipTone.Brand)
            if (isSelected) TazChip("Delivering here", ChipTone.Success)
        }
        Text("${a.recipientName} · ${a.recipientPhone}", fontSize = TazType.captionSize, color = TazColors.TextSecondary)
        Text(
            listOfNotNull(a.addressLine1, a.addressLine2, a.landmark, "${a.city}, ${a.state} ${a.postalCode.value}").joinToString(", "),
            fontSize = TazType.bodySize, color = TazColors.TextPrimary
        )
        val tone = when (a.serviceability) {
            AddressServiceability.SERVICEABLE -> ChipTone.Success
            AddressServiceability.NOT_SERVICEABLE -> ChipTone.Warning
            AddressServiceability.UNKNOWN -> ChipTone.Neutral
        }
        TazChip(a.serviceability.badge, tone)
        if (confirmingDelete) {
            Text("Delete this address?", fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold, color = TazColors.Danger)
            Row(horizontalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                TextAction("Delete", TazColors.Danger, enabled = !busy, onClick = onDelete)
                TextAction("Cancel", TazColors.TextSecondary, onClick = onCancelDelete)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                if (!isSelected) TextAction("Deliver here", enabled = !busy, onClick = onSelect)
                if (!a.isDefault) TextAction("Set as default", enabled = !busy, onClick = onSetDefault)
                TextAction("Edit", enabled = !busy, onClick = onEdit)
                TextAction("Delete", TazColors.Danger, enabled = !busy, onClick = onAskDelete)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------
// Form
// ---------------------------------------------------------------------------------------------------

@Composable
fun RemoteAddressFormScreen(addressId: String?) {
    val app = LocalAppState.current
    val book = ServiceLocator.addressBook
    val scope = rememberCoroutineScope()
    val state by book.state.collectAsState()
    val busy by book.busy.collectAsState()
    val original = (state as? BookState.Loaded)?.addresses?.firstOrNull { it.addressId == addressId }
    // Re-prefill from the refreshed copy after a stale-version refresh.
    var generation by remember { mutableStateOf(0) }

    if (addressId != null && original == null) {
        LaunchedEffect(state) { if (state is BookState.Loaded) app.back() }   // it vanished (deleted elsewhere): leave the form
    }

    var label by remember(addressId, generation) { mutableStateOf<AddressLabel?>(original?.label) }
    var name by remember(addressId, generation) { mutableStateOf(original?.recipientName.orEmpty()) }
    var phone by remember(addressId, generation) { mutableStateOf(original?.recipientPhone.orEmpty()) }
    var line1 by remember(addressId, generation) { mutableStateOf(original?.addressLine1.orEmpty()) }
    var line2 by remember(addressId, generation) { mutableStateOf(original?.addressLine2.orEmpty()) }
    var landmark by remember(addressId, generation) { mutableStateOf(original?.landmark.orEmpty()) }
    var city by remember(addressId, generation) { mutableStateOf(original?.city.orEmpty()) }
    var stateName by remember(addressId, generation) { mutableStateOf(original?.state.orEmpty()) }
    var pin by remember(addressId, generation) { mutableStateOf(original?.postalCode?.value.orEmpty()) }
    var errors by remember { mutableStateOf<Map<AddressField, FieldError>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val input = AddressInput(label, name, phone, line1, line2, landmark, city, stateName, pin)
        scope.launch {
            val result = if (original == null) book.create(input) else book.update(original, input)
            when (result) {
                AddressActionResult.Success, AddressActionResult.NotFound, AddressActionResult.AmbiguousCreate -> app.back()
                is AddressActionResult.ValidationFailed -> { errors = result.errors; banner = null }
                AddressActionResult.Stale -> { errors = emptyMap(); generation++; banner = result.userMessage() }   // fresh values shown; customer retries explicitly
                else -> { errors = emptyMap(); banner = result.userMessage() }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(TazColors.Cream).imePadding()) {
        TazTopBar(if (addressId == null) "Add address" else "Edit address", onBack = { app.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
            banner?.let { Text(it, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.Danger) }

            Text("Label", fontSize = TazType.captionSize, color = TazColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)) {
                AddressLabel.entries.forEach { l ->
                    val on = label == l
                    Text(
                        l.display, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold,
                        color = if (on) TazColors.Surface else TazColors.TextPrimary,
                        modifier = Modifier.clip(TazRadius.chip).background(if (on) TazColors.Green else TazColors.SurfaceSunken)
                            .tazPressable(onClick = { label = l }, pressScale = TazPress.compact, selected = on)
                            .padding(horizontal = TazSpace.lg, vertical = TazSpace.sm)
                    )
                }
            }
            errors[AddressField.LABEL]?.let { ErrorText(it) }

            Field("Recipient name", name, { name = it }, errors[AddressField.RECIPIENT_NAME])
            Field("Recipient phone", phone, { phone = it }, errors[AddressField.RECIPIENT_PHONE], KeyboardType.Phone)
            Field("Address line 1", line1, { line1 = it }, errors[AddressField.ADDRESS_LINE1])
            Field("Address line 2 (optional)", line2, { line2 = it }, errors[AddressField.ADDRESS_LINE2])
            Field("Landmark (optional)", landmark, { landmark = it }, errors[AddressField.LANDMARK])
            Field("City", city, { city = it }, errors[AddressField.CITY])
            Field("State", stateName, { stateName = it }, errors[AddressField.STATE])
            Field("PIN code", pin, { pin = it.filter { c -> c in '0'..'9' }.take(6) }, errors[AddressField.POSTAL_CODE], KeyboardType.Number)

            Spacer(Modifier.height(TazSpace.sm))
            PillButton(
                if (addressId == null) "Save address" else "Save changes", onClick = ::submit,
                modifier = Modifier.fillMaxWidth(), loading = busy, loadingText = "Saving…", enabled = !busy
            )
            Spacer(Modifier.height(TazSpace.xxl))
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, error: FieldError?, keyboard: KeyboardType = KeyboardType.Text) {
    Column {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = TazRadius.card, colors = tazFieldColors(),
            isError = error != null, keyboardOptions = KeyboardOptions(keyboardType = keyboard)
        )
        error?.let { ErrorText(it) }
    }
}

@Composable
private fun ErrorText(e: FieldError) {
    Text(e.message, fontSize = TazType.captionSize, color = TazColors.Danger, modifier = Modifier.padding(start = TazSpace.xs, top = TazSpace.xxs))
}
