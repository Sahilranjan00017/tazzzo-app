package com.tazzzo.app.data.model

import kotlinx.serialization.Serializable

/**
 * A seasonal / festival merchandising moment. Configuration, not code: the
 * same hero renders Janmashtami today and Diwali next month from data alone.
 *
 * @param categoryIds existing catalogue categories that make up the collection.
 *   Nothing is invented for a festival — the collection is a curated view of
 *   what the store already sells.
 * @param heroImageUrl production artwork. Null = `[ASSET REQUIRED]`; the hero
 *   renders a typographic treatment with the collection's category art until
 *   supplied. Never a fabricated or borrowed image.
 * @param validFromLabel / validUntilLabel display copy. Real date-windowing is
 *   `[BACKEND REQUIRED]`; the client does not gate merchandising on its clock.
 */
@Serializable
data class Campaign(
    val id: String,
    val title: String,
    val subtitle: String,
    val ctaLabel: String,
    val categoryIds: List<String>,
    val heroImageUrl: String? = null,
    val validFromLabel: String? = null,
    val validUntilLabel: String? = null,
    val memberOnlyOffer: Boolean = false
)
