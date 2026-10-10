# Backend contracts required by the app

Documented instead of invented, per engineering rule. Each entry names the
app-side seam that will consume it. Owner: backend/microservices team.

## Required for Phase 2 parity (app currently fakes these locally)

### 1. Product by id
`GET /catalog/v1/products/{id}` → Product
- Consumer: `ProductDetailScreen` (today it resolves the id by scanning
  category listings through the repository — works, but is O(categories) and
  wrong once the catalogue is large).

### 2. Search
`GET /catalog/v1/search?q=&sort=&in_stock=&brand=&limit=&offset=`
→ `{ products: [...], appliedQuery: "atta" }`
- Consumer: `CatalogRepository.search()`. The app-side `SearchEngine`
  (typo tolerance, Hindi synonyms, ranking) is a stand-in for a real search
  service; its behaviour is the spec: `aata`→atta, `doodh`→milk, prefix ≥3,
  edit distance 1 (len 4–5) / 2 (len ≥6).
- `appliedQuery` lets the UI show "showing results for atta".

### 3. Listing filters server-side
`GET /catalog/v1/categories/{id}/products?subcategory=&sort=&in_stock=&brand=`
- Consumer: `ProductFilters` in `ui/common/Filters.kt` — the mapping from
  filter state to query params happens in exactly one place.

### 4. Stock / availability
Every product payload must include:
```json
"availability": { "status": "IN_STOCK | LOW_STOCK | OUT_OF_STOCK | NOT_SERVICEABLE",
                   "remaining": 3 },
"maxOrderQuantity": 10
```
- Consumer: `Availability` sealed type in `data/model/Models.kt`; cart
  enforcement in `AppState.addToCart`.

### 5. Delivery promise / serviceability  [gates decision D4]
`GET /serviceability/v1/promise?lat=&lng=` or `?pincode=`
→ `{ status: "SERVICEABLE|NOT_SERVICEABLE", etaMinutes: 42, window: {min,max} }`
- Consumer: `AppConfig.deliveryPromise` (`DeliveryPromise` sealed type).
  Until this exists the app shows neutral "Fast delivery" copy.

### 6. Coin rules  [gates decision D5]
`GET /coins/v1/rules` → `{ earnPercent, rupeesPerCoin, maxRedeemPerOrder, expiryDays, enabled }`
- Consumer: `AppConfig.coins` (`CoinRules`). Current values are development
  configuration, not approved financial policy.

## Consumed from the running backend (not faked)

### 7. Published Home content — `GET /v1/content/home?channel=app`
`{ blocks: [ { blockId, type: "BANNER|PRODUCT_RAIL|CATEGORY_GRID", title, subtitle?, altText?, imageUrl?, desktopImageUrl?, link?, ids? } ], requestId }`
(backend PR #96 for `channel`; banner fields `subtitle` / `altText` / `desktopImageUrl` from backend PR #102)
- Consumer: `data/content/RemoteContentDataSource` → `HomeContentHolder` → `RemoteHomeScreen` (the blocks render in the
  backend's order, before the editorial plates). Anonymous, carries the installation id, names the platform `app`;
  the backend decides what the app sees (APP_ONLY or BOTH), never the app. An unknown block type is skipped.
- Banners: need an `https` `imageUrl` or are not shown; `desktopImageUrl` is the website's and is ignored (the app always
  renders `imageUrl`). `subtitle` (≤ 120) renders under the title when published. `altText` (≤ 300; the backend sends the
  title when the editor gave none, and the app falls back to the title for an older backend) describes the image. A
  banner is ONE accessibility node read once: title, subtitle, then the alt text only when it differs from the title;
  the image and overlay text are hidden from the tree; a tappable banner has the button role. `link` is the closed
  grammar `product:<id>` | `category:<node id>` | `search:<text>`; anything else leaves the banner untappable, and
  `search:` is untappable too — the app's Search screen cannot open on a query (in REMOTE mode it has no search
  contract at all), so it stays a picture rather than an empty search.
