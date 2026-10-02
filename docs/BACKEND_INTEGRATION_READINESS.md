# Backend Integration Readiness Report

**Status:** UI/UX Final Gate PASSED · UI FROZEN · Production readiness NOT passed.
**Purpose:** the complete frontend→backend contract surface, so integration can
begin without inventing behaviour. Nothing here is implemented yet.

Sources of truth: the repository interfaces in `data/repository/` and the models
in `data/model/`. Where the app currently fakes something, this report says so.

> **This document was adversarially fact-checked against the source on
> 2026-08-31 and corrected in 12 places.** Claims below reflect what the code
> actually does, not what it was intended to do. `docs/BACKEND_CONTRACTS.md` is
> an earlier, narrower note (6 endpoints); **where the two disagree, this
> document wins** and that one should be treated as superseded.

---

## 1. Every frontend contract currently documented

Six interfaces, **19 methods**, resolved through `ServiceLocator` — plus two
non-repository seams and, as §1a/§1b record, **four UI call sites that currently
bypass the repositories entirely** and one data surface with no interface at all.

| # | Interface | Methods |
|---|---|---|
| 1 | `CatalogRepository` | `getCategories()`, `getProduct(id)`, `getBanners()`, `getBestsellers()`, `getProducts(categoryId, subcategoryId?)`, `search(query)` |
| 2 | `AuthRepository` | `requestOtp(phone)`, `verifyOtp(phone, otp)` |
| 3 | `AddressRepository` | `getAddresses()`, `addAddress(label, line1, line2, pincode)` |
| 4 | `CheckoutRepository` | `getSlots(addressId)`, `getPaymentMethods()`, `validateCart(lines)`, `placeOrder(request)` |
| 5 | `OrderRepository` | `placeOrder(lines, bill, address, payment)`, `getOrders()` |
| 6 | `CoinRepository` | `getBalance()`, `getLedger()`, `credit(amount, title)` |

Plus two non-repository seams:
- `ProductImageLoader` (image bytes for `Product.imageUrl`) — provided at the app
  root via `ProvideProductImageLoader`, **not** through `ServiceLocator`
- `AnalyticsSink` (13 funnel events) — installed via `Analytics.sink`, **not**
  through `ServiceLocator`

### 🔴 1a. Four UI call sites bypass the repositories (pre-integration work)
The swap is **not** purely a `ServiceLocator` change. These read the mock data
object directly and will keep rendering mock content after the swap:

| File | What it reads directly |
|---|---|
| `ui/home/CategoryDetailScreen.kt:65` | `MockCatalog.categories` (screen header/taxonomy) |
| `ui/common/Components.kt:564` | `MockCatalog.categories` (`categoryTintOf` — every card tint) |
| `ui/onboarding/OnboardingScreen.kt:145` | `MockCatalog.categories` (login photo wall) |
| `ui/home/HelpScreen.kt:81` | `MockCatalog.faqs` |

These must be refactored onto `CatalogRepository.getCategories()` **before** a
remote catalogue is wired in.

### 🔴 1b. A seventh data surface has no interface at all
**FAQ/help content** (`FaqItem{question, answer}`) is read straight from
`MockCatalog.faqs`. It needs a `SupportRepository` + `GET /support/v1/faqs`.

**Integration rule:** keep the mocks — they are the offline/demo fallback and the
test doubles. Do not delete them when `Remote*` lands.

---

## 2. Backend endpoint / data each contract requires

