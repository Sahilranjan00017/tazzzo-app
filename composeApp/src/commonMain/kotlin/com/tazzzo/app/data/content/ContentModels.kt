package com.tazzzo.app.data.content

import com.tazzzo.app.data.catalog.safeImageUrl
import kotlinx.serialization.Serializable

/*
 * `GET /v1/content/home`: the CMS-published Home blocks for the APP channel, in the backend's display order. The app
 * renders exactly what the backend publishes and nothing it does not: a banner without an https image is not shown, a
 * link outside the closed grammar makes the banner untappable, an unknown block type is skipped, and ids are capped so a
 * CMS mistake cannot fan out into hundreds of product requests.
 */

/** Wire shape of the running backend (`content/PublicContentController`). Unknown fields are ignored by the shared Json. */
@Serializable internal data class HomeContentDto(val blocks: List<HomeBlockDto> = emptyList(), val requestId: String? = null)

@Serializable internal data class HomeBlockDto(
    val blockId: String,
    val type: String,
    val title: String = "",
    val imageUrl: String? = null,
    val link: String? = null,
    val ids: List<String> = emptyList()
)

/** Where a banner leads. The grammar is closed on the backend too; anything else is "nowhere" (the banner is not tappable). */
sealed interface ContentLink {
    data class Product(val productId: String) : ContentLink
    data class Category(val nodeId: String) : ContentLink
    data class Search(val query: String) : ContentLink

    companion object {
        private val PRODUCT_ID = Regex("^TZP-[0-9]+$")
        private val NODE_ID = Regex("^TZ[SCGV]-[0-9]{6}$")
        const val MAX_SEARCH = 100

        /** `product:<id>` · `category:<node id>` · `search:<text>`; null for anything else (including an id of the wrong shape). */
        fun parse(raw: String?): ContentLink? {
            val value = raw?.trim() ?: return null
            val colon = value.indexOf(':').takeIf { it > 0 } ?: return null
            val target = value.substring(colon + 1).trim()
            return when (value.substring(0, colon).lowercase()) {
                "product" -> target.takeIf { PRODUCT_ID.matches(it) }?.let { Product(it) }
                "category" -> target.takeIf { NODE_ID.matches(it) }?.let { Category(it) }
                "search" -> target.takeIf { it.isNotEmpty() && it.length <= MAX_SEARCH && it.none { c -> c < ' ' } }?.let { Search(it) }
                else -> null
            }
        }
    }
}

sealed interface HomeBlock {
    val blockId: String
    val title: String

    /** Always has an https image (a banner with nothing to show is dropped at mapping time). */
    data class Banner(override val blockId: String, override val title: String, val imageUrl: String, val link: ContentLink?) : HomeBlock

    /** Product ids the client renders with `GET /v1/products/{id}`; unique, at most [MAX_RAIL_IDS]. */
    data class ProductRail(override val blockId: String, override val title: String, val productIds: List<String>) : HomeBlock

    /** Taxonomy node ids the client renders with the loaded taxonomy; unique, at most [MAX_GRID_IDS]. */
    data class CategoryGrid(override val blockId: String, override val title: String, val nodeIds: List<String>) : HomeBlock

    companion object {
        const val MAX_RAIL_IDS = 12
        const val MAX_GRID_IDS = 12
        const val MAX_BLOCKS = 20
    }
}

/** The published Home, in display order. Empty is a valid, truthful answer (nothing published for the app right now). */
data class HomeContent(val blocks: List<HomeBlock>) {
    companion object {
        val EMPTY = HomeContent(emptyList())
    }
}

internal fun HomeContentDto.toDomain(): HomeContent {
    val out = ArrayList<HomeBlock>()
    val seen = HashSet<String>()
    for (b in blocks) {
        if (out.size >= HomeBlock.MAX_BLOCKS) break
        if (b.blockId.isBlank() || !seen.add(b.blockId)) continue
        val title = b.title.trim()
        val block: HomeBlock? = when (b.type) {
            "BANNER" -> safeImageUrl(b.imageUrl)?.let { HomeBlock.Banner(b.blockId, title, it, ContentLink.parse(b.link)) }
            "PRODUCT_RAIL" -> ids(b.ids, Regex("^TZP-[0-9]+$"), HomeBlock.MAX_RAIL_IDS).takeIf { it.isNotEmpty() }
                ?.let { HomeBlock.ProductRail(b.blockId, title, it) }
            "CATEGORY_GRID" -> ids(b.ids, Regex("^TZ[SCGV]-[0-9]{6}$"), HomeBlock.MAX_GRID_IDS).takeIf { it.isNotEmpty() }
                ?.let { HomeBlock.CategoryGrid(b.blockId, title, it) }
            else -> null
        }
        if (block != null) out += block
    }
    return HomeContent(out)
}

private fun ids(raw: List<String>, shape: Regex, max: Int): List<String> =
    raw.asSequence().map { it.trim() }.filter { shape.matches(it) }.distinct().take(max).toList()
