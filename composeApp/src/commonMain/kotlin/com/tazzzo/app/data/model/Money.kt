package com.tazzzo.app.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline

/**
 * An amount of Indian rupees held as whole **paise** in a `Long`.
 *
 * ₹1 = 100 paise, ₹99 = 9900 paise, matching the backend's `*Paise` fields.
 * There is no floating-point path in or out: construct from rupees or paise,
 * format deterministically, and combine with checked arithmetic.
 *
 * This is the app's ONLY money representation (PR-04B): every price, fee,
 * threshold, discount and total in the commerce model is a [Money]. On the wire
 * and in storage it is the bare paise `Long`.
 *
 * **Sign policy.** [Money] itself is signed — subtraction can go below zero and
 * accounting adjustments need that. Customer-facing amounts (prices, charges,
 * discounts-as-magnitudes, totals) are NOT: the models that hold them reject a
 * negative value in their `init`. A discount or credit is a separate,
 * non-negative field that is subtracted, never a negative price.
 *
 * **Rounding policy.** Proportional amounts use integer floor division only
 * ([percentOf]); there is no floating-point path. Defined for non-negative
 * inputs; signed inputs are rejected rather than left to ambiguous rounding.
 */
@JvmInline
@Serializable(with = MoneySerializer::class)
value class Money private constructor(val paise: Long) : Comparable<Money> {

    val isZero: Boolean get() = paise == 0L
    val isNegative: Boolean get() = paise < 0L

    /** @throws ArithmeticException on overflow. */
    operator fun plus(other: Money): Money {
        val r = paise + other.paise
        if (((paise xor r) and (other.paise xor r)) < 0) throw ArithmeticException("Money overflow")
        return Money(r)
    }

    /** @throws ArithmeticException on overflow. */
    operator fun minus(other: Money): Money {
        val r = paise - other.paise
        if (((paise xor other.paise) and (paise xor r)) < 0) throw ArithmeticException("Money overflow")
        return Money(r)
    }

    /** Quantity multiplication, e.g. unit price × units. @throws ArithmeticException on overflow. */
    operator fun times(quantity: Int): Money = Money(multiplyExact(paise, quantity.toLong()))

    operator fun unaryMinus(): Money {
        if (paise == Long.MIN_VALUE) throw ArithmeticException("Money overflow")
        return Money(-paise)
    }

    override fun compareTo(other: Money): Int = paise.compareTo(other.paise)

    val isPositive: Boolean get() = paise > 0L

    /**
     * `percent`% of this amount, FLOORED to whole paise: `floor(paise * percent / 100)`.
     *
     * Computed as `(paise / 100) * percent + (paise % 100) * percent / 100`, which is exactly
     * the floor of the product but cannot overflow in the multiplication of the two large
     * factors the way `paise * percent` can; only the final result is overflow-checked.
     *
     * @throws IllegalArgumentException for a negative amount or a negative percent (undefined here).
     * @throws ArithmeticException if the result does not fit in a `Long`.
     */
    fun percentOf(percent: Int): Money {
        require(paise >= 0L) { "percentOf requires a non-negative amount" }
        require(percent >= 0) { "percentOf requires a non-negative percent" }
        val whole = multiplyExact(paise / 100L, percent.toLong())
        val rest = (paise % 100L) * percent.toLong() / 100L // < 100 * Int.MAX / 100: fits
        return Money(plusExact(whole, rest))
    }

    /** Integer floor division into [parts] equal shares (non-negative amount, positive divisor). */
    fun floorDiv(parts: Long): Money {
        require(paise >= 0L && parts > 0L) { "floorDiv requires a non-negative amount and a positive divisor" }
        return Money(paise / parts)
    }

    /**
     * `₹99`, `₹99.50`, `₹1,23,456` (Indian digit grouping), `-₹5`.
     * Whole-rupee amounts omit the decimals; anything with paise shows exactly two.
     */
    fun format(): String {
        // abs without overflowing on Long.MIN_VALUE: work on the unsigned magnitude as a string.
        val negative = paise < 0
        val digits = if (negative) paise.toString().drop(1) else paise.toString()
        val padded = digits.padStart(3, '0')
        val rupees = padded.dropLast(2)
        val fraction = padded.takeLast(2)
        val text = "₹" + groupIndian(rupees) + if (fraction == "00") "" else ".$fraction"
        return if (negative) "-$text" else text
    }

    override fun toString(): String = format()

    companion object {
        val ZERO = Money(0L)

        fun ofPaise(paise: Long): Money = Money(paise)

        /** @throws ArithmeticException if the amount does not fit in paise. */
        fun ofRupees(rupees: Long): Money = Money(multiplyExact(rupees, 100L))

        private fun plusExact(a: Long, b: Long): Long {
            val r = a + b
            if (((a xor r) and (b xor r)) < 0) throw ArithmeticException("Money overflow")
            return r
        }

        /** A customer-facing amount: rejects a negative value. */
        fun requireNonNegative(m: Money, what: String = "amount"): Money {
            require(m.paise >= 0L) { "$what must not be negative" }
            return m
        }

        private fun multiplyExact(a: Long, b: Long): Long {
            if (a == 0L || b == 0L) return 0L
            val r = a * b
            if (r / b != a || (a == Long.MIN_VALUE && b == -1L) || (b == Long.MIN_VALUE && a == -1L)) {
                throw ArithmeticException("Money overflow")
            }
            return r
        }

        /** 1234567 -> 12,34,567 */
        private fun groupIndian(rupees: String): String {
            if (rupees.length <= 3) return rupees
            val last3 = rupees.takeLast(3)
            val rest = rupees.dropLast(3)
            val groups = rest.reversed().chunked(2).map { it.reversed() }.reversed()
            return groups.joinToString(",") + "," + last3
        }
    }
}

/** The wire/storage form of [Money]: its paise as a bare `Long`. */
object MoneySerializer : KSerializer<Money> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("com.tazzzo.app.Money", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Money) = encoder.encodeLong(value.paise)
    override fun deserialize(decoder: Decoder): Money = Money.ofPaise(decoder.decodeLong())
}

/** Checked sum of a selector over a collection; [Money.ZERO] for an empty one. */
inline fun <T> Iterable<T>.sumOfMoney(selector: (T) -> Money): Money {
    var total = Money.ZERO
    for (e in this) total += selector(e)
    return total
}