| Frontend method | Endpoint | Notes |
|---|---|---|
| `getCategories()` | `GET /catalog/v1/categories` | Must include `subcategories[]` and the `group` used for section headers |
| `getProduct(id)` | `GET /catalog/v1/products/{id}` | Required — the PDP currently has no other honest source |
| `getProducts(cat, sub?)` | `GET /catalog/v1/categories/{id}/products?subcategory=&sort=&in_stock=&brand=` | Filters should move server-side as the catalogue grows |
| `search(query)` | `GET /catalog/v1/search?q=&sort=&in_stock=&brand=&limit=&offset=` | See §2a |
| `getBestsellers()` | `GET /catalog/v1/bestsellers` | Must be **real** ranking. If unavailable, return the same list the app labels "Bestsellers" — do not label it "Trending" |
| `getBanners()` | `GET /catalog/v1/banners` or remote config | Each banner needs a real destination; the app only makes tappable what exists |
| `requestOtp(phone)` | `POST /v1/auth/otp/request` `{phone}` (E.164 `+91…`) → `202 {challengeId, expiresInSeconds, resendAfterSeconds}` | **Implemented (PR-03A).** Rate-limited server-side (`429 OTP_RATE_LIMITED`, `retryAfterSeconds`) |
| `verifyOtp(challengeId, otp)` | `POST /v1/auth/otp/verify` `{challengeId, otp}` (6-digit string) → `200 {grantId}`, then `POST /v1/auth/session {grantId}` → `{customerId, accessToken, accessTokenExpiresIn, refreshToken}` | **Implemented (PR-03A).** Wrong/expired OTP are `400 OTP_INVALID` / `OTP_EXPIRED`. See §4 |
| refresh / logout | `POST /v1/auth/refresh {refreshToken}` (rotates), `POST /v1/auth/logout` (Bearer, 204) | **Implemented (PR-03A)** |
| `getAddresses()` | `GET /addresses/v1/addresses` | Per authenticated user |
| `addAddress(...)` | `POST /addresses/v1/addresses` | Server returns `isServiceable` — the client must not infer it |
| `getSlots(addressId)` | `GET /serviceability/v1/slots?addressId=` | **Empty array = not serviceable.** That is the contract the UI already implements |
| `getPaymentMethods()` | `GET /payments/v1/methods` | Server decides which are `enabled`; the app renders disabled ones with the server's `note` |
| `validateCart(lines)` | `POST /carts/v1/validate` | See §5 |
| `placeOrder(request)` | `POST /orders/v1/orders` | See §7 |
| `getOrders()` | `GET /orders/v1/orders` | Per authenticated user |
| `getBalance()` / `getLedger()` | `GET /coins/v1/balance`, `GET /coins/v1/ledger` | See §D5 caveat |
| `credit(amount, title)` | *(none — remove client-side)* | **Coins must be credited server-side on order completion.** The client currently credits locally; that is demo behaviour and must not ship |

### 2a. Search service behaviour (the app-side engine IS the spec)
`data/search/SearchEngine.kt` defines expected relevance. The service should
match or beat it:
- exact token match on name/brand → strongest
- Hindi/colloquial synonyms (`aata`→atta, `doodh`→milk, `sabun`→soap, `magi`→Maggi) ranked **above** prefix matches
- prefix match, min 3 chars
- fuzzy: edit distance 1 for 4–5 char tokens, 2 for ≥6, with a first-letter anchor for short tokens
- unit/quantity tokens (`5kg`, `500g`) treated as optional qualifiers, not requirements
- **weak-token layer** (undocumented before): category + subcategory names and
  the product's unit string also match, at lower weight — so "dairy" or "snacks"
  returns products by taxonomy. A service without this returns nothing for those.
- **tie-break order**: score desc → `"Bestseller" in tags` desc → name asc.
- every query word must match something, or the product is excluded.

Optional: returning `appliedQuery` would let the UI show "showing results for
atta" — **no such field or UI affordance exists today**; it is a request, not a
current consumer.

---

## 3. Request/response models required

Serialize to these exact shapes (`kotlinx.serialization`, models in `data/model/`).

**Product** — the most important payload:
```json
{
  "id": "p22", "name": "Chicken Curry Cut", "brand": "Fresh Basket",
  "unit": "500 g", "price": 159, "mrp": 190,
  "categoryId": "meat", "subcategoryId": "chicken-s",
  "rating": 0.0, "ratingCount": 0,
  "tags": ["Bestseller"], "highlights": ["Antibiotic-residue free"],
  "emoji": "🍗",
  "availability": { "status": "IN_STOCK|LOW_STOCK|OUT_OF_STOCK|NOT_SERVICEABLE", "remaining": 3 },
  "maxOrderQuantity": 10,
  "imageUrl": "https://cdn.tazzzo.in/p/p22.webp"
}
```
- **`emoji` is REQUIRED and has no default** (`Models.kt:41`). Omitting it fails
  construction. It drives the placeholder rendering until real photography ships.
