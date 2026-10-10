# Production blockers — single source of truth

> **Phase status (2026-09-01):** UI freeze **REOPENED** by the design owner for
> a 5/5 experience pass. Target raised from 4.8/5 to 5/5. Backend integration
> remains frozen and is now gated on that pass as well.
> See `TAZZZO_5_STAR_EXPERIENCE_AUDIT.md`.
>
> **Prior (2026-08-31):** UI/UX Final Gate **PASSED**, UI **FROZEN**.
> Production readiness **NOT passed**. Next: backend integration, gated on the
> readiness report (`docs/BACKEND_INTEGRATION_READINESS.md`) and explicit GO.

Rule: never silently remove a blocker. Resolution requires a dated entry in
the PRODUCTION_READINESS.md verification log describing how it was verified.

## P0 — blocks any launch
- [x] Persistence — resolved 2026-08-30, verified by 8 on-device process-death
      tests (see PRODUCTION_READINESS.md log). Checkout session intentionally
      does not persist. NOTE: storage is plain NSUserDefaults/SharedPreferences;
      auth tokens now live in Keystore/Keychain (PR-03A), never here.
- [ ] Real backend: auth is REAL as of PR-03A (code + automated tests);
      real-device SMS sign-off is BLOCKED on a non-prod OTP mechanism (see
      "BACKEND CONTRACT / ENVIRONMENT REQUEST" in the PR-03A description).
      Catalogue, stock, serviceability, orders, payments all mocked. Contracts: docs/BACKEND_CONTRACTS.md.
- [ ] Real payments: COD only; UPI/card disabled placeholders.
- [ ] ORDER PAYABLE CONTRACT — LAUNCH BLOCKER (PR-08, 2026-10-02): the real COD order path is implemented and tested, but
      production placement stays OFF (`orderIntegration = false` for REMOTE; only a debug build can enable it through
      `OrderLaunchGate`). The backend order exposes only an item subtotal — no delivery/handling/platform fee, tax, benefit
      discount, COD charge or final payable — so a customer cannot be shown an authoritative amount due. Resolution: the backend
      defines and returns the customer-visible amount due (or contracts that every component is zero), then flip the capability.
- [ ] D4 delivery promise unapproved — app ships neutral "Fast delivery" copy.
- [ ] D5 coin economics unapproved — current values are dev config only.

## P1 — blocks production quality
- [x] Android compile + critical journey — verified 2026-08-30 (see log).
      Progress 2026-08-30: keyboard behaviour (typed search + address form),
      process-death matrix (8 tests), small-screen 480x854 pass, back
      navigation — all verified. Remaining: PDP tap-test, scroll/fling feel
      review, screen-reader (TalkBack) pass, physical-device verification.
- [ ] Automated UI/journey tests (46 domain tests exist; no UI harness yet).
      COUNT CORRECTED 2026-09-01: this line read "30 domain tests" while the
      repository actually held 41 (XML-verified); it was stale, not wrong at
      the time it was written. 41 → 46 in the same session with the D-1
      regression suite. See the PRODUCTION_READINESS.md log entry.
- [ ] Accessibility: atoms done; CONTRAST AUDIT DONE 2026-08-30 (4 token fixes,
      measured table in DESIGN_SPEC.md); screen-reader human pass + dynamic
      type still open.
- [ ] Analytics: vendor-neutral boundary + 13 funnel events wired 2026-08-30;
      vendor adapter + crash reporting still open (vendor unselected).
- [ ] Production photography licensing (category tiles are Wikimedia
      placeholders; attribution in docs/IMAGE_ATTRIBUTIONS.md).

## P1 — visual ceiling (new, Phase 6)
- [ ] **Campaign artwork [ASSET REQUIRED] (2026-09-06).** CampaignHero is live and
      data-driven (CampaignConfig, [MOCKED]); it renders typographically with
      licensed category art until production artwork is supplied. Date windowing
      is [BACKEND REQUIRED].
- [ ] **Delivery slots are [MOCKED] (2026-09-06).** Slot model now carries fee,
      reason, group and recommended; the chosen slot follows the order to the
      receipt. Real slots, fees and availability are [BACKEND REQUIRED]
      (serviceability). No ETA is fabricated — D4 stands.
