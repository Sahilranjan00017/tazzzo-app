package com.tazzzo.app.ui.checkout

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tazzzo.app.CheckoutSession
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.analytics.Analytics
import com.tazzzo.app.analytics.AnalyticsEvents
import com.tazzzo.app.TazzzoAppState
import com.tazzzo.app.data.model.Address
import com.tazzzo.app.data.model.CartIssue
import com.tazzzo.app.data.model.PaymentMethodKind
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.order.OrderPlacement
import androidx.compose.foundation.horizontalScroll
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.EmptyState
import com.tazzzo.app.data.model.PlaceOrderResult
import com.tazzzo.app.ui.interaction.rememberHaptics
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import androidx.compose.ui.semantics.Role
import com.tazzzo.app.ui.interaction.TazHaptic
import kotlinx.coroutines.CancellationException
import com.tazzzo.app.ui.state.toLoadError
import com.tazzzo.app.ui.state.LoadError
import com.tazzzo.app.ui.common.ErrorState
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazTopBar
import com.tazzzo.app.ui.state.rememberLoad
import com.tazzzo.app.ui.common.StateHost
import kotlinx.coroutines.launch

/**
 * The checkout flow UI. All state lives in [CheckoutSession] (the frozen
 * domain state machine); this file only renders it and calls its transitions.
 *
 * Visual contract: one selection-row silhouette for address, slot and payment;
 * one card border; one bottom bar. A customer never has to relearn the page
 * between steps.
 */
