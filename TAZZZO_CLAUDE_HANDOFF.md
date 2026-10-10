# TAZZZO — CLAUDE CODE SESSION HANDOFF

**Generated:** 2026-09-01 · **Verified against source at generation time.**
Every factual claim below was checked against the repository. Where something is
unverified, unknown, or undecided, it says so explicitly.

---

## 1. PROJECT IDENTITY

**Tazzzo** — an India-first quick-commerce grocery shopping application.
Positioning: *"Better Value. Easier Shopping."* Supporting promise:
*"Smart Groceries. Better Prices."*

> ### ⚠️ THIS IS NOT A SERVICENOW PROJECT
> There is **no ServiceNow component, integration, instance, scoped app, or
> dependency** anywhere in this project. Nothing here relates to ServiceNow.
> If a future instruction references ServiceNow, CAT-ID, GTIN reconciliation,
> C-3/F-5/WP-0/D-1a/SRC-1, or a "Tazzzo catalogue service", it belongs to a
> **different workstream** that is not present in this repository. Verify before
> acting; do not fabricate work for it.

**Platform architecture:** Kotlin Multiplatform + Compose Multiplatform.
One shared codebase renders the entire UI on both platforms.

| Source set | Contents |
|---|---|
| `commonMain` | All screens, state, data layer, design system (the overwhelming majority) |
| `androidMain` | `MainActivity`, `PlatformBack.android.kt`, `DemoTour.android.kt` (48 lines total) |
| `iosMain` | `MainViewController`, `PlatformBack.ios.kt`, `DemoTour.ios.kt` (30 lines total) |

**Verified size:** 56 Kotlin files, 11,767 lines across all source sets.

**Platform status:**
- **Android** — builds and runs. Verified on a Pixel 7 emulator (API 35, arm64)
  and at 480×854 @ 240dpi. Launcher icon + branded system splash present.
- **iOS** — builds and runs on the iPhone 17 Pro simulator (iOS 26.4).
- **Neither has been tested on a physical device.**

**Repository layout (relevant paths):**
```
tazzzo/
├── BLOCKERS.md                     P0/P1/P2 blocker list (source of truth)
├── PRODUCTION_READINESS.md         feature × verification matrix + dated log
├── DOCUMENTATION.md                general project documentation
├── README.md                       quick start
├── docs/
│   ├── DESIGN_SPEC.md              design authority (FROZEN)
│   ├── BACKEND_CONTRACTS.md        earlier, narrower contract note
│   ├── BACKEND_INTEGRATION_READINESS.md   ← readiness source of truth
│   ├── IMAGE_ATTRIBUTIONS.md       photo licences
│   ├── search-evidence.log         search relevance evidence
│   └── screenshots/                device evidence (see §4)
├── composeApp/src/{commonMain,androidMain,iosMain,commonTest}/
├── iosApp/                         Xcode wrapper (project.yml → XcodeGen)
└── gradle/libs.versions.toml       version catalog
```

**Not under version control.** `git status` reports "not a git repository", and
there are no `.git` directories anywhere under the user's home. Initialising git
is a reasonable early step but has **not** been done and was not authorised.

