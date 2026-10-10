package com.tazzzo.app.data.model

/**
 * The backend's V1 customer money: `payable = merchandiseSubtotal - benefitDiscount`, and nothing else. There is no
 * delivery/platform/handling fee, tax line, COD charge, coupon, Coins or wallet component, so none exists here.
 *
 *  - On a checkout quote (`moneyPreview`) it is ADVISORY: Order placement revalidates and computes its own money, which may
 *    differ (not an error; the order's money holds). The app shows it as the total for review, never as "due".
 *  - On an order (`money`) it is AUTHORITATIVE: what was committed at placement. [payable] is DUE ON DELIVERY, never "paid".
 *
 * It can only exist consistent: `0 <= benefitDiscount <= merchandiseSubtotal` and `payable == subtotal - discount`. It is
 * never computed by the app, never persisted, never logged and never sent to analytics.
 */
class PayableMoney private constructor(
    val merchandiseSubtotal: Money,
    val benefitDiscount: Money,
    val payable: Money
) {
    val hasDiscount: Boolean get() = benefitDiscount.paise > 0
    val isNothingDue: Boolean get() = payable.paise == 0L

    override fun equals(other: Any?): Boolean = other is PayableMoney && merchandiseSubtotal == other.merchandiseSubtotal &&
        benefitDiscount == other.benefitDiscount && payable == other.payable
    override fun hashCode(): Int = (merchandiseSubtotal.paise * 31 + benefitDiscount.paise * 17 + payable.paise).hashCode()
    override fun toString(): String = "PayableMoney(***)"

    companion object {
        /** The three wire values, or null when they violate the contract. No value is ever repaired or chosen. */
        fun fromPaise(merchandiseSubtotalPaise: Long, benefitDiscountPaise: Long, payablePaise: Long): PayableMoney? {
            if (merchandiseSubtotalPaise < 0 || benefitDiscountPaise < 0 || payablePaise < 0) return null
            if (benefitDiscountPaise > merchandiseSubtotalPaise) return null
            if (payablePaise != merchandiseSubtotalPaise - benefitDiscountPaise) return null
            return PayableMoney(Money.ofPaise(merchandiseSubtotalPaise), Money.ofPaise(benefitDiscountPaise), Money.ofPaise(payablePaise))
        }
    }
}
