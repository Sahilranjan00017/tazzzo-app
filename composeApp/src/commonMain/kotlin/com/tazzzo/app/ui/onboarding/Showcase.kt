package com.tazzzo.app.ui.onboarding

import com.tazzzo.app.ui.common.EditorialRun
import com.tazzzo.app.ui.common.italic
import com.tazzzo.app.ui.common.plain
import org.jetbrains.compose.resources.DrawableResource
import tazzzo.resources.Res
import tazzzo.resources.bg_showcase_farm
import tazzzo.resources.bg_showcase_kitchen
import tazzzo.resources.bg_showcase_wholesale

/**
 * The three-page brand carousel shown after Splash on a device that has not onboarded. Pure data + state; the
 * composable only renders it. Copy is the approved reference copy verbatim (Product decision, 2026-10-02) — no
 * stronger claim is added around it.
 */
data class ShowcasePage(
    /** Headline runs: the last run is the italic emphasis. */
    val headline: List<EditorialRun>,
    val support: String,
    /** The photo slot id in docs/design/UI_ASSET_MANIFEST.md. */
    val assetId: String,
    /** The production plate: the reference scene with its baked UI removed (see docs/design/UI_ASSET_MANIFEST.md). */
    val photo: DrawableResource
)

object ShowcaseCopy {
    const val NEXT = "Next"
    const val GET_STARTED = "Get started"
    const val SKIP = "Skip"

    val pages: List<ShowcasePage> = listOf(
        ShowcasePage(listOf(plain("Wholesale\nPrices,\n"), italic("Delivered.")), "Everyday essentials at prices\nthat make more sense.", "TZ-ASSET-SHOWCASE-001", Res.drawable.bg_showcase_wholesale),
        ShowcasePage(listOf(plain("Farm Fresh,\n"), italic("Everyday.")), "Handpicked produce.\nFreshness you can trust.", "TZ-ASSET-SHOWCASE-002", Res.drawable.bg_showcase_farm),
        ShowcasePage(listOf(plain("Kitchen\nEssentials,\n"), italic("Simplified.")), "Top brands. Great prices.\nAll in one place.", "TZ-ASSET-SHOWCASE-003", Res.drawable.bg_showcase_kitchen)
    )
}

/** What the primary CTA does on a given page. */
sealed interface ShowcaseAction {
    data class GoToPage(val index: Int) : ShowcaseAction
    /** Leave the carousel for Login. Onboarding is NOT marked complete here: that still happens on login / explicit skip-for-now. */
    data object Finish : ShowcaseAction
}

/** Pure carousel rules, testable without Compose. */
object ShowcaseRules {
    val pageCount: Int get() = ShowcaseCopy.pages.size

    fun isLast(index: Int): Boolean = index >= pageCount - 1

    fun primaryLabel(index: Int): String = if (isLast(index)) ShowcaseCopy.GET_STARTED else ShowcaseCopy.NEXT

    /** "Next" advances one page; on the last page the CTA ("Get started") finishes. */
    fun onPrimary(index: Int): ShowcaseAction = if (isLast(index)) ShowcaseAction.Finish else ShowcaseAction.GoToPage(index + 1)

    /** "Skip" always finishes, from any page. */
    fun onSkip(): ShowcaseAction = ShowcaseAction.Finish
}
