package com.tazzzo.app.data.remote

/**
 * Network host seam. **This file is NOT a backend contract.**
 *
 * The authoritative description of every endpoint, request and response shape
 * lives in the documents, in this order of precedence:
 *
 *   1. `docs/BACKEND_INTEGRATION_READINESS.md`  — source of truth
 *   2. `docs/BACKEND_CONTRACTS.md`              — earlier, narrower note
 *
 * This file previously carried its own "endpoint sketch" listing paths that
 * disagreed with both documents (it had no addresses, payments, carts or
 * serviceability service, and it named a `VOICE_SERVICE` for a feature that
 * does not exist — the voice sheet is a dismiss-only "coming soon" card with
 * no capture, no recognition and no waitlist). Three competing sources of
 * truth is one more than the project can survive, so the sketch is gone.
 * The endpoint table in the readiness report replaces it.
 *
 * Nothing referenced these constants at the time they were written; the first
 * real consumer will be the `Remote*` repositories, which do not exist yet.
 *
 * When the backend is ready:
 *   1. Resolve the base URL from the build environment (see `AppEnvironment`).
 *   2. Implement the `Remote*` repositories against the documented contract —
 *      not against paths invented here.
 *   3. Swap the `Mock*` implementations in `ServiceLocator`, keeping the mocks
 *      as offline fallback, demo mode and test doubles.
 */
object ApiConfig {

    /**
     * API gateway origin. Paths are owned by the contract documents and by the
     * repository implementation that calls them, never by this object.
     *
     * No credentials, tokens or secrets belong in this file or anywhere else
     * in source. See `data/local/SecureStore` for where auth material lives.
     */
    const val GATEWAY_BASE_URL: String = "https://api.tazzzo.com"
}
