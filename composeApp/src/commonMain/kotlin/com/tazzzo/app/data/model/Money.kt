package com.tazzzo.app.data.model

import kotlin.jvm.JvmInline

/**
 * An amount of Indian rupees held as whole **paise** in a `Long`.
 *
 * ₹1 = 100 paise, ₹99 = 9900 paise, matching the backend's `*Paise` fields.
 * There is no floating-point path in or out: construct from rupees or paise,
 * format deterministically, and combine with checked arithmetic.
 *
 * The existing rupee-`Int` models (Product, BillSummary, CartLine) are not yet
 * migrated; that happens with their integration PRs.
 */
@JvmInline
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