- [ ] **Tip / delivery-partner gratuity — [BUSINESS DECISION], NOT BUILT.** Seen
      in the reference checkout; deliberately not copied. Needs revenue-
      recognition and reversal rules before any UI exists.
- [ ] **"Rate order" — not built.** No ratings backend; a rating control with no
      destination is a fake. [BACKEND REQUIRED].
- [x] F8 FIXED 2026-09-06 — cart's Club and Offers cards rendered narrower than
      the delivery/bill cards (double gutter). Found in a screenshot after all
      semantic assertions passed.
- [ ] **Promotions are [MOCKED] (2026-09-05).** A deterministic promotion engine now
      exists (config/PromotionEngine.kt, 22 unit tests) but the promotion SET,
      usage counters per user and real validity windows are [BACKEND REQUIRED].
      PromotionConfig.active is fixture data. PromotionPolicy.clubStacksWithPromotions
      = false is a [BUSINESS DECISION] default, same standing as D4/D5/D6.
- [x] F6 FIXED 2026-09-05 — "Start shopping" / "Continue shopping" landed on
      whichever bottom tab was last open (resetTo(Home) reset the stack, not the
      tab). Found by the on-device journey test after joining Club from Account.
      Same bug in 7 files; all now go through AppState.goHome(). GoHomeTest pins it.
- [ ] **5/5 EXPERIENCE PASS (opened 2026-09-01).** Four structural gaps found by
      source audit, none visible in a screenshot: (S1) no component has a
      designed pressed state — `collectIsPressedAsState` appears zero times
      across 65 clickable sites; (S2) navigation has no direction — Crossfade is
      the only transition, forward and back look identical; (S3) no state
      survives navigation — `rememberSaveable` appears zero times against 27
      scroll/query state holders, so scroll position, search query and filters
      are lost on every back; (S4) no haptics anywhere. Full inventory, per
      component and per screen before-scores, and the fix plan are in
      TAZZZO_5_STAR_EXPERIENCE_AUDIT.md.
- [x] REMOVED FROM HOME 2026-09-06: the hero carousel (and with it the
      "SAVE 8–20%" claim and the trade-dress photograph) is replaced by a
      config-driven CampaignHero. The asset still exists in MockCatalog banners
      and D6 remains UNRESOLVED for About / splash / login copy.
- [ ] Hero banner carries THIRD-PARTY TRADE DRESS (Aashirvaad, Daawat, Maggi,
      Tata Salt, Colgate, Fortune packaging) in Tazzzo's own marketing image.
      Brand/licensing exposure, not just a placeholder issue. Raised 2026-09-01.
- [ ] **Real product photography (ASSET DEPENDENCY, not an engineering gap).**
      The image ARCHITECTURE is complete and verified: fixed-aspect container,
      Fit-never-Crop, uniform neutral ground, loading skeleton, graceful
      unavailable state, and a `ProductImageLoader` seam driven by
      `Product.imageUrl` — real packshots drop in with NO screen redesign.
      What is missing is the photography itself (~63 SKUs) plus an image CDN
      and a Compose image-loading library. Until then products render an emoji
      placeholder, explicitly documented as temporary in docs/DESIGN_SPEC.md.
- [x] Re-verify search / orders / account / coins / help / about on device —
      DONE 2026-08-30 (docs/screenshots/qa-gate/).
- [x] Addresses + Voice sheet — verified on Android 2026-08-30 (final-gate);
      voice sheet's surviving "Cart ready in seconds" claim removed on sight.
- [ ] **Pre-launch verification gates** (do NOT reopen UI design for these):
      · Human TalkBack + VoiceOver session — service-on smoke test and full tree
        audit done; gesture traversal via adb blocked by the service's own dialog.
      · Dynamic type / font-scaling pass.
      · Physical Android device + physical iPhone QA (emulator/simulator only so far).

## P2 — polish (expanded by the Phase-5 UX audit — deferred items, not dropped)
- [ ] Shared PhoneOtpForm component (login flows aligned but still duplicated).
- [ ] Vector icon system to replace emoji iconography (cross-platform consistency,
      tinting, semantics). Needs a design asset decision.
