package com.tazzzo.app

import androidx.compose.ui.graphics.Color
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.ui.common.pillLabelColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * A filled button must never render its label in its own fill colour.
 *
 * This shipped once: the Deals campaign band asked for a white button on a dark
 * band and got white text on white. Every semantic assertion passed, because
 * the label node existed and read correctly. Only a screenshot caught it.
 */
class PillContrastTest {

    @Test fun a_light_fill_takes_dark_ink() {
        assertEquals(TazColors.TextPrimary, pillLabelColor(TazColors.White))
        assertEquals(TazColors.TextPrimary, pillLabelColor(Color(0xFFFFF3E0)))
    }

    @Test fun a_dark_fill_takes_light_ink() {
        assertEquals(TazColors.White, pillLabelColor(TazColors.Green))
        assertEquals(TazColors.White, pillLabelColor(Color(0xFF000000)))
    }

    @Test fun the_label_is_never_the_fill_itself() {
        listOf(TazColors.White, TazColors.Green, TazColors.Orange, TazColors.Cream,
               Color(0xFF808080), Color(0xFFFFFFFF), Color(0xFF00411C)).forEach { fill ->
            assertNotEquals(fill, pillLabelColor(fill), "label vanished on fill $fill")
        }
    }
}
