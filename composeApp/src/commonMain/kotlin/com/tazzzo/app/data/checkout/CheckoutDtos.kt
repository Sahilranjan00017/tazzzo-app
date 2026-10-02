package com.tazzzo.app.data.checkout

import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import kotlinx.serialization.Serializable

// Wire shape of the RUNNING backend (`customer.checkout.CheckoutQuoteDto`), only the fields it sends.

@Serializable internal data class QuoteItemDto(val skuId: String, val quantity: Int, val unitPricePaise: Long, val lineTotalPaise: Long)

@Serializable internal data class BenefitPreviewDto(val applied: Boolean, val discountPaise: Long? = null, val discountBps: Int? = null)

@Serializable internal data class QuoteDto(
    val quoteId: String,
    val cartVersion: Long,
    val addressId: String,
    val items: List<QuoteItemDto> = emptyList(),
    val itemCount: Int = 0,
    val distinctItemCount: Int = 0,
    val subtotalPaise: Long,
    val currency: String = "INR",
    val createdAt: String,
    val expiresAt: String,
    val benefitPreview: BenefitPreviewDto? = null,
    val requestId: String? = null
)

private fun malformed(): Nothing = throw ApiException(ApiError.Decoding())

private fun paise(v: Long): Money { if (v < 0) malformed(); return Money.ofPaise(v) }

internal fun BenefitPreviewDto?.toDomain(): BenefitPreviewState = when {
    this == null -> BenefitPreviewState.Legacy
    !applied -> if (discountPaise == null && discountBps == null) BenefitPreviewState.NotApplied else BenefitPreviewState.Unreadable
    discountPaise != null && discountPaise >= 1 && discountBps != null && discountBps in 1..10_000 ->
        BenefitPreviewState.Applied(Money.ofPaise(discountPaise), discountBps)
    else -> BenefitPreviewState.Unreadable
}

internal fun QuoteDto.toDomain(): CheckoutQuote {
    if (quoteId.isBlank() || addressId.isBlank() || cartVersion < 0) malformed()
    val created = Iso8601.parseMillis(createdAt) ?: malformed()
    val expires = Iso8601.parseMillis(expiresAt) ?: malformed()
    if (expires <= created) malformed()
    return CheckoutQuote(
        quoteId = quoteId, cartVersion = cartVersion, addressId = addressId,
        items = items.map {
            if (it.skuId.isBlank() || it.quantity < 1) malformed()
            CheckoutQuoteItem(it.skuId, it.quantity, paise(it.unitPricePaise), paise(it.lineTotalPaise))
        },
        itemCount = itemCount.coerceAtLeast(0), distinctItemCount = distinctItemCount.coerceAtLeast(0),
        subtotal = paise(subtotalPaise), currency = currency, createdAtMillis = created, expiresAtMillis = expires,
        benefit = benefitPreview.toDomain(), requestId = requestId
    )
}

/** Minimal UTC ISO-8601 (`2026-10-02T09:14:00.123Z`) to epoch millis. Only the server's own format is accepted. */
internal object Iso8601 {
    private val FORMAT = Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d{1,9}))?Z$")

    fun parseMillis(s: String): Long? {
        val m = FORMAT.matchEntire(s)?.groupValues ?: return null
        val y = m[1].toInt(); val mo = m[2].toInt(); val d = m[3].toInt()
        val h = m[4].toInt(); val mi = m[5].toInt(); val sec = m[6].toInt()
        if (mo !in 1..12 || d !in 1..31 || h > 23 || mi > 59 || sec > 59) return null
        val frac = m[7].padEnd(3, '0').take(3).toInt()
        return (daysFromCivil(y, mo, d) * 86_400L + h * 3_600L + mi * 60L + sec) * 1_000L + frac
    }

    // Howard Hinnant's days-from-civil.
    private fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
        val y = if (m <= 2) y0 - 1 else y0
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097L + doe - 719_468L
    }
}