- [x] Banner carousel — HorizontalPager landed 2026-08-30 (fixes opaque-
      crossfade ghosting AND adds swipe).
- [ ] Brand filter as multi-select sheet ("Brand ⌄" pattern) once catalogue grows.
- [x] Terms of Service & Privacy Policy pages — 2026-10-10: in-app Legal screen on
      `GET /v1/content/legal/{slug}` (plain text; "This document isn't available yet" on 404),
      linked from Login, Profile and About. Content must be PUBLISHED in the CMS before launch.
- [ ] Splash warm-start: prefetch home data during the logo beat.
- [x] OfflineBanner — DELETED 2026-10-10 (dead code; per-screen failure states remain the
      honest signal). The original blockers, kept for the record: OfflineBanner was built but not wired. Blocked on THREE things, recorded
      2026-09-01: (a) no connectivity source exists — needs an expect/actual
      ConnectivityObserver over ConnectivityManager / NWPathMonitor, verified on
      physical devices; (b) a placement decision on frozen screens, which is the
      design owner's call; (c) a policy decision on whether a global banner is
      wanted at all, given that per-screen LoadError.Kind.Network already covers
      the honest case and OS reachability lies about captive portals.
- [ ] "India's first Voice Commerce" + "SAVE 8–20%" are founder-supplied claims,
      now config-sourced (BrandCopy). DECISION D6: substantiate or amend before launch.

## P2 — original polish list
- [x] Palette (D1), tagline (D2), Poppins (D3) — RESOLVED in Phase 6: final
      ramp in docs/DESIGN_SPEC.md, tagline is the lockup line, Poppins adopted.
- [ ] Micro-interactions, transitions, checkout polish.
- [ ] Performance pass (emulator cold start is slow under swiftshader;
      profile on hardware).

## Taxonomy v0.9.0 conflicts (2026-09-06)

Raised after verifying `Tazzzo_Taxonomy_Handoff_for_Mobile_App.md` directly
against `Tazzzo_Taxonomy_V1_Master.csv` (293 rows), `Tazzzo_Taxonomy_V1_Master.json`
and `tazzzo-catalog-service/docs/openapi.json` (29 paths, 8 GET). None of these
is an app defect. All need a decision or backend work.

- [ ] **No browse, list or search endpoint. Blocks the category screen and
      search entirely.** Verified: `GET /api/v1/products` *requires*
      `canonicalKey` and returns a single `ProductResponse`, not a page. There
      is no node-children endpoint. The app currently renders both screens from
      `MockCatalog`, so this is invisible today and fatal at integration.
      Needs a contract change, which the handoff says is not scoped.
- [ ] **Every vertical id in the shipped master CSV is suffixed
      " (provisional)"** — all 293 rows, e.g. `TZV-000225 (provisional)`. That
      is branch status leaking into the identity column, and it contradicts the
      handoff's own stability contract. The app stores the bare id in
      `Product.verticalId`. If anyone keys on the raw CSV string it breaks the
      day a branch locks. Backend should split status out of the id column.
- [ ] **Fresh produce, dairy and pet care do not exist in v0.9.0 and were
      excluded by recorded decision — but they are the app's entire spine.**
      Verified absent: zero Dairy, Milk, Curd, Paneer, Dog or Cat verticals;
      the only produce-adjacent nodes are Frozen Vegetables, Nuts, Dried
      Fruits, Dates, Seeds and Juice & Fruit Drinks, all inside Food. Eggs DO
      exist (under Meat, Seafood & Eggs). `[BUSINESS DECISION]` — either the
      exclusion is reversed through the release machinery, or Tazzzo ships
      without the aisles its Home screen is built around. **Produce depth work
      is stopped pending this ruling; it was not expanded.**
- [ ] **The collections plane is not built.** Festival campaigns, offer rows
      and curated rails have no backend home. `CampaignConfig` and
      `PromotionConfig` are `[MOCKED]` locally and have nothing to bind to.
- [ ] **All 9 Pooja verticals are `PROPOSED — 50-SKU validation pending`**, as
      are all of Personal Care, Home Care, Meat and Health & Wellness (161 of
      293 rows). Only Staples (69) and Food (63) are conditionally locked.
      Names and structure in the Pooja aisle may still move.
