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
        id = "janmashtami-2026",
        title = "Janmashtami at Tazzzo",
        subtitle = "Everything for the celebration at home",
        ctaLabel = "Shop the collection",
        categoryIds = listOf("dairy", "sweet", "oil", "fruits"),   // dairy · sweets · dry fruits (in oil group) · fruits
        heroImageUrl = null,                                        // [ASSET REQUIRED] production artwork
        validUntilLabel = "Ends this week"
    )

    /** Campaigns the system can rotate to; same shape, different data. */
    val calendar: List<Campaign> = listOfNotNull(current)
}