- `imageUrl` and `Order.payment` are **nullable**; `rating`/`ratingCount` are not.
- **`etaMinutes` is dead** — declared on `Product` and `DeliverySlot`, read
  nowhere. Every delivery string comes from serviceability via `DeliveryCopy`
  (§6). Do not build per-SKU ETA plumbing; nothing consumes it.
- **Money is integer PAISE everywhere (PR-04B).** The `Money` value class, serialized as a bare `Long` of paise whose JSON key NAMES the unit (`pricePaise: 4950` = ₹49.50; the Kotlin property stays `price: Money`). Persisted entries are versioned the same way (`tazzzo.cart.v2` → `priceAtSavePaise`, `tazzzo.membership.v2` → `cumulativeSpendPaise`). No floats, no strings, no rupee `Int`. Server-supplied amounts are authoritative and are never recomputed; proportional client-side amounts use `floor(paise × percent / 100)` in integer arithmetic. Customer-facing amounts are never negative.
- `rating`/`ratingCount`: send `0` until a real ratings system exists. The UI
  renders nothing at `ratingCount <= 0` by design — do **not** send placeholders.

### 🔴 3a. The models are NOT serialization-ready yet
`grep -rn "Serializable" data/model/` returns **nothing**. No domain model carries
`@Serializable` today. Two shapes also cannot be produced by default kotlinx
polymorphism and need **custom serializers**:
- `Availability` — a sealed interface; the flat `{status, remaining}` above is a
  hand-written mapping, not the default `"type"`-discriminator encoding.
- `CartIssue` — same problem.

**RESOLVED 2026-09-01 (pre-backend Wave 3).** Every model above now carries
`@Serializable`, and both sealed types have hand-written serializers in
`data/model/WireFormat.kt` producing exactly the flat shapes documented here.
20 round-trip and wire-shape tests pin them (`SerializationTest`), including a
round trip of the entire fixture catalogue. The exact encodings are:

```
Availability   {"status":"IN_STOCK"}
               {"status":"LOW_STOCK","remaining":3}      // remaining REQUIRED
               {"status":"OUT_OF_STOCK"}
               {"status":"NOT_SERVICEABLE"}

CartIssue      {"type":"OUT_OF_STOCK","productId":"p23","productName":"Rohu Fish"}
               {"type":"QUANTITY_REDUCED","productId":"p2","productName":"Tomato",
                "requested":5,"available":3}
               {"type":"PRICE_CHANGED","productId":"p8","productName":"Milk",
                "oldPricePaise":2900,"newPricePaise":3200}
```

The `"type"` envelope key and the per-issue field names are specified by
`WireFormat.kt`; §5 named the codes but not the encoding. Unknown status/type
strings and missing required fields are hard deserialization failures, on
purpose: a `LOW_STOCK` with no count, or a `PRICE_CHANGED` with no prices, must
never be degraded into something purchasable or something silently accepted.

**`Product.emoji` CHANGED 2026-09-01:** it now defaults to `""` and is therefore
OPTIONAL on the wire. It was required with no default, which meant an otherwise
valid payload that omitted it failed to construct — unacceptable for a field
that is an acknowledged temporary placeholder due to disappear when `imageUrl`
is populated. The renderer treats a blank glyph as "no glyph". The server may
still always send it.

`PlaceOrderResult` is deliberately NOT serializable: it is the client's reading
of an attempt (including transport failures that have no body), not a wire type.
Mapping the response envelope onto it belongs to the repository implementation.