@Composable
fun CheckoutScreen() {
    val app = LocalAppState.current
    if (app.checkout == null) {
        app.checkout = CheckoutSession(
            // Carried over from the cart, so a choice made there is not lost
            // the moment checkout begins.
            initialInstructionIds = if (app.noCarryBag) setOf("no-bag") else emptySet()
        )
        Analytics.track(AnalyticsEvents.CHECKOUT_STARTED)
    }
    val session = app.checkout!!
    val scope = rememberCoroutineScope()

    val lines = app.cartLines()
    val bill = app.bill(lines)

    // Revalidation is what stands between the customer and an order placed
    // against stale stock or a stale price, so a thrown error here must be
    // visible and retryable — never silent. If it were swallowed, `validation`
    // would stay null, the CTA would stay disabled and the screen would sit on
    // "Checking your cart…" forever with no way out.
    var validationError by remember { mutableStateOf<LoadError?>(null) }
    val haptics = rememberHaptics()

    suspend fun runValidation() {
        validationError = null
        session.validation = null
        try {
            session.validation = ServiceLocator.checkout.validateCart(app.cartLines())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            validationError = t.toLoadError()
        }
    }

    LaunchedEffect(Unit) { runValidation() }

    fun revalidate() { scope.launch { runValidation() } }

    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        TazTopBar("Checkout", onBack = {
            if (!session.backStep()) {
                app.checkout = null
                app.back()
            }
        })

        if (lines.isEmpty() && session.placement !is CheckoutSession.Placement.Done) {
            EmptyState(
                "🛒", "Your cart is empty",
                actionLabel = "Browse products",
                onAction = { app.back() }
            )
            return@Column
        }

        StepIndicator(session.step)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(TazSpace.screen)
        ) {
            validationError?.let { error ->
                ErrorState(error, onRetry = { revalidate() })
                Spacer(Modifier.height(TazSpace.md))
            }

            val validation = session.validation
            if (validation != null && !validation.ok) {
                IssuesCard(
                    issues = validation.issues,
                    onFix = { issue ->
                        val line = app.cartLines().find { it.product.id == issue.productId }
                        if (line != null) {
                            val toRemove = when (issue) {
                                is CartIssue.OutOfStock -> line.quantity
                                is CartIssue.PriceChanged -> line.quantity
                                is CartIssue.QuantityReduced ->
                                    (line.quantity - issue.available).coerceAtLeast(0)
                            }
                            repeat(toRemove) { app.removeFromCart(line.product) }
                        }
                        revalidate()
                    }
                )
                Spacer(Modifier.height(TazSpace.md))
            }

            Crossfade(targetState = session.step) { step ->
                when (step) {
                    CheckoutSession.Step.ADDRESS -> AddressStep(session)
                    CheckoutSession.Step.SLOT -> SlotStep(session)
                    CheckoutSession.Step.PAYMENT -> PaymentStep(session)
                    CheckoutSession.Step.REVIEW -> ReviewStep(session, app)
                }
            }
        }

        // ----- Sticky bottom bar -----
        Column(
            Modifier.fillMaxWidth()
                .shadow(12.dp, spotColor = Color.Black.copy(alpha = 0.30f))
                .background(TazColors.Surface)
        ) {
            Hairline()
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(TazSpace.gutter)
            ) {
                if (session.validation == null && validationError == null) {
                    Text(
                        "Checking your cart…",
                        fontSize = TazType.captionSize, color = TazColors.TextSecondary
                    )
                    Spacer(Modifier.height(TazSpace.sm))
                }

                // Why the Continue button is disabled, in one line (selection gates only;
                // cart problems are already explained by the IssuesCard above).
                val gateHint: String? =
                    if (session.step != CheckoutSession.Step.REVIEW &&
                        !session.canAdvanceFrom(session.step)
                    ) {
                        when (session.step) {
                            CheckoutSession.Step.ADDRESS ->
                                if (session.address != null && session.address?.isServiceable != true)
                                    "This address isn't serviceable — pick another"
                                else "Select a delivery address to continue"
                            CheckoutSession.Step.SLOT -> "Pick a delivery slot to continue"
                            CheckoutSession.Step.PAYMENT -> "Choose a payment method to continue"
                            CheckoutSession.Step.REVIEW -> null
                        }
                    } else null
                if (gateHint != null) {
                    Text(
                        gateHint,
                        fontSize = TazType.captionSize, color = TazColors.Warning,
                        lineHeight = TazType.captionLine
                    )
                    Spacer(Modifier.height(TazSpace.sm))
                }

                // The payable amount stays visible on every step.
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "To pay",
                        fontSize = TazType.captionSize, color = TazColors.TextSecondary
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${bill.grandTotal}",
                        fontSize = TazType.titleSize, fontWeight = FontWeight.Bold,
                        color = TazColors.TextPrimary
                    )
                }
                Spacer(Modifier.height(TazSpace.md))

                if (session.step != CheckoutSession.Step.REVIEW) {
                    val enabled = session.canAdvanceFrom(session.step) &&
                        session.validation?.ok == true
                    PillButton(
                        text = "Continue",
                        onClick = {
                            Analytics.track(
                                AnalyticsEvents.CHECKOUT_STEP_COMPLETED,
                                mapOf("step" to session.step.name)
                            )
                            session.next()
                        },
                        enabled = enabled,
                        disabledHint = gateHint,
                        modifier = Modifier.fillMaxWidth().heightIn(min = TazSize.buttonHeight)
                    )
                } else {
                    when (val placement = session.placement) {
                        is CheckoutSession.Placement.InFlight,
                        is CheckoutSession.Placement.Done -> {
                            PillButton(
                                text = "Placing your order…",
                                onClick = { },
                                enabled = false,
                                modifier = Modifier.fillMaxWidth().heightIn(min = TazSize.buttonHeight)
                            )
                        }
                        else -> {
                            if (placement is CheckoutSession.Placement.Failed) {
                                Row(
                                    Modifier.fillMaxWidth().clip(TazRadius.card)
                                        .background(TazColors.DangerSoft).padding(TazSpace.md),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    TazIcon(
                                        TazIcons.Error, null,
                                        size = TazSize.iconSm, tint = TazColors.Danger
                                    )
                                    Spacer(Modifier.width(TazSpace.sm))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            placement.reason,
                                            fontSize = TazType.bodySize,
                                            fontWeight = FontWeight.SemiBold,
                                            lineHeight = TazType.bodyLine,
                                            color = TazColors.Danger
                                        )
                                        Spacer(Modifier.height(TazSpace.xxs))
                                        Text(
                                            "Your cart is untouched.",
                                            fontSize = TazType.captionSize,
                                            color = TazColors.TextSecondary
                                        )
                                    }
                                }
                                Spacer(Modifier.height(TazSpace.md))
                            }
                            val enabled = session.validation?.ok == true &&
                                session.address != null && session.slot != null &&
                                session.payment != null
                            PillButton(
                                text = if (placement is CheckoutSession.Placement.Failed) "Try again"
                                else "Place order  ·  ${bill.grandTotal}",
                                enabled = enabled,
                                // The single most consequential button in the
                                // app. While a placement is in flight it must
                                // look busy AND refuse further taps — the
                                // component enforces both, so the in-flight
                                // guard in OrderPlacement is a second line of
                                // defence rather than the only one.
                                loading = placement is CheckoutSession.Placement.InFlight,
                                loadingText = "Placing your order…",
                                haptic = TazHaptic.Tap,
                                onClick = {
                                    if (!enabled) return@PillButton
                                    scope.launch {
                                        val address = session.address ?: return@launch
                                        val slot = session.slot ?: return@launch
                                        val payment = session.payment ?: return@launch
                                        // Single shared placement path. It owns the
                                        // in-flight guard, the analytics, and the
                                        // replay-gated side effects, so this screen and
                                        // the demo autopilot cannot drift apart again.
                                        val outcome = OrderPlacement.place(
                                            app = app,
                                            session = session,
                                            address = address,
                                            slot = slot,
                                            payment = payment
                                        )
                                        // The payoff of the entire journey, and
                                        // the one place a success haptic is
                                        // earned. A replayed placement is the
                                        // same order coming back, so it is still
                                        // a success from the customer's point of
                                        // view — but it moves no money and is
                                        // already gated inside OrderPlacement.
                                        when (outcome) {
                                            is PlaceOrderResult.Placed ->
                                                haptics.perform(TazHaptic.Success)
                                            is PlaceOrderResult.Failed,
                                            is PlaceOrderResult.Rejected ->
                                                haptics.perform(TazHaptic.Error)
                                            null -> Unit   // refused in flight
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = TazSize.buttonHeight)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step indicator
// ---------------------------------------------------------------------------

private val StepCircle = 28.dp

@Composable
private fun StepIndicator(current: CheckoutSession.Step) {
    val steps = CheckoutSession.Step.entries
    Column(Modifier.fillMaxWidth().background(TazColors.Surface)) {
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = TazSpace.gutter, vertical = TazSpace.md),
            verticalAlignment = Alignment.Top
        ) {
            steps.forEachIndexed { index, step ->
                if (index > 0) {
                    // Connector is green only once the step it trails from is done.
                    val prevDone = steps[index - 1].n < current.n
                    Box(
                        Modifier.weight(1f)
                            .padding(
                                top = StepCircle / 2 - 1.dp,
                                start = TazSpace.xs, end = TazSpace.xs
                            )
                            .height(2.dp)
                            .background(if (prevDone) TazColors.Green else TazColors.CardBorder)
                    )
                }
                // Three states: done (behind us), current (here), upcoming (ahead).
                val done = step.n < current.n
                val isCurrent = step.n == current.n
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(StepCircle).clip(CircleShape)
                            .background(
                                if (done || isCurrent) TazColors.Green else TazColors.Surface
                            )
                            .then(
                                if (done || isCurrent) Modifier
                                else Modifier.border(
                                    BorderStroke(1.5.dp, TazColors.CardBorder), CircleShape
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (done) {
                            TazIcon(
                                TazIcons.Check, null,
                                size = TazSize.iconXs, tint = TazColors.White
                            )
                        } else {
                            Text(
                                "${step.n}",
                                fontSize = TazType.captionSize, fontWeight = FontWeight.Bold,
                                color = if (isCurrent) TazColors.White else TazColors.TextTertiary
                            )
                        }
                    }
                    Spacer(Modifier.height(TazSpace.xs))
                    Text(
                        step.title,
                        fontSize = TazType.microSize,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else TazType.microWeight,
                        color = when {
                            isCurrent -> TazColors.TextPrimary
                            done -> TazColors.TextSecondary
                            else -> TazColors.TextTertiary
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Hairline()
    }
}

// ---------------------------------------------------------------------------
// Cart issues (revalidation results)
// ---------------------------------------------------------------------------

@Composable
private fun IssuesCard(issues: List<CartIssue>, onFix: (CartIssue) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(TazRadius.card)
            .background(TazColors.WarningSoft).padding(TazSpace.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TazIcon(TazIcons.Error, null, size = TazSize.iconSm, tint = TazColors.Warning)
            Spacer(Modifier.width(TazSpace.sm))
            Text(
                "Your cart needs attention",
                fontSize = TazType.titleSize, fontWeight = TazType.titleWeight,
                color = TazColors.TextPrimary
            )
        }
        issues.forEach { issue ->
            Spacer(Modifier.height(TazSpace.sm))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when (issue) {
                        is CartIssue.OutOfStock ->
                            "${issue.productName} is now out of stock — remove it to continue"
                        is CartIssue.QuantityReduced ->
                            "Only ${issue.available} of ${issue.productName} available"
                        is CartIssue.PriceChanged ->
                            "${issue.productName} is now ${issue.newPrice} (was ${issue.oldPrice}) — " +
                                "remove it and add it again at the new price"
                    },
                    fontSize = TazType.bodySize, color = TazColors.TextPrimary,
                    lineHeight = TazType.bodyLine,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(TazSpace.md))
                // Comfortable tap target: 44dp minimum height + horizontal breathing room.
                Box(
                    Modifier.clip(TazRadius.pill).tazPressable(onClick = { onFix(issue) }, pressScale = TazPress.compact)
                        .defaultMinSize(minHeight = TazSize.touchTarget)
                        .padding(horizontal = TazSpace.sm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when (issue) {
                            is CartIssue.OutOfStock -> "Remove"
                            is CartIssue.QuantityReduced -> "Adjust"
                            is CartIssue.PriceChanged -> "Refresh"
                        },
                        fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold,
                        color = TazColors.Green, maxLines = 1
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared row atoms
// ---------------------------------------------------------------------------

/** The 1dp rule used to separate layers without reaching for a shadow. */
@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(TazColors.CardBorder))
}

private val RadioSize = 22.dp

@Composable
private fun RadioDot(selected: Boolean, enabled: Boolean = true) {
    Box(
        Modifier.size(RadioSize).clip(CircleShape)
            .border(
                BorderStroke(
                    2.dp,
                    when {
                        selected -> TazColors.Green
                        enabled -> TazColors.BorderStrong
                        else -> TazColors.CardBorder
                    }
                ),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(Modifier.size(TazSpace.md).clip(CircleShape).background(TazColors.Green))
        }
    }
}

/**
 * The one selection silhouette used by address, slot and payment.
 *
 * Selected reads as a 2dp green frame over a green tint; unselected as a plain
 * hairline card; unavailable dims to 55% and states why in a warning chip.
 */
@Composable
private fun SelectionRow(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    leading: ImageVector? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            // alpha first: the whole row — card, border and content — dims as one
            .alpha(if (enabled) 1f else 0.55f)
            .clip(TazRadius.card)
            .background(if (selected) TazColors.GreenSoft else TazColors.Surface)
            .border(
                BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) TazColors.Green else TazColors.CardBorder
                ),
                TazRadius.card
            )
            // One selection response for address, slot and payment alike, so
            // choosing feels the same wherever the customer is in checkout.
            // Row scale is deliberately off (motion on a wide row is
            // distracting); the shape-clipped tint carries the press.
            .tazPressable(
                onClick = onClick,
                enabled = enabled,
                pressScale = TazPress.row,
                shape = TazRadius.card,
                haptic = TazHaptic.Select,
                role = Role.RadioButton,
                selected = selected
            )
            .padding(TazSpace.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioDot(selected, enabled = enabled)
        Spacer(Modifier.width(TazSpace.md))
        if (leading != null) {
            TazIcon(
                leading, null, size = TazSize.iconSm,
                tint = if (selected) TazColors.Green else TazColors.TextSecondary
            )
            Spacer(Modifier.width(TazSpace.md))
        }
        Column(Modifier.weight(1f), content = content)
        if (trailing != null) {
            Spacer(Modifier.width(TazSpace.sm))
            trailing()
        }
    }
}

/** Status chip: warning tone for "can't use this", neutral for "not yet built". */
@Composable
private fun StatusChip(text: String, warning: Boolean) {
    Box(
        Modifier.clip(TazRadius.chip)
            .background(if (warning) TazColors.WarningSoft else TazColors.SurfaceSunken)
            .padding(horizontal = TazSpace.sm, vertical = TazSpace.xxs)
    ) {
        Text(
            text, fontSize = TazType.microSize, fontWeight = TazType.microWeight,
            color = if (warning) TazColors.Warning else TazColors.TextTertiary,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun UnserviceableBanner() {
    Row(
        Modifier.fillMaxWidth().clip(TazRadius.card)
            .background(TazColors.WarningSoft).padding(TazSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TazIcon(TazIcons.Error, null, size = TazSize.iconSm, tint = TazColors.Warning)
        Spacer(Modifier.width(TazSpace.sm))
        Text(
            "We don't deliver here yet",
            fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold,
            color = TazColors.Warning
        )
    }
}

// ---------------------------------------------------------------------------
// Step 1 — Address
// ---------------------------------------------------------------------------

@Composable
private fun AddressStep(session: CheckoutSession) {
    val scope = rememberCoroutineScope()
    val addresses = rememberLoad { ServiceLocator.addresses.getAddresses() }

    Column {
        StateHost(addresses) { list ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                list.forEach { address ->
                    val selected = session.address?.id == address.id
                    SelectionRow(
                        selected = selected,
                        enabled = true,
                        onClick = { session.selectAddress(address) },
                        leading = TazIcons.Location,
                        trailing = if (!address.isServiceable) {
                            { StatusChip("Not serviceable", warning = true) }
                        } else null
                    ) {
                        Text(
                            address.label,
                            fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold,
                            color = TazColors.TextPrimary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(TazSpace.xxs))
                        Text(
                            listOf(address.line1, address.line2, address.pincode)
                                .filter { it.isNotBlank() }.joinToString(", "),
                            fontSize = TazType.captionSize, color = TazColors.TextSecondary,
                            lineHeight = TazType.captionLine,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (session.address?.isServiceable == false) {
            Spacer(Modifier.height(TazSpace.md))
            UnserviceableBanner()
        }

        Spacer(Modifier.height(TazSpace.md))

        // ----- Add new address (inline form) -----
        var showForm by remember { mutableStateOf(false) }
        var label by remember { mutableStateOf("") }
        var line1 by remember { mutableStateOf("") }
        var line2 by remember { mutableStateOf("") }
        var pincode by remember { mutableStateOf("") }
        var formError by remember { mutableStateOf<String?>(null) }
        var saving by remember { mutableStateOf(false) }

        if (!showForm) {
            Row(
                Modifier.clip(TazRadius.pill).tazPressable(onClick = { showForm = true }, pressScale = TazPress.compact)
                    .defaultMinSize(minHeight = TazSize.touchTarget)
                    .padding(horizontal = TazSpace.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TazIcon(TazIcons.Plus, null, size = TazSize.iconSm, tint = TazColors.Green)
                Spacer(Modifier.width(TazSpace.xs))
                Text(
                    "Add new address",
                    fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold,
                    color = TazColors.Green
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.Surface)
                    .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
                    .padding(TazSpace.lg),
                verticalArrangement = Arrangement.spacedBy(TazSpace.sm)
            ) {
                Text(
                    "New address",
                    fontSize = TazType.titleSize, fontWeight = TazType.titleWeight,
                    color = TazColors.TextPrimary
                )
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Label (Home, Work…)") },
                    singleLine = true, shape = TazRadius.chip,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = line1, onValueChange = { line1 = it },
                    label = { Text("Address line 1") },
                    singleLine = true, shape = TazRadius.chip,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = line2, onValueChange = { line2 = it },
                    label = { Text("Address line 2 (optional)") },
                    singleLine = true, shape = TazRadius.chip,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pincode, onValueChange = { pincode = it },
                    label = { Text("Pincode") },
                    singleLine = true, shape = TazRadius.chip,
                    modifier = Modifier.fillMaxWidth()
                )
                formError?.let {
                    Text(
                        it, fontSize = TazType.captionSize, color = TazColors.Danger,
                        lineHeight = TazType.captionLine
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                    PillButton(
                        text = if (saving) "Saving…" else "Save address",
                        onClick = {
                            if (saving) return@PillButton
                            val error = Address.validate(label, line1, pincode)
                            if (error != null) {
                                formError = error
                            } else {
                                formError = null
                                scope.launch {
                                    saving = true
                                    try {
                                        val added = ServiceLocator.addresses
                                            .addAddress(label, line1, line2, pincode)
                                        session.selectAddress(added)
                                        showForm = false
                                        label = ""; line1 = ""; line2 = ""; pincode = ""
                                        addresses.retry()
                                    } catch (cancellation: CancellationException) {
                                        throw cancellation
                                    } catch (t: Throwable) {
                                        // Keep the typed address on screen; the
                                        // customer must not retype it.
                                        formError = t.toLoadError().message
                                    } finally {
                                        saving = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).alpha(if (saving) 0.45f else 1f)
                    )
                    PillButton(
                        text = "Cancel",
                        onClick = { showForm = false; formError = null },
                        filled = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 2 — Delivery slot
// ---------------------------------------------------------------------------

@Composable
private fun SlotStep(session: CheckoutSession) {
    val slots = rememberLoad(session.address?.id) {
        ServiceLocator.checkout.getSlots(session.address!!.id)
    }
    StateHost(
        slots,
        empty = {
            EmptyState(
                "📍", "We don't deliver here yet", "Try a different address",
                actionLabel = "Change address",
                onAction = { session.backStep() }
            )
        }
    ) { list ->
        // Grouped by when, with the fee on every row. A slot is a CHOICE with a
        // price attached, not a label — so the price sits where the choice is
        // made, and a recommended slot is tagged, never silently pre-selected.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            list.groupBy { it.group ?: "" }.forEach { (group, slots) ->
                if (group.isNotBlank()) {
                    Text(
                        group.uppercase(), fontSize = TazType.microSize, fontWeight = TazType.microWeight,
                        letterSpacing = TazType.labelTracking, color = TazColors.TextTertiary,
                        modifier = Modifier.padding(top = TazSpace.xs)
                    )
                }
                slots.forEach { slot ->
                    val selected = session.slot?.id == slot.id
                    SelectionRow(
                        selected = selected,
                        enabled = slot.available,
                        onClick = { session.slot = slot },
                        leading = TazIcons.Slot,
                        trailing = {
                            if (!slot.available) StatusChip("Full", warning = true)
                            else Text(
                                if (slot.fee.isZero) "Free" else "${slot.fee}",
                                fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold,
                                color = if (slot.fee.isZero) TazColors.Success else TazColors.TextPrimary
                            )
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                slot.label,
                                fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold,
                                color = TazColors.TextPrimary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            if (slot.recommended && slot.available) {
                                Spacer(Modifier.width(TazSpace.sm))
                                StatusChip("Recommended", warning = false)
                            }
                        }
                        slot.feeReason?.takeIf { slot.available && slot.fee.isPositive }?.let {
                            Text(it, fontSize = TazType.captionSize, color = TazColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 3 — Payment
// ---------------------------------------------------------------------------

@Composable
private fun PaymentStep(session: CheckoutSession) {
    val methods = rememberLoad { ServiceLocator.checkout.getPaymentMethods() }
    StateHost(methods) { list ->
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            list.forEach { method ->
                val selected = session.payment == method.kind
                SelectionRow(
                    selected = selected,
                    enabled = method.enabled,
                    onClick = {
                        session.payment = method.kind
                        Analytics.track(
                            AnalyticsEvents.PAYMENT_SELECTED,
                            mapOf("method" to method.kind.name)
                        )
                    },
                    leading = when (method.kind) {
                        PaymentMethodKind.COD -> TazIcons.Payment
                        PaymentMethodKind.UPI, PaymentMethodKind.CARD -> TazIcons.Card
                    },
                    trailing = if (!method.enabled && method.note != null) {
                        { StatusChip(method.note!!, warning = false) }
                    } else null
                ) {
                    Text(
                        method.label,
                        fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold,
                        color = TazColors.TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (method.kind == PaymentMethodKind.COD) {
                        Spacer(Modifier.height(TazSpace.xxs))
                        Text(
                            "Pay when your order arrives",
                            fontSize = TazType.captionSize, color = TazColors.TextSecondary,
                            lineHeight = TazType.captionLine
                        )
                    }
                    // Where the mockup put a CVV box. Card and UPI details are
                    // entered inside the provider's own secure checkout and
                    // never in a Tazzzo view — taking a CVV in app-owned UI is
                    // a PCI violation, and there is no gateway behind it yet.
                    if (method.kind == PaymentMethodKind.CARD) {
                        Spacer(Modifier.height(TazSpace.xxs))
                        Text(
                            "Card details are entered on your bank's secure page, never in Tazzzo",
                            fontSize = TazType.captionSize, color = TazColors.TextTertiary,
                            lineHeight = TazType.captionLine
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 4 — Review
// ---------------------------------------------------------------------------

@Composable
private fun ReviewStep(session: CheckoutSession, app: TazzzoAppState) {
    val lines = app.cartLines()
    val bill = app.bill(lines)

    // Jump back through the state machine without touching CheckoutSession's API.
    fun jump(target: CheckoutSession.Step) {
        while (session.step.n > target.n && session.backStep()) { /* stepping back */ }
    }

    Column(verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {

        // ----- 1. Items -----
        ReviewCard(title = "Items", actionLabel = "Edit cart", onAction = {
            app.checkout = null
            app.back()
        }) {
            lines.forEachIndexed { index, line ->
                if (index > 0) Spacer(Modifier.height(TazSpace.sm))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${line.product.name} ×${line.quantity}",
                        fontSize = TazType.bodySize, color = TazColors.TextSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(TazSpace.md))
                    Text(
                        "${line.lineTotal}",
                        fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold,
                        color = TazColors.TextPrimary
                    )
                }
            }
        }

        // ----- 2. Delivery -----
        ReviewCard(title = "Delivery", actionLabel = "Change", onAction = {
            jump(CheckoutSession.Step.ADDRESS)
        }) {
            Text(
                session.address?.let { "${it.label} — ${it.line1}" } ?: "—",
                fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold,
                color = TazColors.TextPrimary, lineHeight = TazType.bodyLine
            )
            Spacer(Modifier.height(TazSpace.xxs))
            Text(
                session.slot?.let { s ->
                    s.label + (if (s.fee.isZero) " · Free" else " · ${s.fee}")
                } ?: "—",
                fontSize = TazType.captionSize, color = TazColors.TextSecondary
            )
        }

        // ----- 3. Payment -----
        ReviewCard(title = "Payment", actionLabel = "Change", onAction = {
            jump(CheckoutSession.Step.PAYMENT)
        }) {
            Text(
                when (session.payment) {
                    PaymentMethodKind.COD -> "Cash on Delivery"
                    PaymentMethodKind.UPI -> "UPI"
                    PaymentMethodKind.CARD -> "Credit / Debit Card"
                    null -> "—"
                },
                fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold,
                color = TazColors.TextPrimary
            )
        }

        // ----- 3b. Delivery instructions (optional, market-configurable) -----
        ReviewCard(title = "Delivery instructions") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)
            ) {
                com.tazzzo.app.config.defaultDeliveryInstructions.forEach { opt ->
                    val on = opt.id in session.instructionIds
                    Box(
                        Modifier.height(TazSize.chipHeight).clip(TazRadius.pill)
                            .background(if (on) TazColors.Green else TazColors.Surface)
                            .border(BorderStroke(1.dp, if (on) TazColors.Green else TazColors.CardBorder), TazRadius.pill)
                            .tazPressable(
                                onClick = { session.toggleInstruction(opt.id) },
                                pressScale = TazPress.compact, haptic = TazHaptic.Select,
                                role = Role.Checkbox, selected = on
                            )
                            .padding(horizontal = TazSpace.md),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            opt.label, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold,
                            color = if (on) TazColors.White else TazColors.TextPrimary, maxLines = 1
                        )
                    }
                }
            }
        }

        // ----- 4. Bill -----
        ReviewCard(title = "Bill details") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Item total", fontSize = TazType.bodySize,
                    color = TazColors.TextSecondary, modifier = Modifier.weight(1f)
                )
                if (bill.itemMrpTotal > bill.itemTotal) {
                    Text(
                        "${bill.itemMrpTotal}",
                        fontSize = TazType.mrpSize, color = TazColors.TextTertiary,
                        textDecoration = TextDecoration.LineThrough
                    )
                    Spacer(Modifier.width(TazSpace.sm))
                }
                Text(
                    "${bill.itemTotal}", fontSize = TazType.bodySize,
                    fontWeight = FontWeight.SemiBold, color = TazColors.TextPrimary
                )
            }
            // ---- discounts, each on its own line, before the customer commits ----
            // The review is the last place a total can surprise someone. Every
            // rupee taken off is itemised here exactly as on the cart; nothing
            // is folded into "item total".
            bill.appliedPromotions.filter { it.discount.isPositive }.forEach { promo ->
                Spacer(Modifier.height(TazSpace.md))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(promo.title, fontSize = TazType.bodySize, color = TazColors.TextSecondary)
                        Text(
                            promo.explanation, fontSize = TazType.microSize,
                            lineHeight = TazType.microLine, color = TazColors.TextTertiary
                        )
                    }
                    Text(
                        "−${promo.discount}", fontSize = TazType.bodySize,
                        fontWeight = FontWeight.SemiBold, color = TazColors.Green
                    )
                }
            }
            if (bill.clubDiscount.isPositive) {
                Spacer(Modifier.height(TazSpace.md))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Tazzzo Club savings", fontSize = TazType.bodySize,
                        color = TazColors.TextSecondary, modifier = Modifier.weight(1f)
                    )
                    Text(
                        "−${bill.clubDiscount}", fontSize = TazType.bodySize,
                        fontWeight = FontWeight.SemiBold, color = TazColors.Green
                    )
                }
            }
            bill.bestOfferNote?.let { note ->
                Spacer(Modifier.height(TazSpace.sm))
                Text(
                    note, fontSize = TazType.captionSize, lineHeight = TazType.captionLine,
                    color = TazColors.TextSecondary
                )
            }
            Spacer(Modifier.height(TazSpace.md))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Delivery fee", fontSize = TazType.bodySize,
                    color = TazColors.TextSecondary, modifier = Modifier.weight(1f)
                )
                if (bill.deliveryFee.isZero) {
                    Text(
                        "FREE", fontSize = TazType.bodySize,
                        fontWeight = FontWeight.Bold, color = TazColors.Success
                    )
                } else {
                    Text(
                        "${bill.deliveryFee}", fontSize = TazType.bodySize,
                        fontWeight = FontWeight.SemiBold, color = TazColors.TextPrimary
                    )
                }
            }
            Spacer(Modifier.height(TazSpace.md))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Handling charge", fontSize = TazType.bodySize,
                    color = TazColors.TextSecondary, modifier = Modifier.weight(1f)
                )
                Text(
                    "${bill.handlingCharge}", fontSize = TazType.bodySize,
                    fontWeight = FontWeight.SemiBold, color = TazColors.TextPrimary
                )
            }
            Spacer(Modifier.height(TazSpace.md))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TazIcon(TazIcons.Coin, null, size = TazSize.iconXs, tint = TazColors.CoinGold)
                Spacer(Modifier.width(TazSpace.xs))
                Text(
                    "Coins you'll earn", fontSize = TazType.bodySize,
                    color = TazColors.TextSecondary, modifier = Modifier.weight(1f)
                )
                Text(
                    "+${bill.coinsEarned}", fontSize = TazType.bodySize,
                    fontWeight = FontWeight.Bold, color = TazColors.Success
                )
            }
            Spacer(Modifier.height(TazSpace.md))
            HorizontalDivider(color = TazColors.CardBorder)
            Spacer(Modifier.height(TazSpace.md))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Grand total", fontSize = TazType.titleSize,
                    fontWeight = FontWeight.Bold, color = TazColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${bill.grandTotal}", fontSize = TazType.titleSize,
                    fontWeight = FontWeight.Bold, color = TazColors.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun ReviewCard(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.Surface)
            .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
            .padding(TazSpace.lg)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title, fontSize = TazType.titleSize, fontWeight = TazType.titleWeight,
                color = TazColors.TextPrimary, modifier = Modifier.weight(1f)
            )
            if (actionLabel != null && onAction != null) {
                Box(
                    Modifier.clip(TazRadius.pill).tazPressable(onClick = { onAction() }, pressScale = TazPress.compact)
                        .defaultMinSize(minHeight = TazSize.touchTarget)
                        .padding(horizontal = TazSpace.sm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        actionLabel,
                        fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold,
                        color = TazColors.Green, maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(TazSpace.md))
        content()
    }
}
