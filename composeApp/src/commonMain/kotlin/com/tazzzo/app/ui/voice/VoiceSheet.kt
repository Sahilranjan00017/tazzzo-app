package com.tazzzo.app.ui.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tazzzo.app.config.BrandCopy
import com.tazzzo.app.theme.MotionSettings
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazMotion
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.TazIcon

/**
 * "Voice commerce coming soon" bottom sheet, opened from the mic button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceComingSoonSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = TazColors.Surface,
        shape = TazRadius.sheet,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(TazSpace.xxl)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Pulsing mic — ambient motion, the one place it earns its keep.
            // Still gated: under reduce-motion or test it rests at 1f.
            val micScale: Float = if (MotionSettings.ambientEnabled) {
                rememberInfiniteTransition().animateFloat(
                    initialValue = 1f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(TazMotion.ambient),
                        repeatMode = RepeatMode.Reverse
                    )
                ).value
            } else 1f
            Box(
                Modifier
                    .size(96.dp)
                    .scale(micScale)
                    .clip(CircleShape)
                    .background(TazColors.GreenSoft)
                    .border(BorderStroke(2.dp, TazColors.Green), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                TazIcon(TazIcons.Mic, null, size = 44.dp, tint = TazColors.Green)
            }

            Spacer(Modifier.height(TazSpace.lg))

            // Title/status/sub all come from BrandCopy — never hard-coded here.
            Text(
                BrandCopy.voiceTeaser,
                fontSize = TazType.h2Size,
                fontWeight = TazType.h2Weight,
                lineHeight = TazType.h2Line,
                color = TazColors.Green,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(TazSpace.md))

            Box(
                Modifier
                    .clip(TazRadius.pill)
                    .background(TazColors.GreenSoft)
                    .padding(horizontal = TazSpace.md, vertical = TazSpace.xs)
            ) {
                Text(
                    BrandCopy.voiceStatus.uppercase(),
                    fontSize = TazType.microSize,
                    lineHeight = TazType.microLine,
                    fontWeight = FontWeight.Bold,
                    color = TazColors.BrandEditorial
                )
            }

            Spacer(Modifier.height(TazSpace.md))

            Text(
                BrandCopy.voiceSub + " — \"2 litre doodh, 5kg atta, Maggi\" — " +
                    "and Tazzzo builds your cart. We are working on it.",
                fontSize = TazType.bodySize,
                lineHeight = TazType.bodyLine,
                color = TazColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(TazSpace.lg))

            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(TazSpace.sm)
            ) {
                ValueRow(TazIcons.Mic, "Order in Hindi, English & more")
                ValueRow(TazIcons.Delivery, "Hands-free shopping")
                ValueRow(TazIcons.Store, "Learns your monthly list")
            }

            Spacer(Modifier.height(TazSpace.xl))

            // "Notify me" returns when a real waitlist endpoint exists — until
            // then we make no promise we cannot keep.
            PillButton(
                text = "Got it",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ValueRow(icon: ImageVector, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(TazRadius.card)
            .background(TazColors.SurfaceSunken)
            .padding(horizontal = TazSpace.md, vertical = TazSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TazIcon(icon, null, size = TazSize.iconSm, tint = TazColors.Green)
        Spacer(Modifier.width(TazSpace.md))
        Text(
            text,
            fontSize = TazType.bodySize,
            lineHeight = TazType.bodyLine,
            fontWeight = FontWeight.Medium,
            color = TazColors.TextPrimary
        )
    }
}
