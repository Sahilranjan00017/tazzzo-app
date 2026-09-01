package com.tazzzo.app.data.model

data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val tint: Long,             // pastel tile background (ARGB)
    val group: String,          // top-level group shown as section header
    val subcategories: List<Subcategory> = emptyList()
)

data class Subcategory(
    val id: String,
    val name: String,
    val emoji: String
)

/**
 * Stock position for a SKU at the serving dark store.
 *
 * Availability is a DATA concern, not a UI concern: the model must be able to
 * carry what the backend reports so the UI can react. Solving out-of-stock only
 * in the UI layer is how grocery apps end up selling stock they do not have.
 */
sealed interface Availability {
    data object InStock : Availability
    /** In stock but scarce — drives "Only N left" urgency and caps quantity. */
    data class LowStock(val remaining: Int) : Availability
    data object OutOfStock : Availability
    /** Not sold at the customer's current location. */
    data object NotServiceable : Availability

    val isPurchasable: Boolean get() = this is InStock || this is LowStock
}

data class Product(
    val id: String,
    val name: String,
    val brand: String,
    val emoji: String,
    val unit: String,           // "500 g", "1 L", "6 pcs"
    val price: Int,             // selling price in ₹
    val mrp: Int,               // strike-through price in ₹
    val categoryId: String,
    val subcategoryId: String,
    val rating: Double,
    val ratingCount: Int,
    val etaMinutes: Int = 59,
    val tags: List<String> = emptyList(),   // "Bestseller", "New", "Veg"
    val highlights: List<String> = emptyList(),
    val availability: Availability = Availability.InStock,
    /** Hard ceiling per order, independent of stock (e.g. fair-use limits). */
    val maxOrderQuantity: Int = 10,
    val imageUrl: String? = null
) {
    val discountPercent: Int get() = if (mrp > price) ((mrp - price) * 100) / mrp else 0

    val isPurchasable: Boolean get() = availability.isPurchasable

    /** The most a customer may add right now, respecting both stock and policy. */
    val purchasableLimit: Int
        get() = when (val a = availability) {
            is Availability.LowStock -> minOf(a.remaining, maxOrderQuantity)
            Availability.InStock -> maxOrderQuantity
            else -> 0
        }
}

data class PromoBanner(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val dark: Boolean           // dark-green banner vs orange banner
)

data class CartLine(val product: Product, val quantity: Int) {
    val lineTotal: Int get() = product.price * quantity
    val lineMrp: Int get() = product.mrp * quantity
}

data class BillSummary(
    val itemTotal: Int,
    val itemMrpTotal: Int,
    val deliveryFee: Int,
    val handlingCharge: Int,
    val coinsEarned: Int,
    val grandTotal: Int
) {
    val saved: Int get() = itemMrpTotal - itemTotal
}

enum class OrderStatus { PLACED, PACKED, ON_THE_WAY, DELIVERED }

data class Order(
    val id: String,
    val lines: List<CartLine>,
    val bill: BillSummary,
    val status: OrderStatus,
    val placedAtLabel: String,
    val address: String,
    /** How the customer paid. Nullable so historical/mock orders stay valid. */
    val payment: PaymentMethodKind? = null
)

data class CoinTransaction(
    val id: String,
    val title: String,
    val amount: Int,            // positive = earned, negative = spent
    val dateLabel: String
)

data class UserProfile(
    val name: String,
    val phone: String,
    val isGuest: Boolean,
    val coinBalance: Int,
    val address: String
)

data class FaqItem(val question: String, val answer: String)