**Other models:** `Category{id,name,emoji,tint,group,subcategories[]}`,
`PromoBanner{id,title,subtitle,emoji,dark}` (note `dark` is a presentation flag
the server would own), `CartValidation{issues[]}` (the validate response
envelope), `FaqItem{question,answer}`,
`Subcategory{id,name,emoji}`, `CartLine{product,quantity}`,
`BillSummary{itemTotal,itemMrpTotal,deliveryFee,handlingCharge,coinsEarned,grandTotal}`,
`Order{id,lines[],bill,status,placedAtLabel,address,payment}`,
`OrderStatus ∈ {PLACED,PACKED,ON_THE_WAY,DELIVERED}`,
`Address{id,label,line1,line2,pincode,isServiceable}`,
`DeliverySlot{id,label,available,etaMinutes?}`,
`PaymentMethod{kind,label,enabled,note?}`,
`CoinTransaction{id,title,amount,dateLabel}`,
`UserProfile{name,phone,isGuest,coinBalance,address}`.

**Only these four order statuses exist.** The UI has no cancelled/failed/refunded
rendering. Adding them is a model + UI change, not a payload change.

---

## 4. Authentication requirements

- **Implemented (PR-03A):** OTP request → verify (→ one-time `grantId`) → session
  (→ **opaque** access token + rotating refresh token). No profile is fetched yet;
  the signed-in profile is neutral.
- Access tokens are opaque: expiry comes from `accessTokenExpiresIn`, never from
  parsing. Refresh tokens rotate on every refresh with **no grace window and no
  family revocation**: a reused/old token is `401`, so a refresh response the
  client never received means the customer must sign in again. A single refresh
  is in flight at a time; only a `401`/`400` from refresh ends the session —
  offline/timeout/5xx keep it.
- Logout revokes only the current session and needs a valid access token; the
  client wipes local credentials regardless of the result.
- **Guest mode must survive.** The app distinguishes guest from authenticated
  (the secure session — `AppState.isAuthenticated` — is the authority;
  `UserProfile.isGuest` follows it) and browsing must never require auth. Only checkout should demand identity.
- Token refresh on `401`; the error model already has an `Unauthorized` kind that
  maps to "Please log in again".

### ✅ Resolved in PR-03A — secure token storage (history below)
Tokens live in `data/auth/SecureTokenStore`: **Android Keystore** (AES-256-GCM,
non-exportable key, private prefs file excluded from backup) and **iOS Keychain**
(generic password, `AfterFirstUnlockThisDeviceOnly`). Nothing is stored in
multiplatform-settings except the non-secret `authInstallMarker`, which clears a
stale Keychain item after a reinstall.

### (historical) SECURITY BLOCKER — must be fixed before real tokens exist
`data/local/PersistentStore.kt` persists via **plain** `NSUserDefaults` /
`SharedPreferences`. Keys today: cart, session profile, addresses, recent
searches, onboarding flags. **No credentials or tokens are stored — deliberately.**

Before real auth ships, tokens must go to an `expect/actual` secure store:
- **iOS:** Keychain (`kSecClassGenericPassword`, `WhenUnlockedThisDeviceOnly`)
- **Android:** `EncryptedSharedPreferences` (or DataStore + Tink)

Do **not** add a token field to the existing `PersistentStore`.

---

## 5. Stock and price revalidation requirements

`POST /carts/v1/validate` must return, per line, any of:
| Issue | Meaning | UI behaviour (already built) |
|---|---|---|
| `OUT_OF_STOCK` | no longer purchasable | "Remove" action, placement blocked |
| `QUANTITY_REDUCED` | `available < requested` | "Adjust" to available |
| `PRICE_CHANGED` | `newPrice != priceAtAdd` | disclosed, and the line is **removed** — the customer re-adds it at the new price (action: "Refresh", `CheckoutScreen.kt:139-145`). The client never silently accepts a new price mid-checkout. |

Rules the client already enforces and the server must mirror:
- Revalidation runs **on checkout entry and again inside placeOrder**.
- An invalid cart **cannot** be placed (`PlaceOrderResult.Rejected`).
- Cart **restore** after app kill is the one path where current price wins
  outright: it rebuilds from the live `Product` and discloses the change
  (`CartRestore.kt:52-55`). Note the notice names at most 2 items per category
  and shows removed-and-since-deleted products as "an item".
- `maxOrderQuantity` and `LowStock.remaining` both cap quantity; the cart refuses
  over-limit adds at the model layer, not just in the UI.

**The customer is never silently charged a stale price.** Preserve this.

---

## 6. Serviceability requirements