**Build requirements:** JDK 21 is mandatory (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)`).
The machine's default JDK is 27-ea, which Gradle 8.14 rejects.

---

## 2. CURRENT PRODUCT STATE

> **2026-10-10 — product-closure release path (REMOTE = every release build).** The table below is the historical MOCK
> (debug/demo) inventory. The RELEASE app now routes through `RouteTable.kt` (`routeImpl`): REMOTE never renders a MOCK
> screen (`ReleaseConfigurationTest`). Day-1 REMOTE surfaces: launch/onboarding/login (OTP; back on the OTP step returns
> to the phone step; Terms/Privacy links), Home, Shop, PLP, PDP, **Search** (`/v1/search`, `ui/catalog/RemoteSearchScreen.kt`
> + `data/catalog/ProductSearch.kt`), Cart, Addresses, Checkout (COD; **Place order enabled** — the debug opt-in
> `-Ptazzzo.debugRealOrdering` was removed), order confirmation, **Orders history + detail** (`OrderStore.history`,
> `ui/order/RemoteOrderUi.kt`), **Profile** (display name from `/v1/customer/profile`), **Help & support** (FAQs, contacts,
> support requests: `ui/support/HelpScreens.kt`, `data/support/Support.kt`), **Legal** (`Screen.Legal`, plain text), About.
> Coins / Genie / Club / shopping list / Deals / Order again remain MOCK-only. The transient toast is hosted at the App root
> (`ui/common/TransientMessageToast.kt`). iOS edge-swipe back goes through Compose Multiplatform's `BackHandler`
> (`PlatformBack.ios.kt`). None of this has been run on a device yet (see PRODUCTION_READINESS.md verification log).

All screens below exist, compile, and have been run on device.

| Screen | File | State |
|---|---|---|
| Splash | `ui/splash/SplashScreen.kt` | Choreographed logo → tagline → progress track. Routes returning users straight to Home (900ms) vs first-run (1800ms). |
| Login / guest | `ui/onboarding/OnboardingScreen.kt` | Photo-wall entry, logo badge overlapping imagery, value strip, +91 phone → OTP with 30s resend, **"Skip for now" guest path preserved**. Photo band adapts on short screens so the CTA stays above the fold. |
| Secondary login | `ui/onboarding/LoginScreen.kt` | Reachable from Account; same input language. |
| Onboarding tour | `ui/guided/GuidedJourney.kt` | 3-step spotlight coach marks (dim + `BlendMode.Clear` cutout + bobbing pointer) anchored to real elements via `Modifier.guidedTarget`. Shown once (`tourSeen` flag). |
| Home | `ui/home/HomeTabContent.kt` | Fixed brand bar (logo, coins, mic, avatar; location line) + search, then category grid → offers pager → Bestsellers → Order-again → rails → voice teaser last. |
| Categories | `ui/home/CategoriesTabContent.kt` | Grouped 4-wide photographic tiles. |
| Category listing | `ui/home/CategoryDetailScreen.kt` | Two-pane: subcategory sidebar + 2-col `LazyVerticalGrid` with stable keys, filter/sort bar, honest item count. |
| Search | `ui/home/SearchScreen.kt` | Debounced, recent searches, popular chips, filters, no-results recovery. |
| Product detail | `ui/home/ProductDetailScreen.kt` | Hero image well, identity block, highlight/info cards, similar rail, **pinned purchase footer**. |
| Product cards | `ui/common/Components.kt` | Adaptive width; normal / low-stock / out-of-stock / at-limit / discount states. |
| Cart | `ui/home/CartScreen.kt` | Rows with steppers, savings strip, full bill breakdown, sticky "To pay" + checkout CTA, designed empty state. |
| Checkout | `ui/checkout/CheckoutScreen.kt` | 4-step flow — see §6. |
| Order confirmation | `ui/home/OrderSuccessScreen.kt` | Success mark, amount paid, items, placed-at, address, **Paid by**, coins earned. |
| Orders | `ui/home/OrdersScreen.kt` | Cards with status chip + 4-step progress rail, reorder → cart, empty state. |
| Order Again | `ui/home/OrderAgainTabContent.kt` | Past orders with per-line detail + reorder. |
| Account | `ui/home/AccountTabContent.kt` | Profile header (guest vs logged-in), quick stats, menu, logout. |
| Addresses | `ui/home/AddressesScreen.kt` | Saved addresses with serviceability chip. Adding happens in checkout. |
| Tazzzo Coins | `ui/home/CoinsScreen.kt` | Balance hero, how-it-works, ledger. **No redemption UI** (D5 unapproved). |
| Help | `ui/home/HelpScreen.kt` | FAQ search, "Help with an order" rows, WhatsApp contact, expandable FAQs. |
| About | `ui/home/AboutScreen.kt` | Brand, value props, voice card. |
| Voice UI | `ui/voice/VoiceSheet.kt` | "Coming soon" sheet with pulsing mic. **No voice functionality exists** — no capture, no recognition, no waitlist. Dismiss-only ("Got it"). |

**Loading / error / empty states** — `ui/common/States.kt` provides
`ProductCardSkeleton`, `ProductRailSkeleton`, `SkeletonBlock`, `EmptyState`,
`ErrorState` (with retry), and `StateHost` which renders
loading/empty/error/content for a `LoadHandle`. (`OfflineBanner` was deleted
2026-10-10 as dead code: per-screen failure states remain the honest signal.)

**Out-of-stock handling** — `Availability` is a first-class model concept
(`InStock` / `LowStock(remaining)` / `OutOfStock` / `NotServiceable`).
Enforcement is in `AppState.addToCart`, not only in the UI: it refuses
unpurchasable products and caps at `purchasableLimit` (min of `LowStock.remaining`
and `maxOrderQuantity`). Cards dim the image only — name and price stay legible —
and show an "Out of stock" chip in place of ADD.

**Small screens** — verified at 480×854 @ 240dpi on Android: login CTA above the
fold, category grid fits, no clipping.

---

## 3. DESIGN / UI FREEZE

### 🔒 UI IS FROZEN

**No redesign or visual experimentation during backend preparation or
integration.** The only permitted visual change is one required by a genuine
functional defect, and it must be documented before it is made.

**Authority:** `docs/DESIGN_SPEC.md` carries a FROZEN banner dated 2026-08-31.
It is the design source of truth. Read it before touching any UI file.

| Aspect | Decision |
|---|---|
| **Typeface** | **Poppins** (4 weights, ~630 KB), bundled in `composeResources/font/`. Chosen for two functional reasons: it shares the geometric-rounded skeleton of the Tazzzo wordmark, and it ships a Devanagari cut (Indian Type Foundry) so future Hindi copy renders in the same family. Applied once in `TazzzoTheme` via Material typography **and** `LocalTextStyle`, so bare `Text(fontSize=…)` calls inherit it. |
| **Colour** | `theme/Theme.kt`. Green `#00411C` = identity/primary action only; Orange `#C74018` = savings/offers **only**, never decoration; warm neutral ramp (`Cream #FAF9F6`, `Surface`, `SurfaceSunken`, `CardBorder`, `BorderStrong`) so photography and prices lead. Semantic Success/Warning/Danger separate from brand. `CoinGold` is for DARK grounds; `CoinInk #9D6E00` for light. |
| **Contrast** | Measured, not estimated — table in `DESIGN_SPEC.md`. Four tokens were corrected after audit (Orange was 3.61:1 and failed AA; TextTertiary 3.28; Success-on-SuccessSoft 4.46; CoinGold 2.19 on light). Disabled text is intentionally low and exempt under WCAG 1.4.3. |
| **Typography scale** | `theme/Tokens.kt` — display/h1/h2/title/body/caption/micro **plus commerce-specific tokens** for price, MRP, savings, product name, unit, button, nav label. Micro floor is 11sp. |
| **Spacing** | 4dp base scale (2·4·8·12·16·20·24·32·48), 16dp gutter, 96dp `cartBarClearance`. |
| **Radius / elevation / motion** | chip 8 · card 14 · tile 18 · sheet 22 (top-only) · `sheetAll` 22 (all corners) · pill 999. Elevation flat/raised 3/floating 8/overlay 14. Motion fast 150 / normal 300 / ambient 700 — explanatory only, never decorative. |
| **Icons** | `theme/TazIcons.kt` — one Material vector family. **Emoji are content** (a product's 🍅, a category tile) and **never UI**. Verified: zero emoji render as controls. Coins use a currency-neutral two-coin mark (a `$` glyph was wrong for a rupee product). |
| **Product images** | `ui/common/ProductImage.kt` — see §5. |
| **Touch targets / a11y** | 44dp minimum, 48dp for text links in the thumb zone. `PillButton(enabled, disabledHint)` carries real `disabled()` semantics + `stateDescription`. Content descriptions on mic, coins, steppers, back, search, nav tabs. |
| **Home hierarchy** | location → search → categories → offers → bestsellers → order-again → rails → voice **last**. Search is the visually dominant control. |
| **Checkout hierarchy** | 3-state step indicator (done ✓ / current / upcoming), "To pay ₹X" visible on **every** step, disabled CTAs state *why*. |

**Approved visual baseline:** the founder rated all major customer-facing screens
at the 4.8/5 target and accepted the Final Visual QA Gate as PASSED on 2026-08-31.

---

## 4. VISUAL QA STATUS

### Test count — verified from `build/test-results` at generation time
**41 tests, 0 failures.** Breakdown:

| Test class | Tests |
|---|---|
| `BillCalculatorTest` | 9 |
| `CheckoutRepositoryTest` | 9 |
| `CartRestoreTest` | 6 |
| `CartEnforcementTest` | 5 |
| `CheckoutSessionTest` | 5 |
| `PersistentStoreTest` | 5 |
| `AddressValidationTest` | 2 |

Run with:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:iosSimulatorArm64Test
```
**Always parse the results XML — a green BUILD does not prove tests executed.**

### Screens verified on device
**Android (Pixel 7 emulator, API 35):** system splash, in-app splash, login
(normal + 480×854), tour, home, categories, category listing incl. out-of-stock
card, PDP (in-stock + low-stock), cart, checkout (address → slot → payment →
review), order confirmation, orders (3 statuses), account, coins, help, about,
addresses, voice sheet, bottom nav, catalogue failure + successful retry, empty
search recovery, banner pager.

**iOS (iPhone 17 Pro simulator):** splash, login, tour, home (incl. skeleton
loading), categories, category listing, PDP, cart, checkout all four steps incl.
gating captions / in-flight / failure card, order confirmation with Paid-by,
orders, coins, help, account, search.

**Evidence on disk:** `docs/screenshots/` (47), `android/` (13),
`android-phase4/` (17), `phase5-after/` (9), `phase6-before/` (4),
`phase6-after/` (34), `qa-gate/` (13), `final-gate/android/` (16),
`final-gate/ios/` (68).

### Accessibility — completed
Touch targets (44/48dp), content descriptions on interactive atoms, disabled
semantics with reasons, 11sp micro-copy floor, **WCAG contrast audit measured and
four tokens corrected**.

### Accessibility — STILL OPEN
- **Human TalkBack + VoiceOver session.** TalkBack was enabled on the emulator and
  its focus ring confirmed rendering, but a clean gesture-driven traversal via adb
  was blocked by the service's own permission dialog. **Recorded as PARTIAL.**
- **Dynamic type / font-scaling pass** — not done.
- **Physical device QA** (Android + iPhone) — not done.

### Product photography — STATUS
**Category tiles** use openly-licensed Wikimedia Commons photography
(`docs/IMAGE_ATTRIBUTIONS.md`) — placeholders, to be replaced before launch.
**Product images do not exist.** Products render an **emoji glyph** through
`ProductImage`'s unavailable state. This is a documented **temporary development
placeholder** and must never be presented as production photography, nor replaced
with fabricated/generated imagery.

### Remaining visual items
P1: real product photography (asset dependency, not an engineering gap).
P2: shared `PhoneOtpForm` component (login flows aligned but still duplicated);
Terms & Privacy pages (link affordance deliberately removed until real legal
content exists — arguably P0 at launch).

---

## 5. ARCHITECTURE

### Intended layering
```
UI (Compose screens)
   ↓ reads state from
AppState / CheckoutSession  (in-memory, Compose state holders)
   ↓ requests data through
Repository interfaces        (data/repository/)
   ↓ resolved by
ServiceLocator               → Mock* today, Remote* later
   ↓
Data implementation (MockCatalog in-memory today; HTTP later)
```

### 🔴 Remaining violations — UI reaching past the repository boundary
**Verified by grep at generation time.** These read `MockCatalog` directly and
will keep rendering mock data after any `ServiceLocator` swap:

| File | What it reads |
|---|---|
| `ui/home/CategoryDetailScreen.kt` | `MockCatalog.categories` (screen taxonomy) |
| `ui/common/Components.kt` | `MockCatalog.categories` in `categoryTintOf()` — affects **every product card tint** |
| `ui/onboarding/OnboardingScreen.kt` | `MockCatalog.categories` ×2 (login photo wall) |
| `ui/home/HelpScreen.kt` | `MockCatalog.faqs` ×2 — **FAQ content has no repository interface at all** |

### Key components — verified locations

| Component | Location | Purpose |
|---|---|---|
| `ServiceLocator` | `data/repository/Repositories.kt` | Six `val`s resolving the repository interfaces. The single swap point for real implementations. |
| `CatalogRepository` | `data/repository/Repositories.kt` | `getCategories`, `getProduct`, `getBanners`, `getBestsellers`, `getProducts`, `search` |
| `AuthRepository` | same | `requestOtp`, `verifyOtp` |
| `OrderRepository` | same | `placeOrder`, `getOrders` |
| `CoinRepository` | same | `getBalance`, `getLedger`, `credit` |
| `AddressRepository` | `data/repository/CheckoutRepositories.kt` | `getAddresses`, `addAddress` |
| `CheckoutRepository` | same | `getSlots`, `getPaymentMethods`, `validateCart`, `placeOrder` |
| `MockCatalog` | `data/MockCatalog.kt` | 19 categories, 56 subcategories, 63 products, 6 FAQs. Development fixtures. |
| `PersistentStore` | `data/local/PersistentStore.kt` | multiplatform-settings wrapper — see §7 |
| `CartRestore` | `data/local/CartRestore.kt` | Pure reconciliation function for restored carts |
| `UiState` / `LoadError` / `rememberLoad` | `ui/state/UiState.kt` | Loading/Success/Empty/Failure + classified errors + retry |
| `StateHost` | `ui/common/States.kt` | Renders the four states for a `LoadHandle` |
| `SearchEngine` | `data/search/SearchEngine.kt` | See §8 |
| `CheckoutSession` | `CheckoutSession.kt` (package root) | Checkout state machine — see §6 |
| `BillCalculator` | `config/BillCalculator.kt` | **Single source of truth for money.** `AppState.bill()` delegates here. |
| `AppConfig` / `DeliveryCopy` / `CoinRules` / `ChargeRules` / `BrandCopy` | `config/AppConfig.kt` | All business-critical values and customer promises |
| `ProductImage` / `ProductImageLoader` / `LocalProductImageLoader` | `ui/common/ProductImage.kt` | One image architecture: fixed-aspect container, `ContentScale.Fit` (never Crop), neutral ground, loading skeleton, graceful unavailable state, pluggable loader keyed on `Product.imageUrl`. Default loader returns `Unavailable`. |
| `Analytics` / `AnalyticsEvents` / `AnalyticsSink` | `analytics/Analytics.kt` | Vendor-neutral boundary, 13 funnel events, `DevLogSink` in dev |
| `PlatformBackHandler` | `PlatformBack.kt` + `.android.kt` / `.ios.kt` | expect/actual; Android routes the system back gesture into `AppState.handleSystemBack()`, iOS is a no-op |
| `AppState` | `AppState.kt` | Back-stack navigation, cart, session, guided-tour targets, transient messages |
| `TazzzoTheme` / `TazColors` / `TazIcons` / tokens | `theme/` | Design system |
| `DemoTour` / `DemoFlags` | `DemoTour.kt` + platform actuals | QA autopilot. iOS reads `SIMCTL_CHILD_TAZZZO_DEMO_*`; Android reads intent extras (`--ez taz_fail_load true`, `--ez taz_start_home true`). **Must be disabled in release builds.** |

---

## 6. CHECKOUT / ORDER SAFETY

**This is the highest-risk area of the application. Verified against
`CheckoutSession.kt`, `data/repository/CheckoutRepositories.kt`, and
`ui/checkout/CheckoutScreen.kt`.**

### State machine
`CheckoutSession.Step` is a **closed 4-value enum**: `ADDRESS(1) → SLOT(2) →
PAYMENT(3) → REVIEW(4)`. Transitions are hard-coded in `next()` / `backStep()`,
and the indicator is driven by `Step.entries`. **Inserting a step (e.g. a payment
confirm callback) is a code change in ~4 places, not spare capacity.**

`canAdvanceFrom(step)` gates progression:
- ADDRESS → requires `address?.isServiceable == true`
- SLOT → requires `slot?.available == true`
- PAYMENT → requires `payment != null`
- REVIEW → always false (exits via placement, never `next()`)

### Back navigation
`backStep(): Boolean` — returns `false` only from ADDRESS, which the screen
interprets as "exit checkout with the cart intact". While `placement` is
`InFlight` it returns `true` without navigating, so **back cannot fire mid-placement**.
`selectAddress()` clears the selected slot, because serviceability differs per address.

### Session lifecycle
Created on checkout entry (`app.checkout = CheckoutSession()`), cleared on
successful placement and on exit. **Never persisted** — see §7.

### Idempotency
`idempotencyKey` is minted **once per session** as
`"chk-" + Random.nextLong(100_000_000_000L, 999_999_999_999L)` — a 12-digit
`kotlin.random.Random` value, **not a UUID and not cryptographically seeded**.
Worth revisiting, since the server is asked to make it a uniqueness constraint.

Every attempt — including retries after failure — submits the same key.
`MockCheckoutRepository` keeps a `placedByKey` ledger and returns the **same
order** with `replayed = true` for a repeated key.

### Duplicate-order protection (three layers)
1. UI guard: taps are ignored while `placement is InFlight`.
2. Domain guard: `backStep()` refuses navigation in flight.
3. Repository guard: the idempotency ledger — even a broken UI cannot double-order.

### Stock & price revalidation
`validateCart(lines)` runs **on checkout entry AND again inside `placeOrder`**.
It returns `CartIssue`s:
- `OutOfStock` → UI action "Remove" (removes the whole line)
- `QuantityReduced(requested, available)` → "Adjust" to available
- `PriceChanged(oldPrice, newPrice)` → **"Refresh" removes the line entirely**;
  the customer re-adds at the new price. The client **never silently accepts a
  new price mid-checkout.**

Placement is blocked while `validation?.ok != true`. An invalid cart returns
`PlaceOrderResult.Rejected`.

### Stale cart behaviour
On restore after process death, `CartRestore.reconcile` rebuilds from the
**current** catalogue — this is the one path where the current price wins
outright — and surfaces a customer-visible notice naming removed / reduced /
repriced items (at most two names per category; products no longer in the
catalogue appear as "an item").

### Order placement outcomes
`PlaceOrderResult` = `Placed(order, replayed)` | `Rejected(validation)` |
`Failed(reason, retryable)`.

### Failure / retry
`Failed` renders a `DangerSoft` card: the reason plus *"Your cart is untouched."*
The button becomes "Try again" and reuses the **same idempotency key**.

### Payment
**COD only**, enabled by the repository. **UPI and Card render disabled** with a
server-supplied "Coming soon" note. Nothing fake is processed. No card data is
collected anywhere in the app.

### Coin crediting — and the replay defect
Coins are currently credited **client-side** after a successful placement
(`ServiceLocator.coins.credit(...)` plus a local balance bump). **This is demo
behaviour and must move server-side before launch.**

> #### 🐛 DEFECT FOUND AND FIXED — replayed orders double-credited coins
> `replayed` was initially consumed **only as an analytics property**. A replayed
> placement — a duplicate tap, or a retry after a timeout — therefore ran the coin
> credit a second time, handing out coins twice for one purchase.
>
> **Fix (verified present at `ui/checkout/CheckoutScreen.kt:319`):** all client
> side effects are now gated behind `if (!res.replayed) { … }`.
>
> **Rule for the next session:** any new side effect added to the `Placed` branch
> must respect the same guard. Analytics may record `replayed`; state mutation
> may not ignore it.

---

## 7. PERSISTENCE

**Mechanism:** `multiplatform-settings` (`Settings()`), backed by
**NSUserDefaults** on iOS and **SharedPreferences** on Android. Payloads are
JSON via `kotlinx.serialization`. Corrupt payloads degrade to empty rather than
crashing (unit-tested).

### What survives process death — verified keys
| Key | Contents |
|---|---|
| `tazzzo.cart.v1` | Cart as `{id, qty, priceAtSave}` — **id + quantity + last-seen price only, never a whole Product** |
| `tazzzo.session.v1` | `{name, phone, isGuest, coinBalance, address}` |
| `tazzzo.addresses.v1` | User-added addresses (capped at 20) |
| `tazzzo.searches.v1` | Recent searches (capped at 8) |
| `tazzzo.tourSeen.v1` | Guided tour shown |
| `tazzzo.onboarded.v1` | Passed onboarding (falls back to `tourSeen` for migration) |

### Cart reconciliation on restore
`CartRestore.reconcile(saved, lookup)` — pure and unit-tested (6 tests):
- product missing → removed, disclosed
- not purchasable → removed, disclosed
- quantity > current `purchasableLimit` → clamped, disclosed
- `price != priceAtSave` → **restored at the current price**, disclosed
- zero/negative quantities dropped
The reconciled truth is written back to disk, and `AppState.restoreNotice` drives
a one-time banner on Home.

### Checkout session — DELIBERATELY NOT PERSISTED
A half-finished transaction restored after process death is a liability, not a
feature. **Verified on device:** killing the app mid-checkout leaves the cart
intact and no zombie checkout. **Do not add checkout-session persistence.**

### 🔴 Security limitations — production work required
1. **No auth tokens are stored today** (verified — there is no token field
   anywhere). When real auth lands, tokens must go to an `expect/actual` secure
   store: **iOS Keychain** (`kSecClassGenericPassword`, `WhenUnlockedThisDeviceOnly`)
   and **Android EncryptedSharedPreferences** (or DataStore + Tink).
   **Do not add a token field to the existing `PersistentStore`.**
2. **Plaintext PII is already on disk.** `SavedSession` stores **phone number**
   and address; `SavedAddress` stores line1/line2/pincode — all in plain
   NSUserDefaults/SharedPreferences, readable on rooted/jailbroken devices and
   captured by OS backups. **The secure-storage scope is wider than tokens** and
   should cover the session profile and address book.
3. Demo flags (`TAZZZO_DEMO_*`, intent extras) and `DevLogSink` must be disabled
   in release builds.

---

## 8. SEARCH

**Location:** `data/search/SearchEngine.kt`, constructed inside
`MockCatalogRepository` and invoked via `CatalogRepository.search(query)`.
The UI never calls it directly.

### Behaviour — verified from source
Scoring layers, strongest first:
1. **exact** token match on name/brand → 100
2. **synonym** (Hindi/colloquial → catalogue vocabulary) → 65 — deliberately
   **above** prefix, because "magi" means Maggi, not "Magic Masala"
3. **prefix** (min 3 chars) → 60
4. **fuzzy** → 40 — edit distance 1 for 4–5 char tokens, 2 for ≥6, with a
   **first-letter anchor** for short tokens (so "aata" does not match "Tata")
5. **weak tokens** (category + subcategory names + the product's unit) → 25 exact / 15 prefix
   — so "dairy" or "snacks" returns products by taxonomy

Rules: quantity/unit tokens (`5kg`, `500g`) are stripped as optional qualifiers;
**every remaining query word must match something** or the product is excluded.
Tie-break: score desc → `"Bestseller" in tags` desc → name asc.

### Verified examples (`docs/search-evidence.log`)
- `aata` → Shudh Chakki Atta (exactly one result)
- `doodh` → milk products
- `magi` → 2-Minute Masala Noodles **first**, Magic Masala Chips second
- `sabun` → Lux Soft Glow Soap
- `atta 5kg` → atta + aisle matches (quantity token dropped)
- `xyzzy` → 0 results

### What should move to backend
The engine is a **stand-in and the behavioural spec** for a real search service.
Its behaviour above is what the service must match or beat. Keep it as the
offline fallback when the service lands.

### 🔴 Analytics privacy concern
`AppState.recordSearch` fires `AnalyticsEvents.SEARCH` with
`mapOf("query" to q)` — **the raw text the customer typed**. This is the only
free-text field leaving the device and the item most likely to trip a PII review.
**Decide** whether to hash, truncate, allowlist or drop it. Not decided.

---

## 9. BACKEND CONTRACTS

**Reference:** `docs/BACKEND_CONTRACTS.md`.

> ⚠️ That document is an **earlier, narrower note** covering roughly six
> endpoints plus a model-change log. `docs/BACKEND_INTEGRATION_READINESS.md`
> supersedes it and is the current source of truth. Where the two disagree,
> the readiness report wins.

What `BACKEND_CONTRACTS.md` documents:
- `GET /catalog/v1/products/{id}` — product by id (added because the PDP had no honest source)
- `GET /catalog/v1/search?q=…` — search, returning `appliedQuery`
- `GET /catalog/v1/categories/{id}/products?…` — server-side listing filters
- Stock/availability payload shape on every product
- `GET /serviceability/v1/promise?…` — **gates decision D4**
- `GET /coins/v1/rules` — **gates decision D5**
- Later phases (do not build yet): cart revalidation, address CRUD + geocode,
  delivery slots, payment intent/confirm, order status stream, `GET /support/v1/faqs`
- **Model change log** — `Order.payment` was added (documented before the change
  was made) so the confirmation screen can state how the customer paid

**Consumed since (2026-10-07):** the published Home — `GET /v1/content/home?channel=app`
(see `docs/BACKEND_CONTRACTS.md` §7 and `PRODUCTION_READINESS.md`). Banners, product
rails and category grids on Home now come from the CMS; the editorial plates remain.

**Still undefined in that document:** auth endpoints, bestsellers,
address CRUD detail, payment method listing, order history — all of which the
readiness report specifies.

---

## 10. BACKEND INTEGRATION READINESS

**Reference:** `docs/BACKEND_INTEGRATION_READINESS.md` — **the readiness source
of truth.** 14 sections. It was adversarially fact-checked against source and
corrected in 12 places; read it in full before planning.

### Ready (frontend side), once prerequisites in §14 are done
HTTP client + serialization · catalogue reads · search · addresses · order
history · product images · secure token storage · analytics adapter.

### Requires frontend preparation first
- **Repository-boundary violations** (§5) — 4 files still read `MockCatalog`
- **FAQ has no repository** — needs a `SupportRepository`
- **Error-handling gaps** — see below
- **Serialization gaps** — see below

### 🔴 Serialization gaps
**No domain model carries `@Serializable`.** Verified: `grep -rn "Serializable"
data/model/` returns nothing. Only `PersistentStore`'s private DTOs are
serializable. Additionally, `Availability` and `CartIssue` are **sealed types**;
the flat JSON shapes documented in the readiness report are hand-written
mappings and require **custom serializers** — kotlinx's default polymorphism
would emit a `"type"` discriminator instead. `Product.emoji` is **required with
no default** and must be present in any payload.

### 🔴 Network / error-handling gaps
`StateHost`/`rememberLoad` is used by Home, Categories, CategoryDetail, PDP,
Addresses and Checkout. It is **not** used by these, which call `ServiceLocator`
inside a bare `LaunchedEffect` with **no try/catch** — a thrown network error
would kill the coroutine with no error state and no retry:
`OrdersScreen.kt`, `CoinsScreen.kt`, `OrderAgainTabContent.kt`, `SearchScreen.kt`.
The **auth calls** in `OnboardingScreen.kt` and `LoginScreen.kt` are also
unguarded (`error = true` is set only when `verifyOtp` returns null, never on a
thrown exception).

### Security gaps
See §7. Tokens → secure storage; plaintext PII already persisted; demo flags must
be off in release; no card data in the app; server-authoritative money and stock.

### Dependencies
| Dependency | Status |
|---|---|
| **Payment** | Gateway unselected — business decision. COD only today. |
| **Catalogue** | Source unselected (curated / supplier feed / crawler). |
| **Serviceability** | No service. App shows neutral "Fast delivery". |
| **Image / CDN** | No URLs, no CDN, and no Compose image-loading library chosen. |
| **Support content** | FAQs are local fixtures; no `SupportRepository`. |
| **Analytics** | Vendor unselected by design; boundary ready. |

---

## 11. BUSINESS DECISIONS / LAUNCH GATES

**These are the founder's decisions. The next session must NOT decide them.**

### D4 — Delivery promise
- **Status:** UNRESOLVED.
- **Current behaviour:** `AppConfig.deliveryPromise = DeliveryPromise.Unknown`,
  which renders the neutral **"Fast delivery"** and shows *nothing* where a
  specific time would go. All delivery strings flow through `DeliveryCopy`.
- **Decision needed:** what may we promise per area once serviceability exists?
- **Depends on it:** home header, splash, login, PDP delivery line, cart, ETA
  chips, delivery banner. `DeliveryPromise` supports `Unknown`, `Estimate(minutes)`,
  `Window(min,max)`, `NotServiceable`.

### D5 — Coin economics
- **Status:** UNRESOLVED. Current values are **development configuration, not
  approved financial policy**.
- **Current behaviour:** `earnPercent = 2`, `rupeesPerCoin = 1`,
  `maxRedeemPerOrder = null`, `expiryDays = null`, `enabled = true`.
  **Redemption UI is deliberately hidden.** All coin copy derives from config, so
  the app cannot advertise a rate the ledger does not implement.
- **Decision needed:** earn rate, coin value, per-order redemption cap, expiry.
- **Depends on it:** Coins screen, cart coin line, confirmation, home coins
  banner, coin chip, tour step 2.

### D6 — Founder / marketing claims
- **Status:** UNRESOLVED — requires substantiation or amendment.
- **Current behaviour:** `BrandCopy.savingsClaim = "SAVE 8–20%"` and
  `voiceTeaser = "India's first Voice Commerce"` are **config-sourced**, so
  withdrawal is a one-line change. Setting `savingsClaim = null` makes the hero
  fall back to a claim-free line automatically.
- **Decision needed:** substantiate with real price-comparison data, or amend.
- **Depends on it:** home hero banner, About screen, splash caption, login
  sub-line, voice sheet.

---

## 12. PRODUCTION BLOCKERS

**Source:** `BLOCKERS.md` — read it directly; it is the single source of truth
and carries the rule that **a blocker is never silently removed**; resolution
requires a dated entry in `PRODUCTION_READINESS.md` describing how it was verified.

### P0 — blocks any launch
- Real backend: auth, catalogue, stock, serviceability, orders, payments (all mocked)
- Real payments (COD only; UPI/card are disabled placeholders)
- **D4** delivery promise unapproved
- **D5** coin economics unapproved
- *(Resolved earlier and recorded: persistence — verified by 8 on-device
  process-death tests. Note its caveat: storage is plain preferences; tokens must
  move to secure storage.)*

### P1 — blocks production quality
- Automated UI/journey tests (41 domain tests exist; **no UI test harness**)
- Accessibility: atoms + contrast done; **human screen-reader pass**, dynamic type open
- Analytics vendor + crash reporting
- Remaining Android depth: PDP tap-test, scroll-feel review, **physical device**
- Production photography licensing (category tiles are Commons placeholders)
- **Real product photography** (asset dependency; architecture is complete)
- **Pre-launch verification gates** — human TalkBack + VoiceOver, dynamic type,
  physical Android + iPhone QA. *These are verification gates, not reasons to
  reopen UI design.*

### P2 — polish / post-launch
- Shared `PhoneOtpForm` component (flows aligned, still duplicated)
- Vector icon system evaluation (Material family in use)
- Brand-filter multi-select sheet as the catalogue grows
- **Terms of Service & Privacy Policy pages** — link affordance removed until real
  legal content exists (*arguably P0 at launch*)
- Splash warm-start prefetch
- **D6** claim substantiation
- Micro-interactions, performance profiling on hardware

*(D1 palette, D2 tagline, D3 Poppins were resolved in the visual phase.)*

---

## 13. WHAT MUST NOT BE DONE

The next session must **NOT**:

1. **Redesign the UI or change the design system.** It is frozen. Fix genuine
   functional defects only, and document before changing.
2. **Invent backend APIs, endpoints, payloads, or behaviour.** If something is
   needed and undocumented, write the required contract down — do not implement
   against an imagined one.
3. **Invent business rules**, delivery promises (D4), or coin economics (D5).
4. **Fake payment integration.** COD only; UPI/card stay disabled until a real
   gateway is chosen and integrated.
5. **Claim real stock or serviceability** without a backend providing it.
6. **Add fabricated or AI-generated product photography.** Keep the documented
   temporary placeholder until legitimate catalogue image URLs exist.
7. **Remove or downgrade blockers** to make the project look production-ready.
8. **Replace mocks blindly.** Check the repository boundary first (§5) — a
   `ServiceLocator` swap alone leaves four UI files still on mock data.
   **Keep the mocks** as offline fallback, demo mode, and test doubles.
9. **Persist the checkout session.**
10. **Silently change a customer's price** or **silently adjust quantities** —
    every deviation must be disclosed.
11. **Double-credit coins** — respect the `!res.replayed` guard for every side effect.
12. **Store production credentials/tokens in the app** or in plain preferences.
13. **Expose sensitive data in analytics or logs** — including the raw search query.
14. Treat "it compiles" or "the build is green" as verification. Parse test XML;
    look at the running app.

---

## 14. NEXT PHASE — PRE-BACKEND INTEGRATION

Work to complete **before the first real API is connected**. Ordered by
dependency, based on the actual codebase.

**A. Remove UI → MockCatalog coupling.** Four files (§5). Route category taxonomy
through `CatalogRepository.getCategories()`. `categoryTintOf()` in
`Components.kt` is the highest-impact one — it affects every product card.

**B. Complete repository boundaries.** Add a `SupportRepository` for FAQ/help
content (currently no interface at all). Confirm no other data surface bypasses
the boundary.

**C. Migrate remaining load paths to `UiState`/`StateHost`.** `OrdersScreen`,
`CoinsScreen`, `OrderAgainTabContent`, `SearchScreen`. Separately, wrap the
**auth calls** in `OnboardingScreen` and `LoginScreen` so a thrown error becomes
a visible, retryable state rather than a dead coroutine.

**D. Make models serialization-ready.** Add `@Serializable`; write **custom
serializers for `Availability` and `CartIssue`** (sealed types); confirm
`Product.emoji` is supplied or defaulted; decide integer-rupee handling is
explicit in the wire format. Add serialization round-trip tests.

**E. Secure storage.** `expect/actual` over Keychain / EncryptedSharedPreferences.
Scope it to auth tokens **and** the existing plaintext PII (phone, addresses).

**F. Environment separation.** `ApiConfig` currently hard-codes one gateway
constant. Introduce dev/staging/prod configuration, and ensure demo flags and
`DevLogSink` cannot ship enabled.

**G. API error mapping.** Map transport failures onto the existing five
`LoadError.Kind`s (Network/Timeout/Server/Unauthorized/Unknown). Set timeouts and
a retry policy for idempotent GETs. **The client must throw, not return empty
lists** — empty means "no results", a different screen.

**H. Authentication / session lifecycle.** Token storage, refresh on 401, guest→
authenticated transition (`UserProfile.isGuest`, `AppState.isOnboarded`,
`markLoggedOut`). **Browsing must never require auth**; only checkout should.

**I. Analytics privacy treatment.** Decide the raw-search-query question (§8) and
confirm no PII in any event.

**J. Expand tests.** Serialization round-trips, repository contract tests against
a fake server, and a UI/journey harness (none exists). Preserve the 41 passing.

**K. Also required by this codebase.**
- Choose a Compose Multiplatform image-loading library and implement
  `ProductImageLoader` once.
- Consider replacing the 12-digit `Random` idempotency key with a UUID.
- (Done 2026-10-10: `OfflineBanner` deleted — no connectivity observer is planned for day 1.)
- Move coin crediting server-side and remove `CoinRepository.credit` from the
  client path.
- Decide whether to initialise **git** — the project is not under version control.

---

## 15. BACKEND INTEGRATION EXECUTION PLAN

Proposed order **after §14 is complete**. Only capabilities supported by the
existing contract/readiness documents are listed. Each stage should land behind
its own review.

| # | Stage | Frontend dependency | Backend dependency | Test requirement | Failure cases | Release risk |
|---|---|---|---|---|---|---|
| 1 | **Authentication** | §14 E, G, H | OTP request/verify + tokens | Token storage, refresh-on-401, guest→auth | OTP not delivered, expired token, refresh loop | **High** — gates everything user-scoped |
| 2 | **Catalogue** | §14 A, B, D | categories, product-by-id, listings | Contract tests, serialization round-trips | Empty catalogue, malformed payload, 5xx | High — the app is unusable without it |
| 3 | **Images** | §14 K (loader) | CDN URLs on products | Loading/unavailable states, wrong aspect | 404, slow CDN, oversized images | Low — graceful fallback exists |
| 4 | **Search** | §14 C, G | search service | Parity against `search-evidence.log` | Zero results, timeout, relevance regressions | Medium |
| 5 | **Serviceability** | D4 decision | promise + slots | Serviceable / not-serviceable / empty slots | Unknown pincode, service down | **High** — customer promise |
| 6 | **Addresses** | §14 E (PII) | address CRUD + geocode | Add/validate/select, long addresses | Geocode failure, unserviceable | Medium |
| 7 | **Cart / stock** | — | stock + `validateCart` | All three `CartIssue` types, restore reconciliation | Stale cart, price drift, stock loss | **High** — money and trust |
| 8 | **Checkout** | stages 5–7 | slots + validate + place | Full state machine, gating, failure/retry | Mid-flight kill, invalid cart, rejection | **Critical** |
| 9 | **Orders** | §14 C | order history + status | Status rendering, reorder | Empty history, unknown status | Medium |
| 10 | **Payments** | gateway decision | intent/confirm | Success, failure, cancel, timeout, duplicate | Double charge, orphan payment, retry | **Critical** — real money |
| 11 | **Coins** | D5 decision | ledger + server-side crediting | Earn on completion, replay-safety, ledger | Double credit, drift between UI and ledger | High |
| 12 | **Help / support** | §14 B | FAQ content | Content render, search filter | Empty content | Low |
| 13 | **Analytics** | §14 I | vendor SDK | Event fires, no PII | Over-collection, PII leak | Medium — compliance |
| 14 | **Crash / error reporting** | — | vendor SDK | Symbolication both platforms | Noise, PII in breadcrumbs | Medium |

**Idempotency, revalidation and the replay guard must be re-verified at stages
8, 10 and 11 against the real backend — not assumed from the mock.**

---

## 16. THREE-WAY DEPENDENCY MATRIX

### ✅ READY FOR IMPLEMENTATION *(no external dependency — start here)*
- Remove UI → MockCatalog coupling (4 files)
- `SupportRepository` for FAQ content
- `StateHost` migration: Orders, Coins, OrderAgain, Search
- Error handling around auth calls (Onboarding, Login)
- `@Serializable` + custom serializers for `Availability` / `CartIssue`
- Secure storage `expect/actual` (tokens + existing plaintext PII)
- Environment separation; ensure demo flags off in release
- API error mapping onto `LoadError`
- Choose image-loading library; implement `ProductImageLoader`
- Replace `Random` idempotency key with a UUID
- (`OfflineBanner` deleted 2026-10-10)
- Serialization + repository contract tests; UI/journey harness
- Decide on git initialisation
- Android depth: PDP tap-test, scroll review, physical device
- Dynamic type pass

### ⏳ WAITING FOR BACKEND
- Auth service (OTP, tokens, refresh)
- Catalogue service (categories, product-by-id, listings, banners, bestsellers)
- Stock / inventory
- Search service
- Serviceability + delivery slots
- Address CRUD + geocoding
- Cart revalidation endpoint
- Orders service incl. server-side idempotency ledger
- Server-side coin crediting + ledger
- Payment gateway integration
- Image CDN + product image URLs
- Support/FAQ content service
- Push notifications; live order tracking

### 🧭 WAITING FOR PRODUCT / BUSINESS DECISION *(founder)*
- **D4** delivery promise
- **D5** coin economics
- **D6** founder/marketing claims
- Payment provider selection
- Catalogue source (curated / supplier / crawler)
- Product photography commissioning + licensing
- Analytics & crash-reporting vendor
- Terms of Service & Privacy Policy content
- Raw-search-query analytics privacy treatment
- Launch scope (investor demo / pilot pincode / public launch)

---

## 17. TESTING / RELEASE GATE

### Current status — verified
- **Unit tests: 41, 0 failures** (7 classes, §4). Domain logic only.
- **No repository/API tests** — nothing to test against yet.
- **No serialization tests** — models are not serializable yet.
- **Checkout/idempotency: covered** — duplicate-key replay, failure-then-retry
  places exactly once, invalid cart rejected, session transitions, back-blocking.
- **No UI/journey test harness.** All journey verification to date is manual +
  screenshot evidence.
- **Android:** emulator only, incl. 8 process-death tests and small screen.
- **iOS:** simulator only.
- **Process death:** 8 mandated tests passed on Android (cart restore, doctored
  stock/price/removal reconciliation with notice, address survival, recent-search
  survival, checkout NOT restored, coin balance survival).
- **Accessibility:** contrast measured and fixed; targets and semantics done;
  screen-reader **partial**; dynamic type not done.
- **Network failure:** catalogue failure + successful retry verified on device via
  the `taz_fail_load` flag.
- **Payment:** not tested — no gateway.
- **Production configuration:** not defined.
- **Security:** no penetration or dependency audit performed.

### Must be true before production
Serialization round-trip tests · repository contract tests against a fake server ·
UI/journey tests for the critical path · idempotency re-verified against the real
orders service · payment success/failure/cancel/duplicate coverage · human
TalkBack + VoiceOver · dynamic type · physical Android + iPhone QA · release
configuration with demo flags disabled and secure storage in place · security
review of token handling, PII at rest, and analytics payloads.

---

## 18. FILES THE NEXT SESSION MUST READ FIRST

**Documents (in this order):**
1. `docs/DESIGN_SPEC.md` — design authority, FROZEN
2. `BLOCKERS.md` — P0/P1/P2, never silently changed
3. `docs/BACKEND_INTEGRATION_READINESS.md` — **readiness source of truth**
4. `docs/BACKEND_CONTRACTS.md` — earlier narrower note + model change log
5. `PRODUCTION_READINESS.md` — feature × verification matrix and dated log

**Source — architecture and safety:**
- `data/repository/Repositories.kt` — `ServiceLocator` + 4 interfaces + mocks
- `data/repository/CheckoutRepositories.kt` — address/checkout, idempotency ledger
- `CheckoutSession.kt` — checkout state machine
- `ui/checkout/CheckoutScreen.kt` — placement, replay guard at ~line 319
- `AppState.kt` — navigation, cart enforcement, restore
- `config/BillCalculator.kt` + `config/AppConfig.kt` — money and D4/D5/D6
- `data/model/Models.kt`, `data/model/Checkout.kt` — all payload shapes
- `data/local/PersistentStore.kt`, `data/local/CartRestore.kt` — persistence
- `ui/state/UiState.kt`, `ui/common/States.kt` — load/error contract
- `data/search/SearchEngine.kt` — search spec
- `ui/common/ProductImage.kt` — image architecture + loader seam
- `analytics/Analytics.kt` — analytics boundary
- `theme/` — `Theme.kt`, `Tokens.kt`, `TazIcons.kt`

**Evidence:** `docs/screenshots/final-gate/{android,ios}/`, `docs/search-evidence.log`

---

## 19. CURRENT HANDOFF STATUS

```
UI:                        FROZEN
Backend integration:       NOT STARTED
Current test status:       41 tests, 0 failures (verify: parse test-results XML)
Android:                   Builds + runs; emulator-verified incl. 480×854.
                           No physical-device testing.
iOS:                       Builds + runs; iPhone 17 Pro simulator-verified.
                           No physical-device testing.
Version control:           NOT under git — no repository exists
Production readiness:      NOT READY
Next action:               PRE-BACKEND INTEGRATION PREPARATION (§14)
Approval required before:  FIRST REAL BACKEND API INTEGRATION
```

---

## 20. INSTRUCTION FOR THE NEXT CLAUDE SESSION

> Read this handoff completely. Then inspect the actual repository and cross-check
> this handoff against the source and the referenced documents. Do not blindly
> trust the handoff if the source disagrees. Report discrepancies before changing
> anything.
>
> Your first task is ONLY to produce a pre-backend integration implementation plan
> and dependency matrix.
>
> Do not redesign the UI.
> Do not start backend integration.
> Do not invent APIs.
> Do not change business decisions.
> Do not remove blockers.
>
> Wait for explicit GO before connecting the first real backend API.
