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
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.SkeletonBlock
import com.tazzzo.app.ui.common.TazChip
import com.tazzzo.app.ui.common.TazTopBar
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import com.tazzzo.app.ui.onboarding.tazFieldColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.ui.checkout.Header
import com.tazzzo.app.ui.checkout.PurchaseCopy
import com.tazzzo.app.ui.checkout.TextAction
import com.tazzzo.app.ui.common.EditorialEmptyState
import com.tazzzo.app.ui.common.EditorialText
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazzzoPrimaryButton
import com.tazzzo.app.ui.common.plain
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
            SuggestionAction("Use this address") { location.selectAddress(a) }
            SuggestionAction("Keep current location", TazColors.TextSecondary) { location.keepCurrentLocation() }
        }
    }
}

@Composable
private fun SuggestionAction(text: String, ink: androidx.compose.ui.graphics.Color = TazColors.Green, enabled: Boolean = true, onClick: () -> Unit) {
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

    AddressListLayout(
        authenticated = app.isAuthenticated, state = state, selectedId = selected, busy = busy,
        banner = notice?.message ?: message, confirmingDeleteId = confirmDelete,
        actions = AddressListActions(
            back = { app.back() }, login = { app.navigate(Screen.Login) }, retry = { book.refresh() },
            dismissBanner = { if (notice != null) book.dismissNotice() else message = null },
            add = { app.navigate(Screen.AddressForm(null)) },
            select = { location.selectAddress(it) },
            setDefault = { a -> run { book.setDefault(a) } },
            edit = { app.navigate(Screen.AddressForm(it.addressId)) },
            askDelete = { confirmDelete = it.addressId }, cancelDelete = { confirmDelete = null },
            delete = { a -> confirmDelete = null; run { book.delete(a) } }
        ),
        suggestion = { DeliverySuggestionBanner() }
    )
}

class AddressListActions(
    val back: () -> Unit, val login: () -> Unit, val retry: () -> Unit, val dismissBanner: () -> Unit, val add: () -> Unit,
    val select: (CustomerAddress) -> Unit, val setDefault: (CustomerAddress) -> Unit, val edit: (CustomerAddress) -> Unit,
    val askDelete: (CustomerAddress) -> Unit, val cancelDelete: () -> Unit, val delete: (CustomerAddress) -> Unit
)

/**
 * The address list (UI-05): editorial "Delivery address", one rounded card per REAL saved address with the location mark,
 * label, recipient, lines and the server's serviceability badge; the selected one carries the green border and "Delivering
 * here". "Add new address" is the one deep-green CTA, disabled with the truthful limit line at the backend's maximum.
 */
@Composable
fun AddressListLayout(
    authenticated: Boolean, state: BookState, selectedId: String?, busy: Boolean, banner: String?, confirmingDeleteId: String?,
    actions: AddressListActions, suggestion: @Composable () -> Unit = {}
) {
    Box(Modifier.fillMaxSize().background(TazColors.Cream).testTag("addresses")) {
        Column(Modifier.fillMaxSize()) {
            Header(PurchaseCopy.ADDRESSES_TITLE, onBack = actions.back)
            if (!authenticated) {
                EditorialEmptyState(TazIcons.Profile, "Log in to manage addresses", "Your saved addresses live in your account.", "Log in", onAction = actions.login)
                return@Column
            }
            when (state) {
                BookState.SignedOut, BookState.Idle, BookState.Loading -> Column(Modifier.padding(horizontal = TazSpace.lg).testTag("addressSkeleton"), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                    repeat(3) { SkeletonBlock(height = 112.dp, corner = TazRadius.tileDp) }
                }
                is BookState.Failed -> EditorialFailureLike(state.failure.title, state.failure.hint, if (state.failure.isRetryable) actions.retry else null)
                is BookState.Loaded -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = TazSpace.lg, end = TazSpace.lg, bottom = ADD_BAR_CLEARANCE), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                    banner?.let { b -> item { InfoRow(b, onDismiss = actions.dismissBanner) } }
                    item { suggestion() }
                    if (state.addresses.isEmpty()) item {
                        EditorialEmptyState(TazIcons.Location, PurchaseCopy.ADDRESS_EMPTY_TITLE, PurchaseCopy.ADDRESS_EMPTY_BODY)
                    } else items(state.addresses, key = { it.addressId }) { a ->
                        AddressCard(
                            a, isSelected = selectedId == a.addressId, busy = busy, confirmingDelete = confirmingDeleteId == a.addressId,
                            onSelect = { actions.select(a) }, onSetDefault = { actions.setDefault(a) }, onEdit = { actions.edit(a) },
                            onAskDelete = { actions.askDelete(a) }, onCancelDelete = actions.cancelDelete, onDelete = { actions.delete(a) }
                        )
                    }
                    if (state.atLimit) item {
                        Column(Modifier.fillMaxWidth().clip(TazRadius.tile).background(TazColors.Surface).padding(TazSpace.lg)) {
                            Text(PurchaseCopy.ADDRESS_LIMIT_TITLE, fontSize = TazType.titleSize, fontWeight = TazType.titleWeight, color = TazColors.TextPrimary)
                            Text("$ADDRESS_LIMIT_MESSAGE Remove one to add another.", fontSize = TazType.captionSize, color = TazColors.TextSecondary, modifier = Modifier.padding(top = TazSpace.xs))
                        }
                    }
                }
            }
        }
        if (authenticated && state is BookState.Loaded) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = TazSpace.lg).navigationBarsPadding().padding(bottom = TazSpace.md)) {
                TazzzoPrimaryButton(PurchaseCopy.ADD_ADDRESS, onClick = actions.add, enabled = !state.atLimit && !busy, modifier = Modifier.fillMaxWidth().testTag("addAddress"))
            }
        }
    }
}