`GET /serviceability/v1/promise?pincode=|lat,lng` →
```json
{ "status": "SERVICEABLE|NOT_SERVICEABLE", "etaMinutes": 42, "window": {"min":45,"max":75} }
```
Maps to `DeliveryPromise` (`Unknown | Estimate | Window | NotServiceable`) which
drives **every delivery string in the app** through `DeliveryCopy`.

- Until this exists the app shows neutral **"Fast delivery"** and renders *nothing*
  where a specific time would go. That is deliberate and must not be "fixed" with
  a hard-coded value.
- Address serviceability is server-decided; slots endpoint returning empty is the
  canonical "we don't deliver here yet" signal.

---

## 7. Order & idempotency requirements

**This is the highest-risk contract.**

- The client mints **one idempotency key per checkout session** (`CheckoutSession.idempotencyKey`).
  Every attempt — including retries after failure or timeout — sends the same key.
- The server **must** treat the key as the uniqueness constraint and return the
  **same order** for a repeated key, with a `replayed: true` marker.
- Client covers three outcomes; the server must be able to produce all three:
  `Placed(order, replayed)` · `Rejected(validation)` · `Failed(reason, retryable)`.
- `OrderRequest` carries: `idempotencyKey, lines[], bill, addressId, addressText, slotId, payment`.
- The server must **recompute the bill** and reject on mismatch. Client totals are
  a display of `BillCalculator`, never an authority on what to charge.
- Coins are credited **server-side** on completion (see §2 — remove `credit()` from the client path).

- The idempotency key is `"chk-" + Random.nextLong(1e11..1e12)` — a 12-digit
  `kotlin.random.Random` value, **not a UUID and not cryptographically seeded**.
  Stated explicitly because the server is asked to make it a uniqueness
  constraint; consider server-side collision handling or moving to a UUID.
- **Client side effects are gated on `replayed == false`** (fixed 2026-08-31 —
  a replayed placement previously credited coins a second time). Any future
  side effect added on success must respect the same guard.

Verified by 3 unit tests: duplicate key replays, failure-then-retry places
exactly once, invalid cart is rejected at placement.

---

## 8. Payment requirements

- Today: **COD only**, enabled by the repository. UPI and Card are rendered
  disabled with a server-supplied "Coming soon" note. Nothing fake is processed.
- For real payments: gateway selection is a **business decision** (Razorpay/UPI
  intent/cards). The app needs `paymentIntentId` + a confirm/callback step; the
  checkout state machine has room for it between Payment and Review.
- **No card data may touch the app.** Use the gateway SDK/redirect; never collect
  PAN/CVV in Compose fields.
- Payment failure must map to `PlaceOrderResult.Failed(retryable=true)`, which the
  UI already renders with "Your cart is untouched." and a same-key retry.

---

## 9. Image delivery requirements

`Product.imageUrl` + `ProductImageLoader` are the only seam. Backend supplies:
- **CDN URLs**, HTTPS, long cache TTL, stable per SKU.
- Ideally width-parameterised (`?w=320`) — the app requests a card at ~158dp and a
  PDP hero at full width.
- **Landscape-ish packshots (~1.4–1.5) on a neutral/white ground.** The real
  containers are 1.52 (card) and 1.4 (PDP hero) — square art is *safe* but will
  letterbox visibly. The app uses
  `ContentScale.Fit` and never crops (cropping loses the brand), inside a fixed
  aspect container, so mixed aspect ratios are safe but wildly tall images waste space.
- Missing/failed image is a **first-class state** and already designed — the app
  never breaks on a 404.

App-side work still required (not backend): pick a Compose Multiplatform image
library, implement `ProductImageLoader` once, provide it at the app root.

🔴 The current emoji glyph is a **temporary development placeholder**. It must
never be presented as production photography, and must not be replaced with
fabricated/generated product imagery.

---

## 10. Error, timeout and offline behaviour

The client models five error kinds — the transport layer must map onto them:

| Kind | Trigger | Customer copy |
|---|---|---|
| `Network` | no connectivity, DNS/connect failure | "No internet connection" |
| `Timeout` | request timeout | "That took too long" |
| `Server` | 5xx | "Something went wrong at our end" |
| `Unauthorized` | 401/403 | "Please log in again" |
| `Unknown` | anything else | "Something went wrong" |

