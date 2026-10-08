package com.tazzzo.app.data.content

import com.tazzzo.app.data.catalog.PRODUCT_ID
import com.tazzzo.app.data.catalog.safeImageUrl
import kotlinx.serialization.Serializable

/*
 * `GET /v1/content/home`: the CMS-published Home blocks for the APP channel, in the backend's display order. The app
 * renders exactly what the backend publishes and nothing it does not: a banner without an https image is not shown, a
 * link outside the closed grammar makes the banner untappable, an unknown block type is skipped, and ids are capped so a
 * CMS mistake cannot fan out into hundreds of product requests. A banner's optional `desktopImageUrl` is for the desktop
 * website only: the app ignores it (the shared Json drops unknown fields) and always renders `imageUrl`.
 */

/** Wire shape of the running backend (`content/PublicContentController`). Unknown fields are ignored by the shared Json. */
@Serializable internal data class HomeContentDto(val blocks: List<HomeBlockDto> = emptyList(), val requestId: String? = null)

@Serializable internal data class HomeBlockDto(
    val blockId: String? = null,
    val type: String? = null,
    val title: String? = null,
    /** BANNER only, optional (backend ≤ 120 chars). */
    val subtitle: String? = null,
    /** BANNER only; the backend always sends it (the editor's alt text, else the title). Absent from an older backend. */
    val altText: String? = null,
    val imageUrl: String? = null,
    val link: String? = null,
    val ids: List<String>? = null
)

/** Where a banner leads. The grammar is closed on the backend too; anything else is "nowhere" (the banner is not tappable). */
sealed interface ContentLink {
    data class Product(val productId: String) : ContentLink
    data class Category(val nodeId: String) : ContentLink
    data class Search(val query: String) : ContentLink

    companion object {
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

    /**
     * Always has an https image (a banner with nothing to show is dropped at mapping time). [subtitle] is null when not
     * published; [altText] describes the image for a screen reader and is never blank when [title] is not (it falls back
     * to the title, as the backend itself does).
     */
    data class Banner(
        override val blockId: String, override val title: String, val imageUrl: String, val link: ContentLink?,
        val subtitle: String? = null, val altText: String = title
    ) : HomeBlock

    /** Product ids the client renders with `GET /v1/products/{id}`; unique, at most [MAX_RAIL_IDS]. */
    data class ProductRail(override val blockId: String, override val title: String, val productIds: List<String>) : HomeBlock

    /** Taxonomy node ids the client renders with the loaded taxonomy; unique, at most [MAX_GRID_IDS]. */
    data class CategoryGrid(override val blockId: String, override val title: String, val nodeIds: List<String>) : HomeBlock

    companion object {
        /** The backend's own rail bound (`ContentBlock.MAX_RAIL`): every published id can be rendered. */
        const val MAX_RAIL_IDS = 20
        /** The backend's own grid bound (`ContentBlock.MAX_GRID`). */
        const val MAX_GRID_IDS = 12
        /**
         * App-side cap, deliberately TIGHTER than the backend (which serves up to 200 live blocks per placement): the
         * first 20 in display order are rendered and the rest are ignored, so a CMS mistake cannot turn the Home into
         * hundreds of sections and thousands of product reads. Documented in `docs/BACKEND_CONTRACTS.md` §7.
         */
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
        val blockId = b.blockId?.trim().orEmpty()
        if (blockId.isEmpty() || !seen.add(blockId)) continue
        val title = b.title?.trim().orEmpty().take(MAX_TITLE)
        val ids = b.ids.orEmpty()
        val block: HomeBlock? = when (b.type) {
            "BANNER" -> safeImageUrl(b.imageUrl)?.let {
                HomeBlock.Banner(blockId, title, it, ContentLink.parse(b.link), subtitle = text(b.subtitle, MAX_SUBTITLE),
                    altText = text(b.altText, MAX_ALT) ?: title)
            }
            "PRODUCT_RAIL" -> ids(ids, PRODUCT_ID, HomeBlock.MAX_RAIL_IDS).takeIf { it.isNotEmpty() }
                ?.let { HomeBlock.ProductRail(blockId, title, it) }
            "CATEGORY_GRID" -> ids(ids, Regex("^TZ[SCGV]-[0-9]{6}$"), HomeBlock.MAX_GRID_IDS).takeIf { it.isNotEmpty() }
                ?.let { HomeBlock.CategoryGrid(blockId, title, it) }
            else -> null
        }
        if (block != null) out += block
    }
    return HomeContent(out)
}

/** The backend caps a block title at 80 characters; the app never lays out more than that from server text. */
private const val MAX_TITLE = 80
/** Backend bounds for a banner's subtitle and alt text (`ContentBlock.MAX_SUBTITLE` / `MAX_ALT`). */
private const val MAX_SUBTITLE = 120
private const val MAX_ALT = 300

/** Optional server text: trimmed, bounded, and absent when blank. */
private fun text(raw: String?, max: Int): String? = raw?.trim()?.take(max)?.takeIf { it.isNotEmpty() }

private fun ids(raw: List<String>, shape: Regex, max: Int): List<String> =
    raw.asSequence().map { it.trim() }.filter { shape.matches(it) }.distinct().take(max).toList()
