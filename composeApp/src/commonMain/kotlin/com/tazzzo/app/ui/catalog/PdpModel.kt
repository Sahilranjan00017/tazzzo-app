package com.tazzzo.app.ui.catalog

import com.tazzzo.app.data.catalog.CatalogProductDetail
import com.tazzzo.app.data.catalog.ImageRole
import com.tazzzo.app.data.catalog.StockTone
import com.tazzzo.app.data.catalog.discountPercentLabel
import com.tazzzo.app.data.catalog.displayValue
import com.tazzzo.app.data.catalog.mrpLabel
import com.tazzzo.app.data.catalog.priceLabel
import com.tazzzo.app.data.catalog.stockLabel

/*
 * The TRUTHFUL bindings of the product page (UI Page reference `Veg Page.jpeg`). Pure, unit-tested. The reference shows
 * a pack size, a description, ratings, a member price, pack selectors and "People also bought"; the running backend
 * sends none of those, so none of them exist here — a field that is not in [CatalogProductDetail] cannot be rendered.
 */

object PdpCopy {
    const val ADD_TO_CART = "Add to cart"
    const val DETAILS = "Details"
    const val NOT_FOUND_TITLE = "This product isn't available right now."
    const val NOT_FOUND_BODY = "It may have been removed or isn't sold in your area."
    const val BACK_TO_SHOP = "Back to Shop"
    const val PRICE_UNAVAILABLE = "Price unavailable"
}

data class PdpFacts(
    val name: String,
    /** What the hero shows, in order: the PRIMARY gallery image, the other gallery images, else the card thumbnail. */
    val heroUrls: List<String>,
    /** The taxonomy name of the product's vertical when this process already knows it; never a guess. */
    val eyebrow: String?,
    val price: String?,
    val mrp: String?,
    val discount: String?,
    /** Scarcity, unavailability or "not available at your location" only; "In stock" and "unknown" are noise here too. */
    val stockNote: String?,
    val stockTone: StockTone?,
    /** Governed attributes with a one-line display form, in backend order. Empty = no Details section. */
    val details: List<Pair<String, String>>
) {
    val galleryCount: Int get() = heroUrls.size
    val hasHero: Boolean get() = heroUrls.isNotEmpty()
}

fun CatalogProductDetail.pdpFacts(nameOfNode: (String) -> String? = { null }): PdpFacts {
    val p = product
    val gallery = this.gallery.sortedWith(compareBy({ if (it.role == ImageRole.PRIMARY) 0 else 1 }, { it.order })).map { it.url }
    val hero = (gallery.ifEmpty { listOfNotNull(p.thumbnailUrl) }).distinct()
    val stock = p.stockLabel()
    val showStock = stock.tone == StockTone.Scarce || stock.tone == StockTone.Unavailable || p.serviceable == false
    return PdpFacts(
        name = p.name,
        heroUrls = hero,
        eyebrow = p.verticalId?.let(nameOfNode)?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
        price = p.priceLabel(),
        mrp = p.mrpLabel(),
        discount = p.discountPercentLabel(),
        stockNote = if (showStock) stock.text else null,
        stockTone = if (showStock) stock.tone else null,
        details = attributes.mapNotNull { a -> a.displayValue()?.let { a.label to it } }
    )
}

/** The hero's share of the screen in the reference (540 of 1280 px ≈ 42%); expressed as a width:height aspect at 390dp. */
const val PDP_HERO_ASPECT = 588f / 560f

/** How far the curved content panel rises over the hero (dp). */
const val PDP_CURVE_OVERLAP_DP = 28
