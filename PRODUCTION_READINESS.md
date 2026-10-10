# Production Readiness Checklist

Standing rule: **build-green is never completion evidence.** A feature is
complete only when code + business logic + failure states + tests +
running-device verification + platform verification are all addressed.

Statuses: ✅ done · 🟡 partial · ❌ not started

> **2026-08-31 — PHASE TRANSITION.** UI/UX Final Gate **PASSED**; design system
> and customer-facing UI are **FROZEN** (`docs/DESIGN_SPEC.md`). Production
> readiness is **NOT** passed. Next phase is backend integration, gated on
> `docs/BACKEND_INTEGRATION_READINESS.md` and explicit approval. Remaining
> pre-launch verification (human screen-reader, dynamic type, physical devices)
> are verification gates — they do not justify reopening UI design.

| Feature | Implemented | Tested | Android verified | Error states | Backend-ready | A11y reviewed | Production-ready |
|---|---|---|---|---|---|---|---|
| Splash / onboarding / login (photo wall) | ✅ | 🟡 manual | ✅ journey run | 🟡 | ✅ contract doc'd | ❌ | ❌ |
| Guided tour (3 steps) | ✅ | 🟡 manual | ✅ | n/a | n/a | ❌ | 🟡 |
| Home feed | ✅ v3 hierarchy | 🟡 via StateHost | ✅ both platforms | ✅ skeleton/error/retry | ✅ | 🟡 targets+labels | ❌ |
| Category browse + filters/sort | ✅ grid+keys | 🟡 | ✅ | ✅ | ✅ | 🟡 targets | ❌ |
| Search v2 (typo/Hindi synonyms) | ✅ | ✅ engine evidence log | ✅ keyboard-typed aata on device | ✅ no-results recovery | ✅ contract doc'd | ❌ | ❌ |
| Product detail | ✅ pinned buy bar | 🟡 | 🟡 not tap-tested | ✅ | ✅ getProduct contract | 🟡 | ❌ |
| Cart + stock enforcement | ✅ | ✅ 5 unit tests | ✅ | ✅ OOS/limits | ✅ | ❌ | ❌ |
| Bill maths (single source) | ✅ | ✅ 9 unit tests | ✅ (shared code) | n/a | ✅ | n/a | ✅ logic only |
| Checkout state machine | ✅ 3-state steps + To-pay | ✅ 5 unit tests | ✅ journey + back-gesture | ✅ reasons shown | ✅ | 🟡 targets+semantics | ❌ |
| Cart revalidation (stock/price) | ✅ | ✅ 4 unit tests | 🟡 via shared code | ✅ issues UI | ✅ | ❌ | ❌ |
| Idempotent order placement | ✅ | ✅ 3 unit tests | ✅ order placed | ✅ failure/retry UI | ✅ | n/a | 🟡 mock only |
| Orders / coins / help / account | ✅ | 🟡 | 🟡 not tap-tested | 🟡 | 🟡 FAQ still mock-coupled | ❌ | ❌ |
| System back handling | ✅ | ✅ device test | ✅ steps checkout correctly | n/a | n/a | n/a | ✅ |
| Persistence (cart/session/addresses/searches) | ✅ | ✅ 11 unit tests | ✅ 8 process-death tests | ✅ restore notice | ✅ id+qty only | n/a | 🟡 mock-backed |
| Analytics event boundary | ✅ 13 events | 🟡 log-verified | ✅ shared code | n/a | ✅ vendor-neutral | n/a | 🟡 sink pending vendor |
| Voice commerce | teaser only | n/a | ✅ renders | n/a | ❌ | ❌ | ❌ by design |

## Verification log (never remove a row — record how each was verified)

- 2026-10-10 RELEASE ORDERING ON + ADVISORY MONEY (`feature/app-orders-checkout-release`). REMOTE places real COD orders
  (`orderIntegration = true`, debug opt-in removed) and shows real order history. The backend quote `moneyPreview` is ADVISORY
  (the order computes its own money, may differ, placed with 200; PAYABLE_CHANGED is never sent): checkout shows "Total" +
  "Final amount is confirmed when you place your order.", and the confirmation/detail always display the order's money with
  "Your total changed from ₹X to ₹Y" when it differs from what was reviewed (PayableDriftTest). Verified only in a scratch JVM
  harness (non-Compose code + tests); Compose UI and platform builds are CI-only; NOT run on a device.

