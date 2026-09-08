package com.tazzzo.app.config

import com.tazzzo.app.data.model.Campaign

/**
 * [MOCKED] The active campaign. A `CampaignRepository` fronting the backend
 * replaces this; the hero is already data-driven so that swap changes no UI.
 * Category ids are real fixture categories — the collection is curated from
 * the live catalogue, never fabricated for the festival.
 */
object CampaignConfig {
    val current: Campaign? = Campaign(
        id = "ganesh-chaturthi-2026",
        title = "Ganesh Chaturthi at Tazzzo",
        subtitle = "Pooja essentials, sweets and ghee",
        ctaLabel = "Shop the collection",
        categoryIds = listOf("pooja", "sweet", "oil", "atta"),   // pooja needs · sweets · ghee (in the oil group) · atta for prasad
        heroImageUrl = null,                                        // [ASSET REQUIRED] production artwork
        validUntilLabel = "Festival collection"
    )

    /** Campaigns the system can rotate to; same shape, different data. */
    val calendar: List<Campaign> = listOfNotNull(current)
}