Requirements:
- The HTTP client must **throw**, not return empty lists — an empty list means
  "no results", which is a different screen.
- 🔴 **Four screens are NOT yet on `StateHost`/`rememberLoad`** and use bare
  `LaunchedEffect` with no try/catch. A thrown exception there kills the
  coroutine with no error state and no retry. **Migrate before wiring any
  `Remote*`:** `OrdersScreen.kt:75`, `CoinsScreen.kt:56`,
  `OrderAgainTabContent.kt:53`, `SearchScreen.kt:89/91`.
  (`getOrders()` is listed as "integrate immediately" in §12 — this migration is
  its prerequisite.)
- Sensible timeouts (connect ~10s, read ~20s) and retry policy for idempotent GETs.
- **Never surface status codes or stack traces to the customer.**
- Offline: cached last-good catalogue is desirable; the `OfflineBanner` component
  exists and is unused pending a connectivity observer.

---

## 11. Security requirements

1. **Tokens in secure storage only** — Keychain / EncryptedSharedPreferences (§4). Blocking.
1b. **Plaintext PII already on disk today** — `SavedSession` persists `phone` and
   `address`; `SavedAddress` persists `line1/line2/pincode`, all in plain
   NSUserDefaults/SharedPreferences (world-readable on rooted/jailbroken devices,
   and captured by OS backups). The secure-storage work is therefore **wider than
   tokens** — it should cover the session profile and address book too.
2. **HTTPS only**, certificate validation on; consider pinning for auth/payments.
3. **No card data in the app** (§8).
4. **Analytics payload inventory** (verified, complete): product ids, order ids,
   checkout step names, payment-method *kind*, `replayed`/`retryable` booleans —
   **and the raw search query text** (`AppState.kt:92`). That free-text field is
   the one most likely to trip a PII review; decide whether to hash, truncate or
   drop it. Never send phone, address or payment details.
5. **Server-authoritative money** — bill recomputed server-side; client totals are display only.
6. **Server-authoritative stock** — client caps are UX, not enforcement.
7. Logs must not contain tokens/OTPs. The dev `DevLogSink` and the
   `TAZZZO_DEMO_*` flags must be disabled in release builds.
8. Rate-limit OTP request/verify server-side.

---

## 12. What can be integrated immediately (no product decision needed)

1. **HTTP client + serialization** — Ktor, `@Serializable` models, error mapping to `LoadError`.
2. **Catalogue reads** — categories, product-by-id, category listings, bestsellers. Pure reads, models already match.
3. **Search** — swap to the service; app-side engine stays as offline fallback and as the behavioural spec.
4. **Addresses** — CRUD is well-defined and unblocked.
5. **Order history** — `getOrders()` is a straight read.
6. **Product images** — as soon as any real URLs exist; the loader is a single implementation.
7. **Secure token storage** — build the `expect/actual` now, before auth lands.
8. **Analytics adapter** — once a vendor is chosen; the boundary is ready.

Only items **2–5** are a one-line `ServiceLocator` swap plus a `Remote*` class —
and only after the §1a bypasses and §10 `StateHost` migrations are done.
Item 1 (HTTP client) has no seam; item 6 (images) is the `ProvideProductImageLoader`
composition local; item 7 (secure storage) is a new `expect/actual`; item 8
(analytics) is `Analytics.sink`. The existing **41 tests must keep passing.**

## 13. Blocked by a product/business decision (yours)

| Blocker | Decision needed |
|---|---|
| **D4 — delivery promise** | What may we promise per area, once serviceability exists? App shows neutral copy until you rule. |
| **D5 — coin economics** | Earn rate, coin value, redemption cap, expiry. Current values are dev config; redemption UI is deliberately hidden. |
| **D6 — founder claims** | "SAVE 8–20%" and "India's first Voice Commerce" need substantiation or amendment. Both are config-sourced, so withdrawal is a one-line change. |
| **Payment provider** | Gateway choice gates all payment work. |
| **Catalogue source** | Hand-curated vs supplier feed vs crawler — affects taxonomy depth and image rights. |
| **Product photography** | Asset dependency; the ceiling on card/PDP visual quality. |
| **Analytics/crash vendor** | Unselected by design. |
| **Terms & Privacy pages** | Legal content. Link affordance removed until real pages exist — likely a launch blocker. |

