package com.tazzzo.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.ButtonTone
import com.tazzzo.app.ui.common.EditorialText
import com.tazzzo.app.ui.common.PagerDots
import com.tazzzo.app.ui.common.PhotoAnchor
import com.tazzzo.app.ui.common.PhotoBackdrop
import com.tazzzo.app.ui.common.PhotoPlaceholders
import com.tazzzo.app.ui.common.TazzzoPrimaryButton
import com.tazzzo.app.ui.common.TextAction
import com.tazzzo.app.ui.common.VSpace
import com.tazzzo.app.ui.common.plain
import kotlinx.coroutines.launch

/**
 * Showcase 1 → 2 → 3 → Login. Each page is its own full-bleed photo slot (anchored to the bottom, so the subject
 * stays grounded on tall screens) with the headline block in the upper, plain-wall region. Swipe or "Next"
 * advances; "Skip" and the last page's "Get started" go to Login. Nothing here marks the device onboarded.
 */
@Composable
fun ShowcaseScreen() {
    val app = LocalAppState.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { ShowcaseRules.pageCount }

    fun run(action: ShowcaseAction) = when (action) {
        is ShowcaseAction.GoToPage -> { scope.launch { pager.animateScrollToPage(action.index) }; Unit }
        ShowcaseAction.Finish -> app.resetTo(Screen.Login)
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page -> ShowcasePageContent(ShowcaseCopy.pages[page]) }

        // Pagination + actions float over every page so they never jump during a swipe.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .padding(start = TazSpace.xxl, end = TazSpace.xxl, bottom = TazSpace.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TazzzoPrimaryButton(
                text = ShowcaseRules.primaryLabel(pager.currentPage),
                onClick = { run(ShowcaseRules.onPrimary(pager.currentPage)) },
                tone = ButtonTone.Cream, italic = true, modifier = Modifier.fillMaxWidth()
            )
            VSpace(TazSpace.xs)
            TextAction(ShowcaseCopy.SKIP, onClick = { run(ShowcaseRules.onSkip()) }, serif = true, color = TazColors.CreamStrong)
        }
    }
}

@Composable
private fun ShowcasePageContent(page: ShowcasePage) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Reference proportions (640dp frame): headline block starts at ~25% of the height; the dots follow the support line.
        val headlineTop = maxHeight * HEADLINE_TOP_FRACTION
        PhotoBackdrop(
            photo = null,                                            // TZ-ASSET-SHOWCASE-00x outstanding
            placeholder = PhotoPlaceholders.greenWall,
            anchor = PhotoAnchor.Bottom,
            modifier = Modifier.fillMaxSize(),
            // Legibility scrims: a touch at the very top for status glyphs, a deeper one behind the CTA.
            scrim = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.10f), 0.2f to Color.Transparent,
                0.78f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.22f)
            )
        )
        Column(
            Modifier.fillMaxWidth().padding(top = headlineTop, start = TazSpace.xxl, end = TazSpace.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            EditorialText(page.headline, size = TazType.editorialDisplaySize, lineHeight = TazType.editorialDisplayLine, color = TazColors.EditorialOnDark)
            VSpace(TazSpace.lg)
            EditorialText(listOf(plain(page.support)), size = TazType.editorialBodySize, lineHeight = TazType.editorialBodyLine, color = TazColors.EditorialOnDark.copy(alpha = 0.92f))
            VSpace(TazSpace.xxl)
            PagerDots(count = ShowcaseRules.pageCount, index = ShowcaseCopy.pages.indexOf(page))
        }
    }
}

private const val HEADLINE_TOP_FRACTION = 0.25f