- 2026-10-10 PUBLISHED HOME CONTENT — product-read 429 handling (PR #24, review M1). A 429 from
  `GET /v1/products/{id}` used to fail one rail and carry on with the next, up to 400 charged reads per cold start. Now
  it ends the sweep at once and opens a window for all product reads (`Retry-After`, capped 120 s, floor 60 s) that
  refuses a pull (toast) and blocks foreground re-reads and PIN changes; failed ids are not re-asked for 60 s; a sweep
  reads at most 120 distinct cards (rest carried to the next sweep). Added 4 holder tests and fixed a vacuous
  assertion. NOT verified locally: no Android SDK/Xcode in the sandbox, so compile and tests rely on CI. Follow-up:
  `/v1/products:batch` (BACKEND_CONTRACTS §7).

- 2026-10-09 PUBLISHED HOME CONTENT — grid tiles named by id (PR #24). The
  taxonomy walk (up to 13 admission-charged reads per resolution) is replaced
  by ONE `GET /v1/categories/{id}` per grid id (backend #109): sequential, ≤ 12
  per resolution, names cached 300 s, 404 remembered 300 s and drops the tile,
  first failure ends the resolution (60 s backoff), a 429 stops every node read
  for `Retry-After` capped at 120 s. No walk fallback. Verified: Android JVM
  unit tests 1,104 / 0 and iOS simulator tests 1,104 / 0 (new: 10 resolver,
  4 contract, 1 holder). NOT verified on an emulator/simulator: pull gesture,
  foreground re-read, banner navigation, image fallback, TalkBack/VoiceOver
  semantics — the run was stopped by the < 4 GB free-disk guardrail before
  the local backend or emulator started (the app also has no local-backend
  override yet; the debug-only override was written but not committed
  because it could not be run).

- 2026-10-08 PUBLISHED HOME CONTENT — re-review follow-ups (PR #24). A pull
  re-reads only cards older than 30 s (≤ one card sweep per 30 s; a superseded
  sweep keeps the cards it finished); the grid walk stops at the first failed
  read (429 included) and does not retry an unnamed id for 5 min (1 min after a
  failure); `Retry-After` is capped at 120 s like `RetryPolicy`; a pull refused
  by a 429 window shows the transient toast. Numeric-only `TZP-` ids restored
  in BACKEND_CONTRACTS §7. Verified: Android JVM unit tests 1,096 / 0 (7 new),
  iOS simulator tests 1,096 / 0 and the framework linked;
  7 mutation probes all killed.

- 2026-10-08 PUBLISHED HOME CONTENT — review remediation (PR #24). Grids name
  node ids at any level (TZS/TZC/TZG/TZV) by a bounded, sequential walk of the
  existing taxonomy reads (≤ 12 children reads, cached 300 s); an unnamed id
  skips its tile, not the grid. Pull-to-refresh on Home and a re-read on return
  to the foreground once the last success is > 60 s old; failures back off
  10 → 20 → 40 → 60 s and honour 429 `Retry-After`. Rails no longer flash on a
  re-read: per-PIN card cache, only new ids or cards > 5 min old are read, a
  failed re-read keeps the rail; a PIN change still reloads. Holder state is
  confined to a single-threaded scope (as the cart store). Rails render all 20
  ids the backend allows; the 20-block cap is documented. Banners render
  `subtitle`, describe the image with `altText`, are one accessibility node
  (button role when tappable). `search:` stays untappable (no query-capable
  Search screen). Verified: Android JVM unit tests 1,089 / 0 failures (24 new),
  iOS simulator tests 1,089 / 0, `linkDebugFrameworkIosSimulatorArm64` and
  `assembleDebug` built; 16 mutation probes, 15 killed (the survivor removes
  one of two equivalent no-flash branches; removing both is killed). Not verified on a
  device: pull gesture, foreground re-read and TalkBack/VoiceOver reading
  (no reachable non-prod backend; emulator run abandoned for disk space).

- 2026-10-07 PUBLISHED HOME CONTENT (backend P6 / app-integration). Home now
  renders the CMS-published blocks of `GET /v1/content/home?channel=app`
  (`data/content/`: DTO → `HomeContent` mapping, `RemoteContentDataSource`,
  `HomeContentHolder`; `RemoteHomeScreen` renders banners, product rails and
  category grids in the backend's order before the editorial plates). Truthful
  by construction: a banner without an https image is not shown; a link outside
  the closed grammar (`product:` | `category:` | `search:`) leaves the banner
  untappable, and `search:` is untappable until the Search screen can open on
  a query; rails load at most 12 cards through the PIN-aware product read
  (404 = absent, bounded parallelism 4); grids show only loaded root nodes; a
  failed or empty read shows NOTHING (no error surface, the catalogue sections
  stand alone). A copy OR a failure younger than 60 s is not re-requested
  (visiting the Home tab repeatedly against a backend that cannot serve it
  sends nothing more); a stale copy stays on screen while re-read and survives
  a failed re-read; rail loading is serialised (a PIN change and a content
  load never run two loaders). Verified: Android JVM unit tests 1,065 / 0
  failures (19 new in `HomeContentMappingTest`, `RemoteContentDataSourceTest`,
  `HomeContentHolderTest`: link grammar, order, block dedupe within the cap,
  20-block / 12-id / 80-char title bounds, wire nulls treated as absent,
  request shape, 400/429/503 mapping, single-flight + 60 s freshness for
  copies and failures, stale copy kept, rail 404 handling, bounded
  parallelism, PIN change reload, anonymous even with a session);
  `assembleDebug` built (24.7 MB debug APK); iOS simulator tests 1,061 / 0 and
  `linkDebugFrameworkIosSimulatorArm64` linked (Xcode 26.4.1) at the first
  head; 14 mutation probes (http image allowed, open link grammar, rail cap
  dropped, channel=web, authenticated read, no freshness window, rail failure
  fails Home, unbounded parallelism, no block dedupe, grid cap, block cap,
  title trim, re-fetch after failure, stale copy blanked) all killed by the
  tests. Independent review of the first head (PASS, 2 MEDIUM) led to the
  failure-window, stale-copy and rail-serialisation changes above. Backend dependency:
  `channel` exists from backend PR #96; an older backend answers 400 and the
  app shows no published blocks (the documented degraded state). Not done:
  banner desktop/mobile variants (backend D4), `search:` deep link, click
  analytics.

- 2026-09-01 PRE-BACKEND PREPARATION — Wave 0. Project placed under git for the
  first time; baseline commit "PRE-BACKEND BASELINE" records the verified state
  (56 Kotlin files, 11,767 lines, 41 tests / 0 failures) as the rollback point.
  DEFECT D-1 FIXED (money): the demo autopilot in DemoTour.kt placed real orders
  through a hand-rolled copy of the checkout screen's logic and credited coins
  WITHOUT the `!replayed` guard, so a replayed placement credited coins twice.
  The earlier fix recorded on 2026-08-31 covered CheckoutScreen.kt only; the
  duplicated logic was the actual defect. Both call sites now go through one
  shared path, `order/OrderPlacement.kt`, where the guard is structural: all
  once-per-order side effects live in a single private `applyFirstPlacement`.
  Proven by 5 new tests (OrderPlacementTest): first placement credits exactly
  once; a replayed placement credits zero more; failure-then-retry credits
  exactly once and leaves the cart untouched; five repeated placements yield one
  order id and one credit; an in-flight attempt never reaches the repository.
  D-2 RESOLVED (file since deleted in hardening PR-2): data/remote/ApiConfig.kt carried its own endpoint sketch that
  contradicted both contract documents and named a VOICE_SERVICE for a feature
  with no implementation. Verified unreferenced by any source file, so no
  functionality depended on it. Sketch removed; the file now points at
  docs/BACKEND_INTEGRATION_READINESS.md (primary) and docs/BACKEND_CONTRACTS.md
  (secondary) as the only contract authorities, and retains only the gateway
  origin as the network seam.
  D-3 RESOLVED: BLOCKERS.md said "30 domain tests" against an actual 41; count
  corrected in place with an inline note rather than a silent rewrite.
  Regression: 41 → 46 tests, 0 failures (XML-verified). No UI change; the
  design freeze is intact and DESIGN_SPEC.md is untouched.
- 2026-08-30 Android journey: launch → skip → tour → home → category → ADD ×2 →
  cart → checkout (address → slot → COD → review) → placed #TZ100484, +6 coins.
  Evidence: docs/screenshots/android/a1–a13. Emulator Pixel 7 / API 35 arm64.
- 2026-08-30 System back on Android: keyevent 4 during slot step returned to
  address step with selection intact (a11_backtest.png).
- 2026-08-30 Regression after Android enablement: iOS compile green; 30/30
  unit tests pass (results XML parsed, not inferred from build status).
- 2026-08-30 Search relevance: docs/search-evidence.log (aata→atta, magi→Maggi,
  unit-token dropping, junk→0).
- 2026-08-31 BACKEND INTEGRATION READINESS REPORT produced
  (docs/BACKEND_INTEGRATION_READINESS.md, 14 sections) and ADVERSARIALLY
  FACT-CHECKED against source — 12 errors found in my own first draft and
  corrected. Most consequential: (a) four UI call sites bypass the repositories
  and read MockCatalog directly, so the "one-line ServiceLocator swap" claim was
  false; (b) FAQ content has no repository interface at all (7th data surface);
  (c) no domain model carries @Serializable and Availability/CartIssue need
  custom serializers; (d) Product.emoji is required and was missing from the
  documented payload; (e) PRICE_CHANGED removes the line rather than accepting
  the new price — the doc said the opposite; (f) four screens (Orders, Coins,
  OrderAgain, Search) still use bare LaunchedEffect with no try/catch and would
  crash on a thrown network error; (g) etaMinutes is dead code; (h) analytics
  sends raw search-query text (PII review item); (i) plaintext phone + addresses
  already persist in NSUserDefaults/SharedPreferences, so secure-storage scope
  is wider than tokens.
  REAL DEFECT FOUND AND FIXED: a replayed order placement re-credited Tazzzo
  Coins (no `if (!res.replayed)` guard) — duplicate tap or retry-after-timeout
  would hand out coins twice for one purchase. Fixed and gated; 41/41 tests
  still green. Two stale KDocs corrected (SearchEngine ranking order contradicted
  its own code; ProductImage aspect examples were wrong).
  UI remains FROZEN — the only code change was the coin-credit defect fix.
- 2026-08-30 FINAL VISUAL GATE (in progress at report time; iOS recapture noted
  below). SCREEN FIXES: Help fully redesigned around real actions — FAQ search
  field, "Help with an order" rows routing to Orders/WhatsApp, WhatsApp as the
  primary contact card, no invented SLAs, empty green banner deleted. Tour card
  refined (brand rail, layered icon mark, h1 title, dots-left footer). Category
  sidebar refined (Surface rail, edge-pill selection, honest item-count strip).
  Orders verified against legitimate fixtures — three seeded orders covering
  DELIVERED / ON_THE_WAY / PACKED with correct progress rails (fixtures
  documented as development data; only model-supported statuses used).
  NEVER-VERIFIED SCREENS CLOSED: Addresses (serviceability chip, honest
  add-via-checkout guidance) and Voice sheet — where a surviving speed claim
  ("Cart ready in seconds") was caught on-device and removed.
  CONTRAST AUDIT (measured, script in repo history): 4 token failures found and
  fixed — Orange 3.61:1→#C74018 (4.78–5.03), TextTertiary 3.28→#73786E (4.53),
  Success on SuccessSoft 4.46→#147E3C (4.58), and CoinGold (2.19 on light —
  split into CoinInk #9D6E00 for light surfaces; bright gold stays on dark).
  The DESIGN_SPEC's earlier "orange passes 4.6:1" claim was WRONG and has been
  corrected in place. TazRadius.sheetAll token added (coach-mark card had
  square bottoms risk with the top-only sheet shape).
  TALKBACK: service enabled on-device, focus ring verified rendering; a clean
  gesture-driven traversal of app content was NOT achieved via adb (the
  service's own permission dialog intercepted the walk) — screen-reader pass
  recorded as PARTIAL; human TalkBack/VoiceOver session remains P1.
  iOS: 44-frame journey captured — full flow incl. checkout gating captions,
  failure card, in-flight state, confirmation with Paid-by, skeleton home.
  CAUGHT MYSELF: those frames predate the final-gate fixes (old Help visible),
  so iOS was REBUILT and the changed screens re-captured rather than claiming
  stale evidence — per the "compilation is not visual QA" rule.
  iOS FINAL-BUILD RECAPTURE COMPLETE: redesigned Help (search + order rows +
  WhatsApp primary), Orders with FOUR statuses (Placed/Packed/On the way/
  Delivered) and correct rails, refined tour card with spotlight, checkout
  in-flight + confirmation with Paid-by, home skeleton, pager through all three
  banners, categories/account. Evidence: docs/screenshots/final-gate/ios/.
- 2026-08-30 VISUAL QA GATE. Product image architecture shipped
  (ui/common/ProductImage.kt): fixed-aspect container, ContentScale.Fit never
  Crop, uniform SurfaceSunken ground, loading skeleton, graceful unavailable
  state, and a ProductImageLoader seam driven by Product.imageUrl — real
  packshots will drop in with no screen redesign. Wired into ProductCard AND
  the PDP hero so both share one container. Emoji fallback documented as
  TEMPORARY, never presented as production photography.
  DEFECTS FOUND ON DEVICE AND FIXED: (1) Tazzzo Coins was represented by a US
  DOLLAR glyph (MonetizationOn) in a rupee product — replaced with a
  currency-neutral two-coin mark; (2) the WhatsApp card used a question-mark
  icon — now a chat metaphor; (3) the banner carousel cross-faded two OPAQUE
  banners, so mid-transition both headlines overlapped and read as a rendering
  bug — replaced with HorizontalPager (also delivers the swipe the audit asked
  for, and the next banner now peeks to signal it).
  ANDROID QA TOOLING: failure flag is now testable on Android via
  `adb shell am start -n com.tazzzo.app/.MainActivity --ez taz_fail_load true`
  (debug-only intent extras read in MainActivity).
  VERIFIED ON DEVICE THIS GATE (fresh-install each run, pm clear): Account,
  Tazzzo Coins, Help, About, Orders, catalogue failure + successful retry,
  empty search with recovery, home pager, small screen 480x854 CTA above fold.
  Evidence: docs/screenshots/qa-gate/. Missing-image state is verified
  implicitly and exhaustively — it is currently EVERY product's state.
  STILL UNVERIFIED VISUALLY: Addresses screen, Voice sheet.
  Regression: 41 tests / 0 failures; iOS + Android builds green.
- 2026-08-30 PHASE 6 Visual elevation (splash-first). FOUNDATION: Poppins
  adopted app-wide (geometric match to the wordmark + Devanagari cut for future
  Hindi; ~630KB, documented in docs/DESIGN_SPEC.md); TazIcons vector family
  replaces ALL emoji-as-UI (verified by glyph scan: zero remain, product and
  category emoji stay as content); colour system rebuilt with a neutral surface
  ramp (Surface/SurfaceSunken/TextTertiary/BorderStrong + semantic softs) so
  photography and prices lead — Orange and CoinGold darkened to clear 4.5:1 on
  white; commerce type tokens for price/MRP/savings/product-name.
  ANDROID FIRST-IMPRESSION BUG FOUND AND FIXED: the app had no launcher icon or
  splash theme, so Android 12+ showed the generic robot placeholder before
  Compose started. Added adaptive icon (brand mark on cream, monochrome variant)
  + branded windowSplashScreen — the system splash and in-app splash now read as
  one moment.
  SCREENS: splash choreographed (mark, then words, then progress track); login
  rebuilt (photo band, badge overlapping the photography, config-backed value
  strip, unified field language, 48dp Skip); tour restyled; home re-composed
  with a fixed brand bar and section rhythm; category/search/PDP/cart/checkout/
  confirmation/orders/account/help/coins/about/voice all rebuilt against the
  spec. Order.payment added (documented in BACKEND_CONTRACTS.md first) so the
  confirmation can state how the customer paid instead of omitting it.
  SMALL SCREEN: at 480x854 the login CTA was below the fold — the photo band is
  now adaptive (single 132dp row on <700dp heights), verified CTA above fold.
  VERIFIED ON DEVICE (Android Pixel 7 + 480x854): system splash, in-app splash,
  login (both sizes), tour, home, category listing incl. out-of-stock card, PDP
  (in-stock and low-stock), cart, checkout address + review, order confirmation
  with Paid-by row, bottom nav. Evidence: docs/screenshots/phase6-after/,
  before-state in docs/screenshots/phase6-before/.
  NOT visually re-verified this phase (compiled + agent-described only): search
  results, orders list, account, coins, help, about, addresses, voice sheet.
  Regression: 41 tests / 0 failures; iOS + Android builds green.
- 2026-08-30 PHASE 5 Premium UI/UX pass — 40-finding audit (4 parallel reviewers
  over code + device renders). Honest-copy violations found and fixed: 63
  fabricated product ratings zeroed and RatingRow now renders nothing without a
  verified source; "Support replies in ~2 mins" (no SLA) removed; fake
  "Notify me / You're on the list" waitlist removed from the voice sheet;
  "Extra charges may apply" replaced with a computed free-delivery gap;
  "Trending today" renamed Bestsellers (it was never trend data); referral
  "Earn 12 coins" and Rate-us dead rows deleted; founder claims
  ("SAVE 8-20%", "India's first Voice Commerce") moved into BrandCopy config
  and raised as DECISION D6 (substantiate before launch).
  UX: home re-hierarchied to the mandated order with a 2-row header and
  tappable banners; PDP purchase controls pinned; checkout gained a 3-state
  step indicator, "To pay" on every step and real disabled semantics with
  reasons; cart/cart-bar counts unified; category grid moved to
  LazyVerticalGrid with stable keys; search spinner-flash fixed; categories tab
  migrated to StateHost; saved-addresses screen added (same repository as
  checkout). A11y: 44/48dp targets on ADD/stepper/links/back/search/mic,
  disabled semantics + stateDescription on PillButton, micro-copy floor raised
  to 11sp, OOS text no longer dimmed below legibility.
  Verified on BOTH platforms: iOS error+retry state via new
  TAZZZO_DEMO_FAIL_LOAD flag (i1), skeleton (i5), loaded home (i7), checkout
  review with To-pay + failure card (i4); Android home (a_home_after) and
  480x854 small screen (a_small_after) where the category grid now fits above
  the fold. Regression: 41 tests / 0 failures, iOS + Android builds green.
  Two self-caught regressions during the pass: hero banner clipped after the
  height change (fixed with clamped image height + single-line claim), and the
  voice banner CTA still said "Notify me" after the sheet lost that flow
  (relabelled "Learn more").
- 2026-08-30 Persistence (Android, Pixel 7 API 35): all 8 mandated process-death
  tests pass — cart restore; doctored-prefs stock/price/removal reconciliation
  with customer notice ("Rohu Fish no longer available; quantity reduced for
  Fresh Tomato; prices updated for Toned Milk"); typed address survives restart;
  recent search "aata" survives; checkout session NOT restored; coins 40→41
  survive. Evidence: docs/screenshots/android-phase4/p1–p15.
- 2026-08-30 Returning-user routing FIX: process-death test exposed users being
  returned to the login wall each launch; added persisted onboarded flag with
  tourSeen migration fallback; verified relaunch → Home with cart intact (p6).
- 2026-08-30 Search autofocus FIX: search field now requests focus on entry
  (keyboard raises immediately); typo search verified with real Android
  keyboard input (p8).
- 2026-08-30 Small-screen pass 480x854 @ 240dpi: home/category render without
  broken layout; location truncates with ellipsis; banner subtitle tight
  (noted for polish). Evidence: s1–s2.
- 2026-08-30 Accessibility atoms: content descriptions on mic/coins/stepper/
  back/cart-bar controls; 44dp minimum targets on mic and steppers. Full
  screen-reader audit still pending (P1).
- 2026-08-30 Final regression: 41 tests, 0 failures (XML-verified); iOS compile
  + Android APK build green in same run.
- 2026-08-29 Checkout paths on iOS simulator: docs/screenshots/35–40 incl.
  failure card + retry and success.
