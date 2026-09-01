package com.tazzzo.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.config.BrandCopy
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.CartBar
import com.tazzzo.app.ui.common.LogoImage
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazTopBar

@Composable
fun AboutScreen() {
    val app = LocalAppState.current
    // The savings line is CONFIG-sourced (D6). If the claim is withdrawn the
    // screen falls back to a promise we can always keep.
    val pricesLine = BrandCopy.savingsClaim
        ?.let { "$it ${BrandCopy.savingsClaimSub}" }
        ?: "Better prices, every day"

    Box(Modifier.fillMaxSize().background(TazColors.Cream)) {
        Column(Modifier.fillMaxSize()) {
            TazTopBar("About Tazzzo", onBack = { app.back() })
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = TazSpace.gutter)
                    .padding(top = TazSpace.xxxl, bottom = TazSpace.cartBarClearance),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LogoImage(56.dp)
                Spacer(Modifier.height(TazSpace.md))
                Text(
                    BrandCopy.promise, fontSize = TazType.bodySize,
                    lineHeight = TazType.bodyLine, color = TazColors.TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(TazSpace.xxl))
                Column(
                    Modifier.fillMaxWidth().clip(TazRadius.card)
                        .background(TazColors.Surface)
                        .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
                        .padding(vertical = TazSpace.sm)
                ) {
                    AboutValueRow(TazIcons.Store, "Quality you can trust")
                    AboutValueRow(TazIcons.Offer, pricesLine)
                    AboutValueRow(TazIcons.Delivery, "Delivered fresh to your doorstep")
                }

                Spacer(Modifier.height(TazSpace.lg))
                Column(
                    Modifier.fillMaxWidth().clip(TazRadius.tile)
                        .background(TazColors.GreenDark).padding(TazSpace.xl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TazIcon(
                        TazIcons.Mic, null, size = TazSize.iconMd, tint = TazColors.White
                    )
                    Spacer(Modifier.height(TazSpace.sm))
                    Text(
                        BrandCopy.voiceTeaser, fontSize = TazType.titleSize,
                        fontWeight = TazType.titleWeight, lineHeight = TazType.titleLine,
                        color = TazColors.White, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(TazSpace.xs))
                    Text(
                        BrandCopy.voiceStatus, fontSize = TazType.captionSize,
                        lineHeight = TazType.captionLine,
                        color = TazColors.White.copy(alpha = 0.7f)
                    )
                }

                Spacer(Modifier.height(TazSpace.xl))
                Text(
                    "Order on WhatsApp: say HI to ${BrandCopy.whatsappNumber}",
                    fontSize = TazType.captionSize, lineHeight = TazType.captionLine,
                    color = TazColors.TextSecondary, textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(TazSpace.lg))
                Text(
                    "Tazzzo v1.0", fontSize = TazType.microSize,
                    lineHeight = TazType.microLine, color = TazColors.TextTertiary
                )
            }
        }
        CartBar()
    }
}

@Composable
private fun AboutValueRow(icon: ImageVector, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = TazSpace.lg, vertical = TazSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(TazColors.GreenSoft),
            contentAlignment = Alignment.Center
        ) {
            TazIcon(icon, null, size = TazSize.iconSm, tint = TazColors.Green)
        }
        Spacer(Modifier.width(TazSpace.md))
        Text(
            text, fontSize = TazType.bodySize, lineHeight = TazType.bodyLine,
            fontWeight = FontWeight.Medium, color = TazColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}
