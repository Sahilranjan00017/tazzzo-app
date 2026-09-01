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
