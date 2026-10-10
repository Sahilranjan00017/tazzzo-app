package com.tazzzo.app.data.repository

import com.tazzzo.app.data.MockCatalog
import com.tazzzo.app.data.model.FaqItem
import kotlinx.coroutines.delay

/**
 * Help / support content.
 *
 * Added because help content was the one customer-facing data surface with no
 * repository at all — HelpScreen read the fixtures directly, so it would have
 * kept rendering seed FAQs after every other surface moved to the backend.
 *
 * Contract: `GET /v1/content/faqs` (docs/BACKEND_INTEGRATION_READINESS.md §1,
 * docs/BACKEND_CONTRACTS.md). Search stays client-side over the returned list,
 * which is what the screen does today; no search endpoint is assumed.
 */
interface SupportRepository {
    suspend fun getFaqs(): List<FaqItem>
}

/**
 * Development fixtures. Kept after the real service lands, as the offline
 * fallback, the demo-mode source and the test double.
 */
class MockSupportRepository : SupportRepository {
    override suspend fun getFaqs(): List<FaqItem> {
        delay(FAKE_LATENCY_MS)
        return MockCatalog.faqs
    }
}