- Rails: up to 20 ids (the backend's own bound), cards through `GET /v1/products/{id}` for the current PIN (404 =
  absent, parallelism 4), in a horizontal lazy row. A per-PIN card cache keeps rails on screen across re-reads: only ids
  the app has not got, or cards older than 5 min, are read again; a pull re-reads every card; a PIN change reloads the
  rails (old cards would describe the wrong PIN). A pull re-reads only cards older than 30 s, so repeated pulls cost at
  most one card sweep per 30 s; a pull that supersedes a sweep in flight keeps the cards already read.
  Every product read is admission-charged, so a sweep is bounded: at most 120 distinct card reads (ids beyond that are
  read by the next sweep, not penalised; rails already showing cards stay up). A failed batch ends there and its ids are
  not re-asked by another rail or a pull for 60 s. A **429** from a product read stops the whole sweep at once and opens
  ONE window for ALL product reads for `Retry-After` (`RetryPolicy`, capped at 120 s, never under 60 s, as the node
  resolver); inside it a pull is refused with the transient toast, and a foreground re-read or PIN change sends no product
  read (rails with no cards show nothing). **Follow-up, not in this PR:** replace the per-id reads with
  `/v1/products:batch` (one charged read per rail) and drop the budget.
- Product ids: the platform grammar `TZP-[A-Za-z0-9-]{1,40}` (backend `ContentBlock.PRODUCT_ID`, CMS, storefront),
  numeric ids included. The app validates rail ids, `product:` links, the `GET /v1/products/{id}` path and the cart
  `skuId` (`PUT` / `DELETE /v1/customer/cart/items/{skuId}`, backend `CartController` since tazzzo-backend #110) with
  exactly this grammar, from ONE shared constant (`data/catalog/CatalogModels.kt` `PRODUCT_ID`); an id outside it is
  dropped from a rail, leaves the banner untappable, cannot be carted, and never reaches the wire. The grammar admits
  no `/`, `.`, `%`, `?` or whitespace, so an id is always ONE unescaped path segment.
- Grids: up to 12 node ids at ANY level (`TZS` / `TZC` / `TZG` / `TZV`), each named with ONE
  `GET /v1/categories/{id}` (backend PR #109: `{id, name, resolvedReleaseId, requestId}`; 404 = unknown, hidden or
  consumer-empty). `data/content/CategoryNodeResolver`: sequential, in published order, at most 12 reads per
  resolution (ids beyond that are read by the next one, not penalised); a named node is cached 300 s (the route's
  `max-age`), a 404 is remembered 300 s and drops the tile (also one shown before). The first failed read ends the
  resolution, and the failed and unread ids wait 60 s; a 429 stops EVERY node read for its `Retry-After`, capped at
  120 s (`RetryPolicy`) and never under 60 s. No `If-None-Match`: the backend charges a 304 like a 200. A failed
  re-read keeps a name already on screen. An id that cannot be named is a skipped tile, never a skipped grid. No
  taxonomy-walk fallback is kept: the route is on backend main, and an older backend answers 404, i.e. no tiles.
- Block cap: the app renders the first **20** blocks in display order and ignores the rest. The backend serves up to
  200 live blocks per placement; the tighter app cap keeps a CMS mistake from turning Home into hundreds of sections.
- Refresh: re-read when Home is shown or the app returns to the foreground once the last success is older than 60 s
  (`max-age=60`), on pull-to-refresh at any age, and after a failure with backoff 10 s → 20 s → 40 s → 60 s (cap),
  never sooner than a 429's `Retry-After`, itself capped at 120 s as everywhere in the app (`RetryPolicy`). A pull
  inside that window sends nothing and shows the app's transient toast ("Please wait a moment and try again."). A failed or empty read shows
  nothing — the catalogue sections stand on their own, no replacement banner; a stale copy stays on screen while
  re-read and survives a failed re-read.
- Backend dependency: the `channel` parameter exists from backend PR #96 (multichannel content). An older backend
  answers 400 to any query parameter on this route; the app then shows no published blocks (no error surface). **The
  app must not ship before #96 is deployed.**

## Model change log

### 2026-08-30 — `Order.payment`
**Why:** the order-confirmation screen must tell the customer how they paid.
`Order` carried no payment field, and the checkout session is cleared before
navigation, so there was no honest source — the alternative was inventing one.
**Change:** `Order` gains `payment: PaymentMethodKind?` (nullable, defaulted,
so no existing call site breaks). `OrderRepository.placeOrder` accepts it and
`MockCheckoutRepository` passes `OrderRequest.payment` straight through.
**Backend impact:** `POST /orders/v1/orders` already receives `payment`; the
order payload it returns must echo it back as `payment: "COD" | "UPI" | "CARD"`.

## Later phases (checkout — do not build yet)

- `POST /carts/v1/validate` — revalidate lines (price drift, stock) before checkout.
- Address CRUD + geocode; delivery slots; payment intent/confirm; order status stream.
- FAQ/help content: `GET /support/v1/faqs` (HelpScreen currently reads seed data
  through MockCatalog — last remaining UI→mock coupling, tracked).
