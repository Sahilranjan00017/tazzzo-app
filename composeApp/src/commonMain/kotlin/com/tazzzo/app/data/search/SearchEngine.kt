package com.tazzzo.app.data.search

import com.tazzzo.app.data.model.Category
import com.tazzzo.app.data.model.Product

/**
 * Grocery search over an in-memory catalogue.
 *
 * Design constraints:
 *  - The repository interface stays `suspend fun search(query): List<Product>`,
 *    so this whole class can be replaced by a call to the backend search
 *    service (GET /catalog/v1/search?q=) without any UI change.
 *  - Customers type fast, in two languages, with typos. `aata` must find atta,
 *    `doodh` must find milk, `mag` must find Maggi.
 *
 * Matching layers, strongest first:
 *  1. exact token match on name/brand
 *  2. synonym match (Hindi→English grocery vocabulary) — ranked ABOVE prefix:
 *     "magi" means Maggi, not "Magic Masala"
 *  3. prefix match (query token is a prefix of an indexed token, min 3 chars)
 *  4. fuzzy match (edit distance 1 for 4–5 letter tokens, 2 for longer)
 *  5. category/subcategory name match
 */
class SearchEngine(products: List<Product>, categories: List<Category>) {

    /** Common Hindi/colloquial grocery terms mapped to catalogue vocabulary. */
    private val synonyms: Map<String, List<String>> = mapOf(
        "aata" to listOf("atta"), "ata" to listOf("atta"),
        "doodh" to listOf("milk"), "dudh" to listOf("milk"),
        "chawal" to listOf("rice"), "chaval" to listOf("rice"),
        "namak" to listOf("salt"),
        "tel" to listOf("oil"),
        "sabun" to listOf("soap"),
        "anda" to listOf("egg", "eggs"), "ande" to listOf("egg", "eggs"),
        "murgi" to listOf("chicken"), "murga" to listOf("chicken"),
        "machli" to listOf("fish", "rohu"), "machhli" to listOf("fish", "rohu"),
        "pyaz" to listOf("onion"), "pyaaz" to listOf("onion"), "kanda" to listOf("onion"),
        "aloo" to listOf("potato", "bhujia"), "alu" to listOf("potato"),
        "tamatar" to listOf("tomato"),
        "kela" to listOf("banana"), "kele" to listOf("banana"),
        "seb" to listOf("apple"),
        "dahi" to listOf("curd", "yogurt", "paneer"),
        "ghee" to listOf("ghee"),
        "chini" to listOf("sugar"), "shakkar" to listOf("sugar"),
        "chai" to listOf("tea"), "chay" to listOf("tea"),
        "kapda" to listOf("laundry", "detergent"),
        "biscuit" to listOf("biscuits", "cookies", "cookie"),
        "chips" to listOf("chips", "crisps"),
        "kurkure" to listOf("kurkure", "munch"),
        "noodles" to listOf("noodles", "maggi"),
        "maggie" to listOf("maggi"), "magi" to listOf("maggi"),
        "colddrink" to listOf("cola", "thums", "drink"),
        "paani" to listOf("water"), "pani" to listOf("water"),
        "toothpaste" to listOf("toothpaste", "colgate"),
        "shampu" to listOf("shampoo"),
        "surf" to listOf("detergent", "matic", "surf")
    )

    private data class Entry(val product: Product, val tokens: Set<String>, val weakTokens: Set<String>)

    private val entries: List<Entry>

    init {
        val categoryNames = categories.associate { c ->
            c.id to (tokenize(c.name) + c.subcategories.flatMap { tokenize(it.name) })
        }
        entries = products.map { p ->
            Entry(
                product = p,
                tokens = (tokenize(p.name) + tokenize(p.brand)).toSet(),
                weakTokens = (categoryNames[p.categoryId].orEmpty() + tokenize(p.unit)).toSet()
            )
        }
    }

    private val unitLike = Regex("^\\d+(kg|g|gm|l|ltr|ml|pc|pcs|pack)?$|^(kg|g|gm|l|ltr|ml|pc|pcs|pack)$")

    fun search(rawQuery: String): List<Product> {
        val allTokens = tokenize(rawQuery)
        // "atta 5kg" — the quantity is a qualifier, not a requirement; requiring
        // it to match would empty the results.
        val queryTokens = allTokens.filterNot { unitLike.matches(it) }.ifEmpty { allTokens }
        if (queryTokens.isEmpty()) return emptyList()

        return entries.mapNotNull { entry ->
            var total = 0
            for (q in queryTokens) {
                val s = scoreToken(q, entry)
                if (s == 0) return@mapNotNull null   // every query word must match something
                total += s
            }
            entry.product to total
        }
            .sortedWith(
                compareByDescending<Pair<Product, Int>> { it.second }
                    .thenByDescending { "Bestseller" in it.first.tags }
                    .thenBy { it.first.name }
            )
            .map { it.first }
    }

    private fun scoreToken(q: String, entry: Entry): Int {
        // 1. exact
        if (q in entry.tokens) return 100
        // 2. synonym — checked before prefix: a customer typing "magi" means
        // Maggi (synonym), not "Magic ..." (prefix).
        synonyms[q]?.let { expansions ->
            if (expansions.any { syn -> syn in entry.tokens || entry.tokens.any { it.startsWith(syn) } }) return 65
        }
        // 3. prefix
        if (q.length >= 3 && entry.tokens.any { it.startsWith(q) }) return 60
        // 4. fuzzy
        val maxEdits = when {
            q.length >= 6 -> 2
            q.length >= 4 -> 1
            else -> 0
        }
        // First-letter anchor: for short words a distance-1 match with a
        // different initial ("aata" → "tata") is almost always a different
        // product; customers rarely mistype the first letter.
        if (maxEdits > 0 && entry.tokens.any { token ->
                (q.length >= 6 || token.firstOrNull() == q.firstOrNull()) &&
                    editDistanceAtMost(q, token, maxEdits)
            }
        ) return 40
        // 5. category / unit
        if (q in entry.weakTokens) return 25
        if (q.length >= 3 && entry.weakTokens.any { it.startsWith(q) }) return 15
        return 0
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.length >= 2 }

    /**
     * Bounded Levenshtein — true if edit distance ≤ [max]. Early-exits rows
     * whose minimum already exceeds the bound, so cost stays trivial for a
     * catalogue this size and acceptable for a few thousand SKUs.
     */
    private fun editDistanceAtMost(a: String, b: String, max: Int): Boolean {
        if (kotlin.math.abs(a.length - b.length) > max) return false
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > max) return false
            prev = cur
        }
        return prev[b.length] <= max
    }
}
