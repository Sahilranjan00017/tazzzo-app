package com.tazzzo.app.catalog

import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.CatalogProductDetail
import com.tazzzo.app.data.catalog.ImageRole
import com.tazzzo.app.data.catalog.ProductAttribute
import com.tazzzo.app.data.catalog.ProductImage
import com.tazzzo.app.data.catalog.StockState
import com.tazzzo.app.data.model.Money
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** A catalogue card with sensible, fully-populated defaults; tests override only what they exercise. */
fun cp(
    sku: String = "SKU-1",
    product: String = "TZP-1",
    price: Money? = Money.ofPaise(4_950),
    mrp: Money? = Money.ofPaise(5_500),
    discountPercent: Int? = 10,
    discountAmount: Money? = Money.ofPaise(550),
    stock: StockState = StockState.IN_STOCK,
    low: Int? = null,
    serviceable: Boolean? = true,
    buyable: Boolean = true,
    max: Int = 10,
    thumb: String? = "https://media.example.test/a.jpg"
) = CatalogProduct(
    skuId = sku, productId = product, name = "Item $sku", brandCode = "BR1", thumbnailUrl = thumb,
    sellingPrice = price, mrp = mrp, discountPercent = discountPercent, discountAmount = discountAmount,
    verticalId = "TZV-000225", stockState = stock, lowStockRemaining = low, maxOrderQuantity = max,
    minimumOrderQuantity = 1, serviceable = serviceable, buyable = buyable
)

fun detailOf(
    card: CatalogProduct = cp(),
    gallery: List<ProductImage> = listOf(
        ProductImage("https://media.example.test/a-0.jpg", ImageRole.PRIMARY, 0, "front", 800, 800),
        ProductImage("https://media.example.test/a-1.jpg", ImageRole.GALLERY, 1, null, null, null)
    ),
    attributes: List<ProductAttribute> = emptyList()
) = CatalogProductDetail(card, gallery, attributes, "rel_1")

fun attr(key: String, value: JsonElement, unit: String? = null) = ProductAttribute(key, key.replaceFirstChar { it.uppercase() }, value, unit)
fun str(v: String) = JsonPrimitive(v)
