package com.tazzzo.app.ui.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.config.AppConfig
import com.tazzzo.app.config.BrandCopy
import com.tazzzo.app.config.DeliveryCopy
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import com.tazzzo.app.ui.interaction.TazHaptic
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.CategoryArtTile
import com.tazzzo.app.ui.common.categoryArtTiles
import com.tazzzo.app.ui.common.LogoImage
import com.tazzzo.app.ui.common.MarqueeRow
import com.tazzzo.app.ui.common.PillButton
import kotlinx.coroutines.CancellationException
import com.tazzzo.app.ui.state.toLoadError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/** Height of the photographic band at the top of the entry screen. */
private val WallHeight = 300.dp

/** One photographic tile in the wall. */
private val tileSize = 132.dp

/**
 * Login / guest entry — the customer's first interactive screen.
 *
 * Composition, top to bottom:
 *   1. a fixed 300dp photographic band (two counter-scrolling marquee rows)
 *      that fades into the cream page at the bottom and is scrimmed at the top
 *      so status-bar glyphs stay legible;
 *   2. a white logo badge pulled UP over that photography, so the brand block
 *      overlaps the imagery instead of sitting on top of it;
 *   3. headline, one supporting line, and a three-item value strip — every
 *      claim sourced from BrandCopy / DeliveryCopy / AppConfig;
 *   4. the phone → OTP form, then an unmissable green "Skip for now" and the
 *      plain legal caption.
 *
 * Reachability: the band has a fixed height but lives INSIDE the single
 * vertical scroll, and the root carries imePadding. On a 320x570dp device the
 * content measures ~800dp, so the primary CTA is always scrollable into view —
 * including when the keyboard is up, because the band scrolls away with it.
 *
 * Demo auth: any 4-digit OTP verifies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Short screens (480x854-class ≈ 570dp tall) get a shallower photo band
        // and smaller tiles so the primary CTA stays above the fold rather than
        // relying on the customer to scroll for it.
        val compact = maxHeight < 700.dp
        OnboardingContent(
            wallHeight = if (compact) 132.dp else WallHeight,
            tileSize = if (compact) 104.dp else tileSize,
            compact = compact
        )
    }
}

@Composable
private fun OnboardingContent(wallHeight: Dp, tileSize: Dp, compact: Boolean) {
    val app = LocalAppState.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(1) }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    // Was a bare Boolean meaning "the server said the OTP was wrong". It now
    // carries the message, because an unreachable auth service and a rejected
    // OTP are different things and the customer must not be told they typed it
    // wrong when the network failed. Null = no error.
    var errorText by remember { mutableStateOf<String?>(null) }
    var resendIn by remember { mutableStateOf(30) }

    // Resend countdown: ticks once a second while the OTP step is showing.
    // Tapping "Resend OTP" just resets [resendIn]; this loop keeps ticking.
    LaunchedEffect(step) {
        if (step != 2) return@LaunchedEffect
        resendIn = 30
        while (true) {
            delay(1_000)
            if (resendIn > 0) resendIn--
        }
    }

    // Decorative brand wall. Deliberately drawn from the bundled art table, not
    // from CatalogRepository: this is the pre-auth screen and it must render
    // instantly, offline, with no failure state. Nothing here is merchandise.
    val rowOne = remember { categoryArtTiles.take(10) }
    val rowTwo = remember { categoryArtTiles.drop(10) }

    Column(
        Modifier
            .fillMaxSize()
            .background(TazColors.Cream)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ------------------------------------------------------ 1. Photo band
        Box(Modifier.fillMaxWidth().height(wallHeight)) {
            Column(
                Modifier.fillMaxWidth().padding(top = TazSpace.sm),
                verticalArrangement = Arrangement.spacedBy(TazSpace.sm)
            ) {
                MarqueeRow(speedPxPerSec = 24f) {
                    rowOne.forEach { cat ->
                        WallTile(cat)
                        Spacer(Modifier.width(TazSpace.sm))
                    }
                }
                // Short screens show a single band so the form stays above the fold.
                if (!compact) {
                    MarqueeRow(reverse = true, speedPxPerSec = 20f) {
                        rowTwo.forEach { cat ->
                            WallTile(cat)
                            Spacer(Modifier.width(TazSpace.sm))
                        }
                    }
                }
            }
            // Bottom fade so the band melts into the cream page.
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to TazColors.Cream
                    )
                )
            )
            // Top scrim: only over the status-bar strip, so system glyphs stay
            // legible without washing out the photography below them.
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(96.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to TazColors.Cream,
                            0.45f to TazColors.Cream.copy(alpha = 0.85f),
                            1f to Color.Transparent
                        )
                    )
            )
        }

        // Everything below is lifted 34dp so the badge overlaps the band —
        // one move that turns a stack into a composition.
        Column(
            Modifier.fillMaxWidth().offset(y = (-34).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // -------------------------------------------------- 2. Brand block
            Box(
                Modifier
                    .shadow(
                        10.dp, RoundedCornerShape(20.dp),
                        spotColor = Color.Black.copy(alpha = 0.22f)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(TazColors.Surface)
                    .padding(horizontal = TazSpace.xl, vertical = 14.dp)
            ) {
                LogoImage(height = 36.dp)
            }
            Spacer(Modifier.height(if (compact) TazSpace.sm else TazSpace.lg))
            Text(
                BrandCopy.tagline,
                fontSize = TazType.h1Size,
                fontWeight = TazType.h1Weight,
                lineHeight = TazType.h1Line,
                color = TazColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)
            )
            Spacer(Modifier.height(TazSpace.sm))
            Text(
                // Value + differentiator, both config-sourced (D4 / D6).
                DeliveryCopy.subtitle(AppConfig.deliveryPromise) + " · " + BrandCopy.voiceTeaser,
                fontSize = TazType.bodySize,
                lineHeight = TazType.bodyLine,
                color = TazColors.TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)
            )

            // -------------------------------------------------- 3. Value strip
            Spacer(Modifier.height(TazSpace.xl))
            ValueStrip()
            Spacer(Modifier.height(TazSpace.xl))

            Text(
                "LOG IN OR SIGN UP",
                fontSize = TazType.microSize,
                fontWeight = TazType.microWeight,
                letterSpacing = TazType.labelTracking,
                color = TazColors.TextTertiary
            )
            Spacer(Modifier.height(TazSpace.md))

            // -------------------------------------------------- 4. Phone / OTP
            Crossfade(
                targetState = step,
                modifier = Modifier.fillMaxWidth().padding(horizontal = TazSpace.xl)
            ) { currentStep ->
                if (currentStep == 1) {
                    Column(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { input -> phone = input.filter { it.isDigit() }.take(10) },
                            modifier = Modifier.fillMaxWidth().height(TazSize.inputHeight),
                            singleLine = true,
                            shape = TazRadius.card,
                            leadingIcon = { DialPrefix() },
                            placeholder = {
                                Text(
                                    "Enter mobile number",
                                    fontSize = TazType.bodySize,
                                    color = TazColors.TextTertiary
                                )
                            },
                            textStyle = TextStyle(
                                fontSize = TazType.titleSize,
                                fontWeight = TazType.titleWeight,
                                color = TazColors.TextPrimary
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            colors = tazFieldColors()
                        )
                        // Only rendered when the OTP request actually failed.
                        // Before this, a failed request silently did nothing and
                        // the button sat on "Sending OTP…" forever.
                        errorText?.let { message ->
                            Spacer(Modifier.height(TazSpace.sm))
                            Text(
                                message,
                                fontSize = TazType.captionSize,
                                fontWeight = FontWeight.SemiBold,
                                color = TazColors.Danger,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(TazSpace.md))
                        PillButton(
                            text = "Continue",
                            loading = sending,
                            loadingText = "Sending OTP…",
                            onClick = {
                                if (!sending) {
                                    scope.launch {
                                        sending = true
                                        errorText = null
                                        try {
                                            ServiceLocator.auth.requestOtp(phone)
                                            otp = ""
                                            step = 2
                                        } catch (cancellation: CancellationException) {
                                            throw cancellation
                                        } catch (t: Throwable) {
                                            // Do not advance to the OTP step for
                                            // a code that was never sent.
                                            errorText = t.toLoadError().message
                                        } finally {
                                            sending = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = phone.length == 10,
                            disabledHint = "Enter your 10-digit mobile number to continue"
                        )
                    }
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "OTP sent to +91 $phone",
                                fontSize = TazType.captionSize,
                                color = TazColors.TextSecondary
                            )
                            Spacer(Modifier.width(TazSpace.sm))
                            Text(
                                "Change",
                                fontSize = TazType.captionSize,
                                fontWeight = FontWeight.SemiBold,
                                color = TazColors.Green,
                                modifier = Modifier
                                    .clip(TazRadius.chip)
                                    .tazPressable(onClick = { step = 1
                                        otp = ""
                                        errorText = null }, pressScale = TazPress.compact)
                                    .padding(horizontal = TazSpace.xs, vertical = TazSpace.xxs)
                            )
                        }
                        Spacer(Modifier.height(TazSpace.md))
                        OutlinedTextField(
                            value = otp,
                            onValueChange = { input -> otp = input.filter { it.isDigit() }.take(4) },
                            modifier = Modifier.fillMaxWidth().height(TazSize.inputHeight),
                            singleLine = true,
                            shape = TazRadius.card,
                            placeholder = {
                                // Demo shortcut copy renders only in demo builds.
                                Text(
                                    if (AppConfig.demoMode) "4-digit OTP (demo: any)"
                                    else "Enter 4-digit OTP",
                                    fontSize = TazType.bodySize,
                                    color = TazColors.TextTertiary
                                )
                            },
                            textStyle = TextStyle(
                                fontSize = TazType.titleSize,
                                fontWeight = TazType.titleWeight,
                                color = TazColors.TextPrimary
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            colors = tazFieldColors()
                        )
                        errorText?.let { message ->
                            Spacer(Modifier.height(TazSpace.sm))
                            Text(
                                message,
                                fontSize = TazType.captionSize,
                                fontWeight = FontWeight.SemiBold,
                                color = TazColors.Danger,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(TazSpace.sm))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (resendIn > 0) {
                                Text(
                                    "Resend OTP in 0:" + resendIn.toString().padStart(2, '0'),
                                    fontSize = TazType.captionSize,
                                    color = TazColors.TextTertiary
                                )
                            } else {
                                Box(
                                    Modifier
                                        .defaultMinSize(minHeight = TazSize.touchTarget)
                                        .clip(TazRadius.chip)
                                        .tazPressable(
                                            pressScale = TazPress.compact,
                                            haptic = TazHaptic.Tap,
                                            onClick = {
                                            scope.launch {
                                                errorText = null
                                                try {
                                                    ServiceLocator.auth.requestOtp(phone)
                                                } catch (cancellation: CancellationException) {
                                                    throw cancellation
                                                } catch (t: Throwable) {
                                                    errorText = t.toLoadError().message
                                                }
                                            }
                                            resendIn = 30
                                            }
                                        )
                                        .padding(horizontal = TazSpace.md),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Resend OTP",
                                        fontSize = TazType.captionSize,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TazColors.Green
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(TazSpace.md))
                        PillButton(
                            text = "Verify & Start Shopping",
                            loading = verifying,
                            loadingText = "Verifying…",
                            onClick = {
                                if (!verifying) {
                                    scope.launch {
                                        verifying = true
                                        errorText = null
                                        try {
                                            val u = ServiceLocator.auth.verifyOtp(phone, otp)
                                            if (u != null) {
                                                app.user = u
                                                app.requestGuidedTourIfFirstTime()
                                                app.resetTo(Screen.Home)
                                            } else {
                                                errorText = "Invalid OTP, try again"
                                            }
                                        } catch (cancellation: CancellationException) {
                                            throw cancellation
                                        } catch (t: Throwable) {
                                            errorText = t.toLoadError().message
                                        } finally {
                                            verifying = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = otp.length == 4,
                            disabledHint = "Enter the 4-digit OTP to continue"
                        )
                    }
                }
            }

            // -------------------------------------------------- 5. Guest entry
            Spacer(Modifier.height(TazSpace.xs))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TazSpace.xl)
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(TazRadius.card)
                    .tazPressable(onClick = { app.requestGuidedTourIfFirstTime()
                        app.resetTo(Screen.Home) }, pressScale = TazPress.compact),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Skip for now",
                    fontSize = TazType.buttonSize,
                    fontWeight = FontWeight.SemiBold,
                    color = TazColors.Green
                )
            }

            // -------------------------------------------------- 6. Legal
            Spacer(Modifier.height(TazSpace.sm))
            // Plain caption, no link affordance — the underlined/bold links
            // return when the real Terms & Privacy legal pages ship.
            Text(
                "By continuing, you agree to our Terms of Service & Privacy Policy",
                fontSize = TazType.microSize,
                fontWeight = FontWeight.Normal,
                lineHeight = TazType.microLine,
                color = TazColors.TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = TazSpace.xxxl)
            )
            Spacer(Modifier.height(TazSpace.xxl))
            Box(Modifier.navigationBarsPadding())
        }
    }
}

/**
 * Three compact, config-backed reasons to sign up. Fills the composition
 * without crowding it; the coin item disappears entirely when loyalty is off.
 */
