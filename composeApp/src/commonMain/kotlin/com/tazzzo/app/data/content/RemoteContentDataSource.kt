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

    companion object {
        /** The backend's closed channel vocabulary: `app` | `web`. */
        const val CHANNEL = "app"
    }
}
