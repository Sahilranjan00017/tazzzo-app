package com.tazzzo.app.data.content

import com.tazzzo.app.data.catalog.InstallationId
import com.tazzzo.app.data.remote.ApiClient
import com.tazzzo.app.data.remote.ApiRequest
import com.tazzzo.app.data.remote.execute
import io.ktor.http.HttpMethod

/**
 * `GET /v1/content/home?channel=app` — anonymous (no token is read or sent), carries the installation id like every
 * public read, and identifies the platform as `app` so content published APP_ONLY reaches it and WEB_ONLY never does.
 * The filtering is authoritative on the backend; the app only names itself. Every non-2xx is an
 * [com.tazzzo.app.data.remote.ApiException] for the holder to classify; nothing here falls back to bundled content.
 */
class RemoteContentDataSource(
    private val api: ApiClient,
    private val installationId: () -> InstallationId
) {
    suspend fun home(): HomeContent = api.execute<HomeContentDto>(
        ApiRequest(
            method = HttpMethod.Get, path = "/v1/content/home", query = mapOf("channel" to CHANNEL),
            authenticated = false, headers = mapOf(InstallationId.HEADER to installationId().value)
        )
    ).body.toDomain()

    private fun get(path: String, query: Map<String, String?> = emptyMap()) = ApiRequest(
        method = HttpMethod.Get, path = path, query = query, authenticated = false,
        headers = mapOf(InstallationId.HEADER to installationId().value)
    )

    /** `GET /v1/content/faqs` (all categories), grouped for the Help screen. */
    suspend fun faqs(): List<FaqGroup> = api.execute<FaqsDto>(get("/v1/content/faqs")).body.toGroups()

    /** `GET /v1/app-config` (no parameters): support contacts and store state. */
    suspend fun appConfig(): AppInfo = api.execute<AppConfigDto>(get("/v1/app-config")).body.toDomain()

    /**
     * `GET /v1/content/legal/{slug}`. Null = not published (404 — an older backend without the endpoint answers 404 too) or an
     * empty body; any other failure is thrown for the screen's failure state.
     */
    suspend fun legal(slug: LegalSlug): LegalDocument? = try {
        api.execute<LegalDocumentDto>(get("/v1/content/legal/${slug.path}")).body.toDomain(slug)
    } catch (e: com.tazzzo.app.data.remote.ApiException) {
        if ((e.error as? com.tazzzo.app.data.remote.ApiError.Http)?.status == 404) null else throw e
    }

    companion object {
        /** The backend's closed channel vocabulary: `app` | `web`. */
        const val CHANNEL = "app"
    }
}