## 14. Blocked because the backend does not exist yet

Auth service · catalogue service · stock/inventory · serviceability & slots ·
orders service (incl. idempotency ledger) · payments · coins ledger ·
image CDN · support/FAQ content · push notifications · live order tracking.

Each has a documented consumer-side contract above. **None is invented in the app** —
where the backend is absent, the app either uses a labelled mock or renders nothing.

---

## Non-negotiables carried into integration

- Keep the `Mock*` implementations. They are the offline fallback, the demo mode
  and the test doubles. Do not delete them when `Remote*` lands.
- **41/41 tests stay green.** Integration changes must preserve behaviour, not
  replace working demo behaviour blindly.
- The honest-content rule survives integration: no fabricated ratings, times,
  counts, offers or SLAs — if the server can't back it, the app doesn't say it.
- UI is **frozen**. Integration must not require redesign; if it does, that is a
  contract problem to raise, not a screen to redraw.


---

## 6. Catalogue + serviceability: the RUNNING contract (PR-04A)

The app-side data foundation is implemented in `data/catalog/` (not yet wired to
any screen — PR-04C does that). It targets what the backend ACTUALLY sends on
`origin/main`, which is thinner than its OpenAPI:

| Call | Endpoint | Notes |
|---|---|---|
| categories | `GET /v1/categories` | 7 super categories `{id,name}`; ETag + 304; `max-age=300` |
| children | `GET /v1/categories/{id}/children` | immediate children; empty categories hidden |
| product list | `GET /v1/categories/{id}/products?page_size&cursor&pin` | opaque cursor bound to node, page size and **PIN**; `404` = nothing to list |
| PDP | `GET /v1/products/{id}?pin` | flat card fields + `gallery[]` + `attributes[]` |
| serviceability | `GET /v1/serviceability?pin=` | `serviceable` never null; ETA not populated |

Rules the client follows: anonymous (no token), `X-Tazzzo-Installation-Id` on every
call, never `lat`/`lng`/`release`, money is integer paise, `buyable` is the only
purchase signal, `stockState` may be `UNKNOWN`. Never populated today (treat as
absent): `brandName`, `packSize`, `unit`, `rating`, `ratingCount`, `badges`,
`description`, `highlights`, `variants`, `legal`, ETA. There is no search, banners,
bestsellers, deals, counts, or category imagery/ordering endpoint.

**BACKEND CONTRACT / ENVIRONMENT REQUEST** — a non-production environment where the
five endpoints can be exercised with representative data: taxonomy, products, prices,
inventory, at least one service area including PIN 560047, thumbnail/gallery media
configuration, cursor signing key, `tazzzo.freshness.enabled`, a configured
consumer rate-limit mode, and a provisioned gateway host. Not blocking for
PR-04A's automated work; blocking for real-environment sign-off before launch.


### 6a. What is wired to the real catalogue (PR-04C)

REMOTE is the production mode (a debug build is REMOTE too unless `taz_mock_catalog` / the demo flags ask for MOCK
explicitly). In REMOTE: Categories (TZS sections → TZC categories, children loaded lazily) → PLP (cursor paging, TZG
subcategory chips) → PDP, plus the delivery-PIN banner (launch PIN 560047, editable). **Hidden, because the backend has
no source:** Search, Deals, Order Again, Bestsellers, banners/home rails, category counts, server sort. **Not rendered:**
rating, description, highlights, variants, legal, ETA (unless sent), brand name, pack size. Images use a neutral
placeholder (URLs are kept in state; the loader is a later PR). **No real product can enter the local/mock cart**
(`cartIntegration = false`): the purchase action is a disabled, neutral label. `ServiceLocator.catalog` (the mock) throws
in REMOTE mode instead of answering, so nothing can silently mix mock and real data.