- [ ] **Local-language product names need native review before launch.** The
      Kannada names planned for Bengaluru produce (Eerulli, Baale Hannu,
      Southekayi) are researched, not verified by a native speaker. A wrong
      word in a customer's own language is worse than English only. None have
      shipped yet.

### Correction to the handoff document itself
§1.2 states "there is no festive category" in a way that reads as though ritual
goods are absent. **`Pooja & Religious Needs` does exist** — a full Category
under Household & Lifestyle with 2 sub-categories and 9 verticals
(TZV-000225..TZV-000233), and §3 of the same document lists it. What does not
exist is *festive as a seasonal grouping*, which the document's own three-plane
rule correctly places in collections. The app now mirrors the real shape.

## UI redesign — decisions and defects (2026-09-06)

Raised while implementing the six supplied screen mockups. The redesign itself
is built and green; these are the things it cannot decide for you.

### Founder decisions
- [ ] **Tip `[BUSINESS DECISION]`.** Built and wired into the bill as its own
      row, added to the payable and never netted against savings. But there is
      **no payout rail**: nothing routes a tip to a delivery partner, and on
      Cash on Delivery it is cash handed to a person the app cannot account to.
      Copy states only that the amount is added to this order — the mockup's
      "100% of your tip goes directly to your rider" is a promise nobody can
      keep today. **Do not ship to production until a payout rail exists.**
- [ ] **Delivery promise (D4).** `AppConfig.deliveryPromise` is `Unknown`, so
      the Home delivery chip, the cart items-header ETA and the mockups' "10
      MINS" / per-item "8 MINS" all render **nothing**. The components are
      built and light up the moment a verified window per pincode exists.
- [ ] **Store hours.** The mockup's "24x7 STORE" chip is an operational
      commitment. Nothing models store hours, so it is not drawn.
- [ ] **Support SLA (D6).** The mockup's "Quick Resolution Promise — instant
      refund within 2 minutes" is not drawn. No refund pipeline, no ticketing,
      no signed SLA.
- [ ] **Privacy policy and terms.** No document exists at any URL the app
      knows. The Account row is not drawn. This is an independent launch
      blocker for an Indian consumer app, not a redesign item.
- [ ] **Bottom nav label.** The mockups rename the fifth tab "Order Again" →
      "Orders". Kept as "Order Again": the label feeds both the visible text
      and the contentDescription, and `InteractionSemanticsTest` asserts it.
      Changing it is one line plus one test — say the word.
- [ ] **`BrandCopy.savingsClaim` "SAVE 8–20%" (D6, pre-existing).** Still live
      on marketing surfaces with no substantiating dataset in the repo.

### Backend required before these screens can be finished
- [ ] **Payment gateway.** Until one exists: UPI and Card stay `Coming soon`,
      there are **no Google Pay / PhonePe / Paytm rows** (third-party trade
      dress), no saved-card vault, and **no CVV field in Tazzzo's own UI** —
      taking a CVV outside the gateway's checkout is a PCI violation. The card
      row says where card details are actually entered.
- [ ] **Refunds, e-gift cards, payment management** (Account). Each needs a
      backend that does not exist. Rows are not drawn rather than leading
      nowhere.
- [ ] **Rupee wallet.** The mockup's "Tazzzo Wallet · Add Balance · 5%
      cashback" is stored value — a regulated prepaid-instrument product in
      India. The card renders **Tazzzo Coins** instead.
- [ ] **WhatsApp transport.** Every Help contact route still ends in an
      "Opening WhatsApp… (demo)" dialog; there is no URL-opening
      expect/actual in commonMain. The redesign makes Help look considerably
      more capable, which widens the gap between what it offers and what it
      does.

### Defect found while reviewing, NOT introduced by this work
- [ ] **Coin balance has two sources that can disagree.** Account's balance
      card and Home's `CoinChip` read `app.user.coinBalance` (advanced by
      `OrderPlacement`); `CoinsScreen` reads `ServiceLocator.coins.getBalance()`,
      which only `credit()` moves. The two can show different balances for the
      same customer. Not fixed here — fixing it means choosing which is
      authoritative, which is a data decision, and doing it inside a UI wave
      would bury it.