@Composable
private fun ValueStrip() {
    val items: List<Pair<ImageVector, String>> = buildList {
        add(TazIcons.Delivery to DeliveryCopy.headline(AppConfig.deliveryPromise))
        if (AppConfig.coins.enabled) add(TazIcons.Coin to "Tazzzo Coins")
        add(TazIcons.Mic to "Voice shopping")
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = TazSpace.xxl),
        horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)
    ) {
        items.forEach { (icon, label) ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(TazColors.GreenSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = TazColors.Green,
                        modifier = Modifier.size(TazSize.iconSm)
                    )
                }
                Spacer(Modifier.height(TazSpace.sm))
                Text(
                    label,
                    fontSize = TazType.navLabelSize,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 14.sp,
                    color = TazColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}

/** "+91" rendered as part of the input, separated by a hairline rule. */
@Composable
internal fun DialPrefix() {
    Row(
        Modifier.padding(start = TazSpace.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "+91",
            fontSize = TazType.titleSize,
            fontWeight = TazType.titleWeight,
            color = TazColors.TextPrimary
        )
        Spacer(Modifier.width(TazSpace.md))
        Box(Modifier.width(1.dp).height(22.dp).background(TazColors.BorderStrong))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun tazFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TazColors.Green,
    unfocusedBorderColor = TazColors.BorderStrong,
    cursorColor = TazColors.Green,
    focusedContainerColor = TazColors.Surface,
    unfocusedContainerColor = TazColors.Surface,
    focusedTextColor = TazColors.TextPrimary,
    unfocusedTextColor = TazColors.TextPrimary
)

/** One photographic tile in the entry photo band. */
@Composable
private fun WallTile(tile: CategoryArtTile) {
    Box(
        Modifier
            .size(tileSize)
            .clip(TazRadius.tile)
            .background(Color(tile.tint))
    ) {
        Image(
            painter = painterResource(tile.art),
            contentDescription = tile.label,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Subtle inner bottom scrim for depth.
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0.7f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.08f)
                )
            )
        )
    }
}
