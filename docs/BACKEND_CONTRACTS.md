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
- Product ids: the platform grammar `TZP-[A-Za-z0-9-]{1,40}` (backend `ContentBlock.PRODUCT_ID`, CMS, storefront),
  numeric ids included. The app validates rail ids, `product:` links and the `GET /v1/products/{id}` path with exactly
  this grammar; an id outside it is dropped from a rail, leaves the banner untappable, and never reaches the wire. The
  grammar admits no `/`, `.`, `%`, `?` or whitespace, so an id is always ONE unescaped path segment. (Cart SKUs are a
  separate grammar, `TZP-[0-9]{1,18}`, matching the backend cart.)
- Grids: up to 12 node ids at ANY level (`TZS` / `TZC` / `TZG` / `TZV`). The public API has no "node by id" read, so a
  name is found by walking down from `GET /v1/categories` through `GET /v1/categories/{id}/children`, level by level,
  only as deep as needed, sequentially, at most 12 children reads per resolution (served from the 300 s taxonomy cache
  when fresh). Today's taxonomy (7 TZS, ~50 TZC, ~108 TZG) means TZS and TZC ids always resolve; a TZG/TZV id resolves
  only if found within the budget. The first failed read (a 429 above all) ends the walk without spending the rest of
  the budget; an id left unnamed is not walked for again for 5 min (1 min when a read failed), so Home re-reads do not
  repeat a futile walk. An id that cannot be named is a skipped tile, never a skipped grid. **Backend ask:**
  names in the grid block (or a node-by-id read) would remove this walk and its admission cost.
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
