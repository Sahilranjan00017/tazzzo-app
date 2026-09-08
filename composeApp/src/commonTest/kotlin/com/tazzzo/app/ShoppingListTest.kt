package com.tazzzo.app

import com.tazzzo.app.data.search.ShoppingList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How people actually write a shopping list, versus what a naive split expects.
 */
class ShoppingListTest {

    @Test fun commas_are_the_common_case() {
        assertEquals(listOf("aata", "doodh", "maggi"), ShoppingList.parse("aata, doodh, maggi"))
    }

    @Test fun spoken_lists_use_and_not_commas() {
        assertEquals(listOf("atta", "oil", "maggi"), ShoppingList.parse("atta and oil and maggi"))
        assertEquals(listOf("atta", "doodh"), ShoppingList.parse("atta aur doodh"))
    }

    @Test fun a_leading_quantity_is_not_part_of_the_product_name() {
        // "2 maggi" is a request for Maggi. Searching for "2 maggi" finds nothing.
        assertEquals(listOf("maggi", "atta", "oil"), ShoppingList.parse("4 maggi, 5 kg atta, 1 l oil"))
    }

    @Test fun repeats_and_stray_punctuation_are_cleaned() {
        assertEquals(listOf("milk", "atta"), ShoppingList.parse("milk, MILK, atta."))
    }

    @Test fun noise_is_dropped_rather_than_searched() {
        assertTrue(ShoppingList.parse("").isEmpty())
        assertTrue(ShoppingList.parse("  , , ").isEmpty())
        assertEquals(listOf("milk"), ShoppingList.parse("a, milk, x"))
    }

    @Test fun a_runaway_paste_is_capped() {
        val huge = (1..40).joinToString(",") { "item$it" }
        assertEquals(12, ShoppingList.parse(huge).size)
    }
}
