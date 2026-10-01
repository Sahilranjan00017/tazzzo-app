package com.tazzzo.app.catalog

import com.tazzzo.app.auth.BASE
import com.tazzzo.app.auth.JSON_HEADERS
import com.tazzzo.app.data.catalog.CatalogReader
import com.tazzzo.app.data.catalog.InstallationId
import com.tazzzo.app.data.catalog.RemoteCatalogDataSource
import com.tazzzo.app.data.catalog.RemoteServiceabilityDataSource
import com.tazzzo.app.data.catalog.TaxonomyCache
import com.tazzzo.app.data.local.PersistentStore
import com.tazzzo.app.data.remote.ApiClient
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf

const val INSTALL_ID = "tzi-0123456789abcdef0123456789abcdef"

fun installationId(): InstallationId = InstallationId.getOrCreate(PersistentStore(MapSettings().also {
    PersistentStore(it).installationId = INSTALL_ID
}))

fun mockApi(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
    ApiClient(baseUrl = BASE, engine = MockEngine(handler))

fun dataSource(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
    RemoteCatalogDataSource(mockApi(handler)) { installationId() }

fun serviceabilitySource(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
    RemoteServiceabilityDataSource(mockApi(handler)) { installationId() }

fun reader(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): CatalogReader {
    val ds = dataSource(handler)
    return CatalogReader(ds, TaxonomyCache(ds))
}

fun etagHeaders(etag: String): Headers = headersOf(
    HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf(etag)
)

val JSON = JSON_HEADERS

// ---- fixtures (shapes of the RUNNING backend) ---------------------------------------------------

fun nodeListJson(vararg items: Pair<String, String>, release: String = "rel_1") =
    """{"resolvedReleaseId":"$release","items":[${items.joinToString(",") { """{"id":"${it.first}","name":"${it.second}"}""" }}],"requestId":"req_1"}"""

/** A fully populated, buyable card. */
fun cardJson(
    id: String = "TZP-1",
    price: Long? = 4950,
    mrp: Long? = 5500,
    stock: String = "IN_STOCK",
    buyable: Boolean = true,
    serviceable: String = "true",
    max: Int = 10,
    extra: String = ""
): String = buildString {
    append("""{"skuId":"$id","productId":"$id","name":"Item $id","brandCode":"BR1","thumbnailUrl":"https://media.example.test/$id.jpg",""")
    if (price != null) append(""""sellingPricePaise":$price,""")
    if (mrp != null) append(""""mrpPaise":$mrp,""")
    if (price != null && mrp != null && mrp > price) append(""""discountPercent":${(mrp - price) * 100 / mrp},"discountAmountPaise":${mrp - price},""")
    append(""""verticalId":"TZV-000225","stockState":"$stock",""")
    if (stock == "LOW_STOCK") append(""""lowStockRemaining":3,""")
    append(""""sponsored":false,"maxOrderQuantity":$max,"minimumOrderQuantity":1,""")
    if (serviceable != "null") append(""""serviceable":$serviceable,""")
    append(""""buyable":$buyable$extra}""")
}

fun pageJson(
    cards: List<String>, next: String? = null, area: String? = """{"serviceAreaId":"SA-BLR-01","serviceable":true}""", release: String = "rel_1"
): String = buildString {
    append("""{"resolvedReleaseId":"$release",""")
    if (area != null) append(""""serviceArea":$area,""")
    append(""""items":[${cards.joinToString(",")}],""")
    if (next != null) append(""""nextCursor":"$next",""")
    append(""""hasMore":${next != null},"requestId":"req_2"}""")
}

fun detailJson(id: String = "TZP-1") = cardJson(id, extra = """,
  "gallery":[{"url":"https://media.example.test/$id-2.jpg","role":"GALLERY","order":2},
             {"url":"https://media.example.test/$id-0.jpg","role":"PRIMARY","order":0,"alt":"front","width":800,"height":800},
             {"url":"http://insecure.example.test/x.jpg","role":"GALLERY","order":3}],
  "attributes":[{"key":"weight","label":"Weight","value":500,"unit":"g"},{"key":"veg","label":"Vegetarian","value":true}],
  "resolvedReleaseId":"rel_1","requestId":"req_3" """.trimIndent())

fun serviceabilityJson(serviceable: Boolean, area: Boolean = serviceable) =
    if (area) """{"serviceable":$serviceable,"serviceAreaId":"SA-BLR-01","serviceAreaVersion":7,"requestId":"req_4"}"""
    else """{"serviceable":$serviceable,"requestId":"req_4"}"""

fun errorFlat(code: String, retryable: Boolean = false, retryAfter: Long? = null) =
    """{"code":"$code","message":"generic","requestId":"req_e","retryable":$retryable${retryAfter?.let { ""","retryAfterSeconds":$it""" } ?: ""}}"""

const val LEGACY_404 = """{"error":{"code":"NO_SUCH_ENDPOINT","message":"nope","request_id":"req_l"}}"""