@Composable
private fun EditorialFailureLike(title: String, hint: String, onRetry: (() -> Unit)?) {
    EditorialEmptyState(TazIcons.Offline, title, hint, if (onRetry != null) "Try again" else null, onAction = onRetry)
}

@Composable
private fun InfoRow(text: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.WarningSoft).padding(horizontal = TazSpace.lg, vertical = TazSpace.sm), verticalAlignment = Alignment.CenterVertically) {
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
        Modifier.fillMaxWidth().clip(TazRadius.tile).background(TazColors.Surface)
            .border(BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) TazColors.BrandEditorial else TazColors.CardBorder), TazRadius.tile)
            .semantics(mergeDescendants = false) { contentDescription = "${a.label.display} address" + if (isSelected) ", delivering here" else "" }
            .padding(TazSpace.lg),
        verticalArrangement = Arrangement.spacedBy(TazSpace.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(if (isSelected) TazColors.BrandEditorial else TazColors.GreenSoft), contentAlignment = Alignment.Center) {
                TazIcon(TazIcons.Location, null, size = TazSize.iconSm, tint = if (isSelected) TazColors.EditorialOnDark else TazColors.BrandEditorial)
            }
            Spacer(Modifier.width(TazSpace.md))
            EditorialText(listOf(plain(a.label.display)), size = TazType.editorialSectionSize, lineHeight = TazType.editorialSectionLine, color = TazColors.TextPrimary, textAlign = TextAlign.Start, modifier = Modifier.weight(1f))
            if (a.isDefault) TazChip("Default", ChipTone.Brand)
            if (isSelected) { Spacer(Modifier.width(TazSpace.xs)); TazChip("Delivering here", ChipTone.Success) }
        }
        Text("${a.recipientName} · ${a.recipientPhone}", fontSize = TazType.captionSize, color = TazColors.TextSecondary)
        Text(
            listOfNotNull(a.addressLine1, a.addressLine2?.takeIf { it.isNotBlank() }, a.landmark?.takeIf { it.isNotBlank() }, "${a.city}, ${a.state} ${a.postalCode.value}").joinToString(", "),
            fontSize = TazType.bodySize, lineHeight = TazType.bodyLine, color = TazColors.TextPrimary
        )
        val tone = when (a.serviceability) {
            AddressServiceability.SERVICEABLE -> ChipTone.Success
            AddressServiceability.NOT_SERVICEABLE -> ChipTone.Warning
            AddressServiceability.UNKNOWN -> ChipTone.Neutral
        }
        TazChip(a.serviceability.badge, tone)
        if (confirmingDelete) {
            Text("Delete this address?", fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold, color = TazColors.Danger)
            Row(horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)) {
                TextAction("Delete", TazColors.Danger, enabled = !busy, onClick = onDelete)
                TextAction("Cancel", TazColors.TextSecondary, onClick = onCancelDelete)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TazSpace.xs)) {
                if (!isSelected) TextAction("Deliver here", enabled = !busy, onClick = onSelect)
                if (!a.isDefault) TextAction("Set as default", TazColors.TextSecondary, enabled = !busy, onClick = onSetDefault)
                TextAction("Edit", TazColors.TextSecondary, enabled = !busy, onClick = onEdit)
                TextAction("Delete", TazColors.Danger, enabled = !busy, onClick = onAskDelete)
            }
        }
    }
}

private val ADD_BAR_CLEARANCE = 100.dp

// ---------------------------------------------------------------------------------------------------
// Form
// ---------------------------------------------------------------------------------------------------

