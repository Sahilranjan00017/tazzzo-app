package com.tazzzo.app.brand

import com.tazzzo.app.config.BrandCopy
import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.ui.common.WORDMARK_ASPECT
import com.tazzzo.app.ui.common.editorialString
import com.tazzzo.app.ui.common.italic
import com.tazzzo.app.ui.common.plain
import com.tazzzo.app.ui.onboarding.LoginCopy
import com.tazzzo.app.ui.onboarding.ShowcaseAction
import com.tazzzo.app.ui.onboarding.ShowcaseCopy
import com.tazzzo.app.ui.onboarding.ShowcaseRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** UI-01 brand foundation: the carousel rules, the approved copy, and the things this pass must NOT change. */
class BrandFoundationTest {

    // ---- Showcase: three pages, Next / Skip / Get started ------------------------------------------------------------

    @Test fun thereAreExactlyThreeShowcasePagesWithTheApprovedCopyInReferenceOrder() {
        assertEquals(3, ShowcaseRules.pageCount)
        val heads = ShowcaseCopy.pages.map { editorialString(it.headline).text.replace("\n", " ") }
        assertEquals(listOf("Wholesale Prices, Delivered.", "Farm Fresh, Everyday.", "Kitchen Essentials, Simplified."), heads)
        assertEquals(
            listOf("Everyday essentials at prices that make more sense.", "Handpicked produce. Freshness you can trust.", "Top brands. Great prices. All in one place."),
            ShowcaseCopy.pages.map { it.support.replace("\n", " ") }
        )
    }

    @Test fun onlyTheLastWordOfEachHeadlineIsItalic() {
        for (p in ShowcaseCopy.pages) {
            assertTrue(p.headline.last().italic, p.headline.toString())
            assertTrue(p.headline.dropLast(1).none { it.italic }, p.headline.toString())
        }
    }

    @Test fun nextAdvancesOnePageAndTheLastPageCtaIsGetStartedWhichFinishes() {
        assertEquals(ShowcaseAction.GoToPage(1), ShowcaseRules.onPrimary(0))
        assertEquals(ShowcaseAction.GoToPage(2), ShowcaseRules.onPrimary(1))
        assertEquals(ShowcaseAction.Finish, ShowcaseRules.onPrimary(2))
        assertEquals(listOf("Next", "Next", "Get started"), (0..2).map { ShowcaseRules.primaryLabel(it) })
    }

    @Test fun skipFinishesFromEveryPage() {
        repeat(3) { assertEquals(ShowcaseAction.Finish, ShowcaseRules.onSkip()) }
        assertEquals("Skip", ShowcaseCopy.SKIP)
    }

    @Test fun eachPageHasItsOwnPhotoSlot() {
        assertEquals(3, ShowcaseCopy.pages.map { it.assetId }.toSet().size)
        assertTrue(ShowcaseCopy.pages.all { it.assetId.startsWith("TZ-ASSET-SHOWCASE-") })
    }

    @Test fun showcaseCopyAddsNoUnsupportedClaims() {
        val all = ShowcaseCopy.pages.flatMap { listOf(editorialString(it.headline).text, it.support) }.joinToString(" ").lowercase()
        for (w in listOf("our farm", "direct from", "organic", "minutes", "guaranteed", "%", "free delivery", "cheapest")) assertFalse(w in all, w)
    }

    // ---- brand copy ------------------------------------------------------------------------------------------------------

    @Test fun theCanonicalTaglineIsBestValueSmartShopping() {
        assertEquals("Best Value. Smart Shopping.", BrandCopy.tagline)
    }

    @Test fun editorialStringKeepsTheTextAndMarksOnlyItalicRuns() {
        val s = editorialString(listOf(plain("What’s\nyour "), italic("number?")))
        assertEquals("What’s\nyour number?", s.text)
        assertEquals(1, s.spanStyles.size)
        assertEquals("number?", s.text.substring(s.spanStyles.single().start, s.spanStyles.single().end))
    }

    // ---- login ---------------------------------------------------------------------------------------------------------

    @Test fun theDialPrefixIsStaticPlusNinetyOneAndGuestEntryStaysAsAQuietSkip() {
        assertEquals("+91", LoginCopy.DIAL_PREFIX)
        assertEquals("Skip for now", LoginCopy.SKIP)
        assertTrue("6-digit" in LoginCopy.PHONE_SUPPORT)
    }

    // ---- what this UI pass must not touch -------------------------------------------------------------------------------

    @Test fun productionOrderingStaysOff() {
        assertFalse(CatalogCapabilities.REMOTE.orderIntegration)
        assertFalse(CatalogCapabilities.REMOTE.orderHistoryIntegration)
    }

    @Test fun theWordmarkAspectMatchesTheMasterSvg() {
        assertEquals(316f / 70.4f, WORDMARK_ASPECT)
    }
}
