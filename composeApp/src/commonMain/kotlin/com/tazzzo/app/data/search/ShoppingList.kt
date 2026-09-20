package com.tazzzo.app.data.search

/**
 * Splits a typed or spoken shopping list into the individual things asked for.
 *
 * "aata, doodh, 2 maggi" becomes ["aata", "doodh", "maggi"], each of which is
 * then searched separately so the customer picks the exact pack they want.
 * Resolving each term to a single product automatically is precisely what this
 * must not do: "oil" matches four oils, and silently choosing one puts a
 * product in someone's basket that they did not select.
 *
 * Handles the separators people actually use — commas, "and", newlines — and
 * strips a leading quantity, because "2 maggi" is a request for Maggi, not for
 * a product called "2 maggi".
 */
object ShoppingList {

    private val separators = Regex("""[,\n;]+|\band\b|\baur\b""", RegexOption.IGNORE_CASE)
    private val leadingQuantity = Regex("""^\s*\d+\s*(x|kg|g|l|ml|pcs?|packets?|packs?)?\s+""", RegexOption.IGNORE_CASE)

    /** Distinct, cleaned terms, in the order they were said. Max 12: a longer
     *  list is almost always a paste accident, and 12 rows is already a screen. */
    fun parse(raw: String): List<String> =
        raw.split(separators)
            .map { it.trim().replace(leadingQuantity, "").trim().trim('.', '-', '·') }
            .filter { it.length >= 2 }
            .distinctBy { it.lowercase() }
            .take(12)
}