/**
 * Add / edit address (UI-05), in the Login form language: cream page, Newsreader title, rounded cream fields with the
 * hairline border, Poppins labels and inline validation, the one deep-green Save CTA. The submission logic is unchanged:
 * client validation first, then the book's create/update; a stale edit re-fills from the refreshed copy and the customer
 * retries explicitly; the CTA is busy-locked so nothing is submitted twice.
 */
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
        if (busy) return
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

    AddressFormLayout(
        editing = addressId != null, label = label, name = name, phone = phone, line1 = line1, line2 = line2, landmark = landmark,
        city = city, stateName = stateName, pin = pin, errors = errors, banner = banner, busy = busy,
        onLabel = { label = it }, onName = { name = it }, onPhone = { phone = it }, onLine1 = { line1 = it }, onLine2 = { line2 = it },
        onLandmark = { landmark = it }, onCity = { city = it }, onState = { stateName = it }, onPin = { pin = it.filter { c -> c in '0'..'9' }.take(6) },
        onBack = { app.back() }, onSubmit = ::submit
    )
}

@Composable
fun AddressFormLayout(
    editing: Boolean, label: AddressLabel?, name: String, phone: String, line1: String, line2: String, landmark: String, city: String, stateName: String, pin: String,
    errors: Map<AddressField, FieldError>, banner: String?, busy: Boolean,
    onLabel: (AddressLabel) -> Unit, onName: (String) -> Unit, onPhone: (String) -> Unit, onLine1: (String) -> Unit, onLine2: (String) -> Unit,
    onLandmark: (String) -> Unit, onCity: (String) -> Unit, onState: (String) -> Unit, onPin: (String) -> Unit, onBack: () -> Unit, onSubmit: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(TazColors.Cream).imePadding().testTag("addressForm")) {
        Header(if (editing) "Edit address" else "Add address", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
            banner?.let {
                Row(Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.DangerSoft).padding(TazSpace.md)) {
                    Text(it, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.Danger)
                }
            }
            Text("Save as", fontSize = TazType.captionSize, fontWeight = FontWeight.Medium, color = TazColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)) {
                AddressLabel.entries.forEach { l ->
                    val on = label == l
                    Box(
                        Modifier.height(TazSize.chipHeight).clip(TazRadius.pill).background(if (on) TazColors.BrandEditorial else TazColors.Surface)
                            .border(BorderStroke(1.dp, if (on) TazColors.BrandEditorial else TazColors.BorderStrong), TazRadius.pill)
                            .tazPressable(onClick = { onLabel(l) }, pressScale = TazPress.compact, selected = on, role = androidx.compose.ui.semantics.Role.RadioButton)
                            .padding(horizontal = TazSpace.lg),
                        contentAlignment = Alignment.Center
                    ) { Text(l.display, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = if (on) TazColors.EditorialOnDark else TazColors.TextPrimary) }
                }
            }
            errors[AddressField.LABEL]?.let { ErrorText(it) }

            Field("Recipient name", name, onName, errors[AddressField.RECIPIENT_NAME])
            Field("Recipient phone", phone, onPhone, errors[AddressField.RECIPIENT_PHONE], KeyboardType.Phone)
            Field("Address line 1", line1, onLine1, errors[AddressField.ADDRESS_LINE1])
            Field("Address line 2 (optional)", line2, onLine2, errors[AddressField.ADDRESS_LINE2])
            Field("Landmark (optional)", landmark, onLandmark, errors[AddressField.LANDMARK])
            Row(horizontalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                Box(Modifier.weight(1f)) { Field("City", city, onCity, errors[AddressField.CITY]) }
                Box(Modifier.weight(1f)) { Field("State", stateName, onState, errors[AddressField.STATE]) }
            }
            Field("PIN code", pin, onPin, errors[AddressField.POSTAL_CODE], KeyboardType.Number)

            Spacer(Modifier.height(TazSpace.sm))
            TazzzoPrimaryButton(
                if (editing) "Save changes" else "Save address", onClick = onSubmit,
                modifier = Modifier.fillMaxWidth().testTag("saveAddress"), loading = busy, enabled = !busy, trailingArrow = false
            )
            Spacer(Modifier.navigationBarsPadding().height(TazSpace.xxl))
        }
    }
}

/** A rounded cream field in the Login language: hairline border, green focus, Poppins label, inline error beneath. */
@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, error: FieldError?, keyboard: KeyboardType = KeyboardType.Text) {
    Column {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(label, fontSize = TazType.bodySize) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = TazRadius.tile, colors = tazFieldColors(),
            isError = error != null, keyboardOptions = KeyboardOptions(keyboardType = keyboard)
        )
        error?.let { ErrorText(it) }
    }
}

@Composable
private fun ErrorText(e: FieldError) {
    Text(e.message, fontSize = TazType.captionSize, color = TazColors.Danger, modifier = Modifier.padding(start = TazSpace.sm, top = TazSpace.xxs))
}
