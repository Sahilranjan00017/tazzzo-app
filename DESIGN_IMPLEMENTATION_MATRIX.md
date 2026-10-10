# Tazzzo — Design Implementation Matrix

**Created:** 2026-09-21 · **Owner:** Principal Mobile Engineer / Technical Implementation Lead
**Branch:** `claude/tazzzo-design-implementation-16b61f` (worktree of `main` @ `a9329a5`)
**Baseline verified at creation:** `./gradlew :composeApp:iosSimulatorArm64Test` → **169 tests, 0 failures, 0 errors** (26 result XMLs parsed, not inferred from a green build).
**Android instrumented suite:** 21 tests in 9 classes exist (`composeApp/src/androidInstrumentedTest`); not run yet in this worktree — no emulator booted at creation time.

This is the single traceability record for the 50-screen design programme. Every T-ID from `TAZZZO_50_SCREEN_UX_BLUEPRINT.md` §B appears below exactly once. Nothing here claims fidelity that has not been measured.

---

## 0. HANDOFF INVENTORY — what was received, what is missing

The implementation contract (STEP 2) names five inputs. Status of each, searched across the repo, every branch (`main`, `origin/main`, `api-host-migration-tazzzo-com`), the parent directory, `~/Downloads`, `~/Desktop`, `~/Documents`:

| Required artifact | Status | Location | Notes |
|---|---|---|---|
| `TAZZZO_DESIGNER_MINDSET_SKILL.md` | **RECEIVED** | `~/Downloads/` (v1.0, 2026-09-21, 18,979 B) | Operating principles, component registry, correctness gates. Copied into `docs/design-handoff/` by this session. |
| `TAZZZO_50_SCREEN_UX_BLUEPRINT.md` | **RECEIVED** | `~/Downloads/` (v1.0, 2026-09-21, 55,493 B) | Per-screen job/widgets/data/states for T01–T50. Copied into `docs/design-handoff/`. |
| `DESIGN_MANIFEST.md` | **MISSING** | not found anywhere | Would carry design versions and the approved-asset index. Without it the "Design version" column below is `blueprint-1.0` (the only version identifier that exists). |
| `TZ-001 … TZ-050` screen specification files | **MISSING** | not found anywhere | The blueprint uses IDs **T01–T50**, not TZ-001–TZ-050. No per-screen spec files exist. The blueprint's §B entries are the only screen-level specification available; they are wireflow-level (widget order, data authority, states) — **they contain no dimensions, no spacing values, no type assignments, no colour assignments.** |
| Approved visual references (ChatGPT design images) and approved assets | **MISSING** | not found anywhere | **Zero design images** exist for any of the 50 screens. Companion `TAZZZO_Dynamic_Commerce_Platform_Flow_and_Architecture.md` (referenced by both received docs) is also absent. |

### Consequence, stated plainly

The contract says: *"Do not begin a screen until its required design artifacts are available. If an essential design or asset is missing, identify it explicitly instead of inventing a substitute and claiming fidelity."*

- STEP 5 (implement exact screen designs) and STEP 6 (pixel comparison, image-diff) **cannot begin for any screen** — there is no approved image to compare against. A "Visual comparison result" column that says PASS without a reference image would be a fabricated claim.
- STEP 4 (design system first) can proceed **only as an audit and consolidation of the existing token system** against the blueprint's registry (§5 of the Mindset doc). Adopting *new* token values requires the approved design tokens, which are not here. See §3 below.
- What *can* be done honestly now: this matrix (STEP 3), the existing-implementation audit per T-ID, the reusable-component gap list, the API dependency map, and the Android/iOS baseline screenshot capture of the *current* build so the before/after comparison has a "before".

### Blocking request to the design owner (ChatGPT / founder)

To start Batch 1 (five screens), supply for each screen in the batch:
1. The approved visual reference (PNG/PDF), at ~360dp and ~393dp widths, in every specified state.
2. The per-screen specification (TZ-0xx) with dimensions, spacing, type roles, colour tokens and copy.
3. `DESIGN_MANIFEST.md` with design version per screen and the asset index.
4. Any approved asset (hero art, packshots, icons) with licence metadata.

Suggested Batch 1 (blueprint §H recommended order, foundation-first, P0): **T01, T02, T03, T04, T07** — or whatever five ChatGPT designs first. This document will be updated the moment artifacts land.

---

## 1. Column definitions

| Column | Meaning |
|---|---|
| Design version | Version of the approved design. Only `blueprint-1.0` exists today (the wireflow-level spec). Becomes `TZ-0xx vN` when a spec file arrives. |
| Approved visual reference | Path to the approved image. `MISSING` until supplied. |
| Specification file | `blueprint §B/Txx` = the only spec present. `MISSING (TZ-0xx)` for the dedicated file. |
| Implementation files | Existing code that implements (fully or partly) the screen today. Paths relative to `composeApp/src/commonMain/kotlin/com/tazzzo/app/`. |
| Reusable components | Existing shared components used, and blueprint-registry components still missing (marked `NEW:`). |
| Required APIs | Authority per blueprint legend (CAT/PRC/INV/LOC/ID/CLUB/ORD/PAY/SUP/CMS) and the existing repository interface. `[MOCKED]` when only a `Mock*` implementation exists — **true for every repository today** (`ServiceLocator`, `data/repository/Repositories.kt:173`). |
| Implementation status | `EXISTS` (route + screen live, functionality real within the mock seam) · `PARTIAL` (route or some widgets exist; blueprint widgets/states missing) · `MISSING` (no route, no screen) · `GATED` (blueprint says do not build until a backend/business decision exists). |
| Android / iOS screenshot | Path to a screenshot of *this* implementation. `PENDING` until captured in this programme; historical screenshots in `docs/screenshots/` are listed as `baseline:` for the before-state only. |
| Visual comparison | Result of image comparison vs approved reference. `N/A — no reference` until one exists. Never `PASS` from intuition. |
| Functional test | Existing automated coverage that exercises the screen. Unit = `commonTest`; UI = `androidInstrumentedTest`. |
| Accessibility | What has been verified (semantics-tree assertions, contrast) vs open (TalkBack/VoiceOver human pass, 200% text). |
| Remaining issues | Gaps against the blueprint, business decisions, backend dependencies. |

**Status labels used in text** (from Mindset §7): `[IMPLEMENTED]` `[MOCKED]` `[BACKEND REQUIRED]` `[ASSET REQUIRED]` `[DEVICE REQUIRED]` `[BUSINESS DECISION]` `[LEGAL REVIEW]`.

---

## 2. THE MATRIX — T01 to T50

Summary count (existing code vs blueprint): **EXISTS 22 · PARTIAL 14 · MISSING 7 · GATED 6 · T50 split (T50a MISSING/GATED, T50b PARTIAL) 1** (= 50). EXISTS means the route and screen work within the mock seam — it is **not** a design-fidelity claim; no screen has been compared to an approved design.

### Onboarding & login (T01–T06)

| Field | T01 — Splash / launch routing [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T01 · MISSING (TZ-001) |
| Implementation files | `ui/splash/SplashScreen.kt` (175 L) · routing in `PersistenceRunner.kt`, `AppState.kt` (`isOnboarded`, `restoreFromDisk`) |
| Reusable components | `LogoImage`, `TazWordmark` · NEW: none required |
| Required APIs | LOCAL (PersistentStore) · ID `AuthRepository` [MOCKED] · serviceability check **not implemented** (LOC) |
| Implementation status | **EXISTS** — logo beat, routes onboarded users to Home, first-run to Onboarding; restores cart/session/membership with reconciliation notice |
| Android screenshot | baseline: `docs/screenshots/phase6-after/d1_splash.png` · PENDING (this programme) |
| iOS screenshot | baseline: `docs/screenshots/final-gate/ios/frame_01.png` · PENDING |
| Visual comparison | N/A — no reference |
| Functional test | Unit: `PersistenceTest` (restore/reconcile) · UI: `JourneyWalkthroughTest` launches through splash |
| Accessibility | Logo has contentDescription; reduced-motion gate via `MotionSettings` (OS wiring open, E6) |
| Remaining issues | No expired-auth or serviceability routing (no real auth) `[BACKEND REQUIRED]` · corrupted-cache state not designed · time-to-first-usable `[DEVICE REQUIRED]` |

| Field | T02 — Welcome / value proposition [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T02 · MISSING (TZ-002) |
| Implementation files | `ui/onboarding/OnboardingScreen.kt` (604 L) — photo-wall marquee, logo badge, tagline, phone→OTP inline, "Skip for now" |
| Reusable components | `MarqueeRow`, `WallTile`, `LogoImage`, `PillButton`, `DialPrefix` |
| Required APIs | ID `AuthRepository.requestOtp` [MOCKED] · LEGAL (Terms/Privacy URLs) **do not exist** |
| Implementation status | **PARTIAL** — welcome + login are one screen today; blueprint separates T02 (welcome) from T03 (phone) and T04 (OTP). Privacy/Terms links removed until real pages exist (BLOCKERS P2) |
| Android screenshot | baseline: `docs/screenshots/phase6-after/b2_login_new.png` · PENDING |
| iOS screenshot | baseline: `docs/screenshots/final-gate/ios/frame_02.png` · PENDING |
| Visual comparison | N/A — no reference |
| Functional test | UI: `JourneyWalkthroughTest` (skip path) · Unit: none screen-specific |
| Accessibility | Wall images decorative (`null` desc); CTA ≥ touch target; small-screen 480×854 verified 2026-08-30 |
| Remaining issues | Hero photography is Wikimedia category art `[ASSET REQUIRED]` · Terms/Privacy `[LEGAL REVIEW]` · "browse-as-guest" policy `[BUSINESS DECISION]` (currently allowed) · "India's first Voice Commerce" claim D6 |

| Field | T03 — Phone number entry [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T03 · MISSING (TZ-003) |
| Implementation files | `ui/onboarding/OnboardingScreen.kt` §4 (phone form) · `ui/onboarding/LoginScreen.kt` (422 L, secondary entry from Account) |
| Reusable components | `DialPrefix`, `PillButton(loadingText)` · NEW: shared `PhoneOtpForm` (BLOCKERS P2 — two duplicated forms today) |
| Required APIs | ID `AuthRepository.requestOtp` [MOCKED — always succeeds after 350 ms] |
| Implementation status | **PARTIAL** — validation, loading state, failure line exist; no rate-limit state, no marketing-consent separation, not a standalone step |
| Android screenshot | baseline: `docs/screenshots/phase6-after/a2_login.png` · PENDING |
| iOS screenshot | PENDING |
| Visual comparison | N/A — no reference |
| Functional test | UI: keyboard behaviour verified 2026-08-30 (typed search + address form; login form not asserted) |
| Accessibility | Labelled field; CTA height stable while loading |
| Remaining issues | Rate-limit/offline states `[BACKEND REQUIRED]` · duplicate form implementations (consolidate before restyling) |

| Field | T04 — OTP verification [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T04 · MISSING (TZ-004) |
| Implementation files | `ui/onboarding/OnboardingScreen.kt` (OTP step, resend countdown) · `ui/onboarding/LoginScreen.kt` |
| Reusable components | `PillButton` · NEW: accessible code input with autofill |
| Required APIs | ID `AuthRepository.verifyOtp` [MOCKED — any 4 digits verify; `AppConfig.demoMode` shows the hint] |
| Implementation status | **PARTIAL** — verify, resend countdown, wrong-code distinction exist; demo-mode badge present; no backend-driven rate limit, no SMS autofill |
| Android screenshot | PENDING |
| iOS screenshot | PENDING |
| Visual comparison | N/A — no reference |
| Functional test | none screen-specific |
| Accessibility | Not audited for VoiceOver focus order |
| Remaining issues | Real OTP `[BACKEND REQUIRED]` · test-vs-live mode must be visibly distinct (demo hint exists; formal badge per T32 pattern needed) |

| Field | T05 — Location permission & serviceability [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T05 · MISSING (TZ-005) |
| Implementation files | **none** — location hard-coded `"HSR Layout, Bengaluru"` (`AppState.kt` user default); `Address.isServiceable` flag exists on the model; `UnserviceableBanner` in `CheckoutScreen.kt:624` |
| Reusable components | NEW: permission rationale card, "Use current location / Enter PIN" pair, serviceability result, unsupported-area alternative |
| Required APIs | LOC serviceability by PIN/geo — **no repository interface exists** · OS location permission expect/actual — **none exists** |
| Implementation status | **MISSING** |
| Android screenshot | — |
| iOS screenshot | — |
| Visual comparison | N/A |
| Functional test | none |
| Accessibility | — |
| Remaining issues | Needs `ServiceabilityRepository` contract `[BACKEND REQUIRED]` · platform location permission plumbing (Android + iOS separately) · GPS timeout/denial states |

| Field | T06 — Add address [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T06 · MISSING (TZ-006) |
| Implementation files | `ui/checkout/CheckoutScreen.kt` `AddressStep` (645–804) inline add form (label, line1, line2, pincode) · `ui/home/AddressesScreen.kt` (126 L, list only) |
| Reusable components | `TazTopBar`, `PillButton` · NEW: `AddressCard` (registry), recipient/phone fields, address-type selector, delivery-note field |
| Required APIs | ID+LOC `AddressRepository.addAddress` [MOCKED — serviceability decided by pincode prefix fixture] |
| Implementation status | **PARTIAL** — form exists only inside checkout; no standalone add/edit route; no recipient, phone, type or note fields; no map |
| Android screenshot | baseline: `docs/screenshots/android-phase4/p10_addr_form.png` · PENDING |
| iOS screenshot | PENDING |
| Visual comparison | N/A — no reference |
| Functional test | UI: `CheckoutJourneyTest` (address add + persistence) · process-death matrix 2026-08-30 |
| Accessibility | IME/keyboard behaviour verified 2026-08-30 |
| Remaining issues | Standalone route `Screen.AddAddress` needed · geocode/Maps `[BACKEND REQUIRED]`/`[BUSINESS DECISION]` (no Maps API key) · edit-vs-new |

### Shopping & discovery (T07–T18)

| Field | T07 — Home [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T07 · MISSING (TZ-007) |
| Implementation files | `ui/home/HomeTabContent.kt` (972 L): `HomeHeader`, `HomeSearchBar`+`RotatingSearchHint`, `CategoryGrid` (4×2), `CampaignHero`, `PriceBandDealsPanel`, `CouponRail`, Buy-Again rail, `EssentialsGrid`, `MasterListButton`, `RestoreNoticeBanner` · shell `ui/home/MainScaffold.kt` (`BottomNavBar`, `CartBar`) |
| Reusable components | `ProductCard`, `ProductRail`, `CategoryTile`, `CoinChip`, `MicButton`, `CartBar`, `SectionHeader`, skeletons · NEW: `DeliveryChip` slot-aware state ("Choose delivery"), `BuyAgainRail` as named registry component (exists inline), `MemberOffer` |
| Required APIs | CMS/BFF manifest **does not exist** (`CampaignConfig`, `PromotionConfig` are local fixtures [MOCKED]) · CAT `getCategories/getBestsellers/getDeals` [MOCKED] · ORD `getOrders` [MOCKED] · LOC (none) · CLUB `LocalMembershipRepository` [MOCKED, local storage — see T34 note] |
| Implementation status | **EXISTS** — every rail resolves to a real route; counts derived; no ETA (D4); hero is config-driven and removes itself when no campaign |
| Android screenshot | baseline: `docs/screenshots/redesign/c3_01_home_top.png`, `c3_02_home_deals.png` · PENDING |
| iOS screenshot | baseline: `docs/screenshots/phase5-after/i7_home_final.png` · PENDING |
| Visual comparison | N/A — no reference |
| Functional test | UI: `HomeEvidenceTest`, `RedesignEvidenceTest`, `InteractionSemanticsTest`, `NavigationJourneyTest` · Unit: `HomeFeedDedupeTest`, `CategoryArtTilesTest`, `CatalogCountsTest` |
| Accessibility | Semantics tree asserted (actionable grid tiles exactly once; duplicate-label rule) · ambient motion gated · TalkBack human pass **open** |
| Remaining issues | Header shows address but no selected-slot/"Choose delivery" chip · Buy Again shows only if history (correct) · campaign art `[ASSET REQUIRED]` · 41/72 SKUs have photos, rest emoji `[ASSET REQUIRED]` · `BrandCopy.savingsClaim` "SAVE 8–20%" still config-live (D6) |

| Field | T08 — Search entry & suggestions [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T08 · MISSING (TZ-008) |
| Implementation files | `ui/home/SearchScreen.kt` (443 L): `SearchHeader`, recent searches (persisted, removable), `SearchChipRows` (popular), trending rail · `data/search/SearchEngine.kt` |
| Reusable components | `TazChip`, `MicButton`, `ProductRail` · NEW: grouped suggestions (Products/Categories/Brands) |
| Required APIs | REMOTE (release): `GET /v1/search` [REAL, 2026-10-10 — products only; `ui/catalog/RemoteSearchScreen.kt` + `data/catalog/ProductSearch.kt`; recents recorded only after a query's page returns products] · MOCK: local `SearchEngine` over `MockCatalog` · LOCAL recents (`PersistentStore`) [IMPLEMENTED] |
| Implementation status | **EXISTS** — entry and results share one screen; recents persist across process death (verified `p9_recents_after_kill.png`) |
| Android screenshot | baseline: `docs/screenshots/android-phase4/p7_recents.png` · PENDING |
| iOS screenshot | baseline: `docs/screenshots/phase6-after/r1_search.png` · PENDING |
| Visual comparison | N/A — no reference |
| Functional test | UI: `NavigationJourneyTest` (query survives navigation) · Unit: `ShoppingListTest`, search-evidence log |
| Accessibility | Mic action is independent and actionable (F4 regression fixed) |
| Remaining issues | No grouped suggestions · "trending" chips are fixture — must be removed or grounded `[BACKEND REQUIRED]` · Hindi synonyms/typo tolerance partial (`SearchEngine` has synonym table; no fuzzy) · analytics: `AppState.recordSearch` passes the query, but `AnalyticsPolicy.releaseSafe` strips the `query` key in release (debug log only) |

| Field | T09 — Search results [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T09 · MISSING (TZ-009) |
| Implementation files | `ui/home/SearchScreen.kt` results grid · `ui/common/Filters.kt` (`FilterBar`, `SortSheet`) |
| Reusable components | `ProductCard`, `FilterBar`, `SortSheet`, `EmptyState` |
| Required APIs | REMOTE: `GET /v1/search` [REAL — cursor-paged product cards with price/MRP/stock; 429 Retry-After honoured] · MOCK: CAT search fixture |
| Implementation status | **EXISTS** — count, sort/filter, empty-state recovery; query/filter/scroll preserved via `SaveableStateHolder` |
| Android screenshot | baseline: `docs/screenshots/android-phase4/p8_aata_results.png` · PENDING |
| iOS screenshot | baseline: `docs/screenshots/phase6-after/r2_search_results.png` · PENDING |
| Visual comparison | N/A — no reference |
| Functional test | UI: `NavigationJourneyTest` · Unit: `DealsFilterTest` (filter engine) |
| Accessibility | Filter chips actionable; not human-traversed |
| Remaining issues | Spelling-correction-with-undo not built · applied-filter chips row not built · partial-results state |

| Field | T10 — All Categories [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T10 · MISSING (TZ-010) |
| Implementation files | `ui/home/CategoriesTabContent.kt` (357 L): search field, `GroupPills` (filter), `PromotionSpotlight`, per-group sections with derived counts, 4-col `CategoryTile` grid · `data/repository/Taxonomy.kt` |
| Reusable components | `CategoryTile`, `TazChip`, `CategoriesSkeleton` |
| Required APIs | CAT taxonomy [MOCKED — v0.9.0 aligned, `TaxonomyAlignmentTest`] · CMS promo spotlight from `PromotionConfig` [MOCKED] |
| Implementation status | **EXISTS** |
| Android screenshot | baseline: `docs/screenshots/redesign/r1_categories.png` · PENDING |
| iOS screenshot | PENDING |
| Visual comparison | N/A — no reference |
| Functional test | UI: `RedesignEvidenceTest` · Unit: `TaxonomyAlignmentTest`, `CatalogCountsTest`, `CategoryArtTilesTest` |
| Accessibility | Tile labels unique per module (rule in TESTING.md) |
| Remaining issues | Category with zero serviceable stock state not designed (no INV feed) · tile photography is Wikimedia `[ASSET REQUIRED]` · Fresh/Dairy/Pet absent from taxonomy v0.9.0 `[BUSINESS DECISION]` (BLOCKERS) |

| Field | T11 — Category / PLP [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T11 · MISSING (TZ-011) |
| Implementation files | `ui/home/CategoryDetailScreen.kt` (324 L): subcategory sidebar, `FilterBar`, 2-col grid, `ProductGridSkeleton` |
| Reusable components | `ProductCard`, `FilterBar`, `SortSheet`, `CartBar`, `SidebarEntry` |
| Required APIs | CAT `getProducts(categoryId, sub)` [MOCKED] · INV via `Product.availability` fixture |
| Implementation status | **EXISTS** — stable keys, OOS semantics, scroll retention verified (`category_scroll_position` test) |
| Android screenshot | baseline: `docs/screenshots/c3-after/c3_04_category_product_grid.png` · PENDING |
| iOS screenshot | baseline: `docs/screenshots/phase6-after/d4_category.png` · PENDING |
| Visual comparison | N/A — no reference |
| Functional test | UI: `NavigationJourneyTest`, `InteractionSemanticsTest` · Unit: `UnitPriceTest` |
| Accessibility | Stepper hit-target 48dp verified (`StepperTouchTarget`) |
| Remaining issues | Applied-filter chip row absent · pagination N/A on fixture · 2-col-only-when-controls-fit rule not adaptive (fixed 2 cols; 480×854 pass exists) |

| Field | T12 — Deals / price-drop collection [P1] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T12 · MISSING (TZ-012) |
| Implementation files | `ui/home/DealsTabContent.kt` (244 L): `CampaignBand`, `OfferCard`, filtered product grid · `HomeTab.DEALS` |
| Reusable components | `ProductCard`, `DiscountBadge`, `SavingsBadge` · NEW: campaign-terms disclosure link |
| Required APIs | PRC `getDeals()` [MOCKED — MRP vs price only; **no price history**] · CMS campaign [MOCKED] |
| Implementation status | **EXISTS** with caveat — shows MRP comparison; must not say "price drop" without history (currently does not) |
| Android screenshot | baseline: `docs/screenshots/n2-after/n2_01_deals_tab.png` · PENDING |
| iOS screenshot | PENDING |
| Visual comparison | N/A — no reference |
| Functional test | UI: `DealsTabTest` · Unit: `DealsFilterTest` |
| Accessibility | not human-traversed |
| Remaining issues | Terms/cap link per campaign not built · validated price history `[BACKEND REQUIRED]` · location eligibility |

| Field | T13 — Festival / seasonal collection [P1] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T13 · MISSING (TZ-013) |
| Implementation files | `ui/home/HomeTabContent.kt` `CampaignHero` (682–802) · `config/CampaignConfig.kt` · `data/model/Campaign.kt` — hero only; **no collection screen** |
| Reusable components | `CampaignHero` · NEW: `FestivalHero` full page, `NeedTile`, `CollectionHeader` |
| Required APIs | CMS schedule [MOCKED, date windowing `[BACKEND REQUIRED]`] · CAT/INV/PRC |
| Implementation status | **PARTIAL** — hero CTA routes to a category; no dedicated collection destination with shop-by-need |
| Android screenshot | PENDING |
| iOS screenshot | PENDING |
| Visual comparison | N/A |
| Functional test | UI: `HomeEvidenceTest` (hero label uniqueness) |
| Accessibility | hero image decorative, label distinct |
| Remaining issues | New route `Screen.Collection(campaignId)` · original artwork `[ASSET REQUIRED]` · seasonal claims `[LEGAL REVIEW]` |

| Field | T14 — Product Detail Page [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T14 · MISSING (TZ-014) |
| Implementation files | `ui/home/ProductDetailScreen.kt` (375 L): `ProductImage` hero, name/brand/unit, `PriceColumn`, `RatingRow` (self-hides without source), highlights, `InfoCard`, sticky `PurchaseFooter` |
| Reusable components | `ProductImage`, `PriceColumn`, `QuantityStepper`, `RatingRow`, `InfoCard` · NEW: `ProductGallery` (multi-image + count), `VariantPill`, `FactSection`/`NutritionTable`, similar-products rail |
| Required APIs | CAT `getProduct(id)` [MOCKED] · verified facts (ingredients, nutrition, shelf life) — **not in model** · INV/PRC/LOC |
| Implementation status | **PARTIAL** — essentials + sticky ADD exist; no gallery, no variants, no sourced facts, no related goods, no policy links |
| Android screenshot | baseline: `docs/screenshots/photos/pdp_and_rail_with_bundled_photos.png` · PENDING |
| iOS screenshot | baseline: `docs/screenshots/phase5-after/i3_pdp_after.png` · PENDING |
| Visual comparison | N/A — no reference |
| Functional test | UI: `NavigationJourneyTest` (PDP back restores grid offset) · Unit: `ProductArtTest` |
| Accessibility | Sticky footer above safe area; rating hidden when no source (no fake stars) |
| Remaining issues | Product-fact schema `[BACKEND REQUIRED]` · 31/72 SKUs lack photos `[ASSET REQUIRED]` · `Product.rating` field exists in fixture — must stay hidden until a real review source exists |

| Field | T15 — Variant / pack selector [P0 when multi-variant] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T15 · MISSING (TZ-015) |
| Implementation files | **none** — `Product` has no variant grouping (grep `variant` in `ui/` = 0) |
| Reusable components | NEW: `VariantPill`, variant sheet |
| Required APIs | CAT variant groups **not in model or contract** · INV/PRC per variant |
| Implementation status | **MISSING** |
| Android/iOS screenshot | — |
| Visual comparison | N/A |
| Functional test | none |
| Remaining issues | Requires catalogue variant model `[BACKEND REQUIRED]`; taxonomy v0.9.0 does not define variant axes (BLOCKERS "unresolved rice axes") |

| Field | T16 — Buy Again collection [P1] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T16 · MISSING (TZ-016) |
| Implementation files | `ui/home/OrderAgainTabContent.kt` (204 L) · `ui/home/MasterListScreen.kt` (355 L, "regulars" list from history) · `data/search/ShoppingList.kt` |
| Reusable components | `OrderAgainCard`, `MasterListRow`, `EmptyOrderAgainState` |
| Required APIs | ORD `getOrders` [MOCKED] · current CAT/INV/PRC reconciliation (`CartRestore.reconcile` pattern) |
| Implementation status | **EXISTS** — hidden from Home without history; reorder uses current prices (`OrderPlacementTest`) |
| Android screenshot | baseline: `docs/screenshots/final-gate/android/a_orders.png` · PENDING |
| iOS screenshot | PENDING |
| Visual comparison | N/A — no reference |
| Functional test | Unit: `MasterListTest`, `ShoppingListTest` · UI: `JourneyWalkthroughTest` |
| Accessibility | reorder pill labelled |
| Remaining issues | Discontinued-SKU / new-pack disclosure UI partial (reconcile logic exists, per-item flagging in Buy Again view not surfaced) |

| Field | T17 — Wishlist / saved items [P1] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T17 · MISSING (TZ-017) |
| Implementation files | **none** (redesign spec §2 explicitly omitted the mockup's "12 Items" wishlist as unverifiable) |
| Reusable components | NEW: save toggle on `ProductCard`/PDP, saved-list screen |
| Required APIs | ID saved list — **no repository** · CAT/INV/PRC |
| Implementation status | **MISSING** |
| Remaining issues | Guest policy `[BUSINESS DECISION]` · persistence model · `[BACKEND REQUIRED]` for sync |

| Field | T18 — Voice shopping [P1] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T18 · MISSING (TZ-018) |
| Implementation files | `ui/voice/VoiceSheet.kt` (165 L, `VoiceComingSoonSheet`) · `MicButton` · `VoiceCommerceBannerV3` |
| Reusable components | `MicButton` · NEW: transcription status, candidate-match clarifier, editable draft basket |
| Required APIs | STT + CAT match — **none exists** |
| Implementation status | **GATED** — honest "Coming soon" sheet; founder instruction 2026-09-06: *do not change the voice feature*. Blueprint T18 is a full capture flow → `[BACKEND REQUIRED]` + founder release of the freeze |
| Android screenshot | baseline: `docs/screenshots/final-gate/android/a_voice.png` |
| iOS screenshot | baseline: `docs/screenshots/final-gate/ios/frame_…` (burst) |
| Visual comparison | N/A |
| Functional test | UI: `InteractionSemanticsTest` (mic actionable, F4) |
| Remaining issues | Frozen by instruction; D6 claim "India's first Voice Commerce" |

### Cart & offers (T19–T22)

| Field | T19 — Cart [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T19 · MISSING (TZ-019) |
| Implementation files | `ui/home/CartScreen.kt` (1,050 L): `DeliveryChoiceCard`, `SavingsHeader`, `FreeDeliveryMilestone`, item rows, `PromotionsPanel` (coupon), `ClubCartPrompt`, `CartUpsellRail`, `CarryBagRow`, `BillRow`s, sticky CTA · `config/BillCalculator.kt`, `config/PromotionEngine.kt` |
| Reusable components | `QuantityStepper(stepperInlineWidth)`, `ProductImage`, `PillButton`, `TazGroupedCard` · registry names map: `BillBreakdown`≈BillRow set, `SavingsSummary`≈SavingsHeader, `ThresholdProgress`≈FreeDeliveryMilestone, `CouponInput`≈PromotionsPanel |
| Required APIs | **Server quote PRC** — today `BillCalculator` runs on-device [MOCKED, deterministic, 22+ unit tests] · INV `validateCart` [MOCKED] · LOC slots [MOCKED] · CLUB [MOCKED, local] |
| Implementation status | **EXISTS** — C1/C2 verified on device; F7/F8 fixed; savings headline/card/bill agree by construction (single `BillSummary`) |
| Android screenshot | baseline: `docs/screenshots/c2-after/c2_01_cart_delivery_choice.png`, `club-after/11_cart_fixed.png` · PENDING |
| iOS screenshot | baseline: `docs/screenshots/phase6-after/f2_cart.png` · PENDING |
| Visual comparison | N/A — no reference |
| Functional test | UI: `CartRowRegressionTest`, `CartPromotionJourneyTest` · Unit: `BillCalculatorTest`, `PromotionEngineTest`, `FreeDeliveryProgressTest`, `CartUpsellTest`, `DeliveryFeeTest` |
| Accessibility | Row name/price never starved (regression test) · 200% text **open** |
| Remaining issues | Quote authority is client-side until BFF exists `[BACKEND REQUIRED]` · price-change acknowledgement lives in checkout, not cart (see T30) · `clubStacksWithPromotions=false` `[BUSINESS DECISION]` |

| Field | T20 — Offers & coupons [P0 if coupons launched] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T20 · MISSING (TZ-020) |
| Implementation files | `ui/home/CartScreen.kt` `PromotionsPanel` (717–812) — inline; applied/available/loser reasons from `PromotionEngine` |
| Reusable components | NEW: dedicated offers screen/sheet, `OfferEligibility`, `PromotionRow` |
| Required APIs | PRC promotions + per-user usage counters `[BACKEND REQUIRED]`; set is `PromotionConfig.active` fixture [MOCKED] |
| Implementation status | **PARTIAL** — engine + inline panel exist; no dedicated surface grouping available/unavailable with reasons and terms |
| Android screenshot | baseline: `docs/screenshots/c1-after/c1_03_cart_coupon_contest.png` |
| iOS screenshot | PENDING |
| Functional test | UI: `CartPromotionJourneyTest` · Unit: `PromotionEngineTest` |
| Remaining issues | New route `Screen.Offers` · usage limits/expiry `[BACKEND REQUIRED]` |

| Field | T21 — Offer details [P1] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T21 · MISSING (TZ-021) |
| Implementation files | **none** — `Promotion` model carries title/min/cap; no details surface |
| Reusable components | NEW: terms sheet with eligibility/cap/validity/exclusions/stacking |
| Required APIs | PRC + approved CMS copy [MOCKED fixture] |
| Implementation status | **MISSING** |
| Remaining issues | Depends on T20; approved T&C copy `[LEGAL REVIEW]` |

| Field | T22 — Reward / gift selector [P1; only when redemption live] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T22 · MISSING (TZ-022) |
| Implementation files | none (model only: `MembershipReward`, `MembershipRewardStatus` in `data/model/Membership.kt`) |
| Required APIs | CLUB reward ledger with exactly-once redeem `[BACKEND REQUIRED]` |
| Implementation status | **GATED** — blueprint: "hide live redemption when backend absent" |
| Remaining issues | Fruit-reward cap/economics `[BUSINESS DECISION]` |

### Checkout & payment (T23–T30)

| Field | T23 — Checkout address selection [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T23 · MISSING (TZ-023) |
| Implementation files | `ui/checkout/CheckoutScreen.kt` `AddressStep` (645–804), `SelectionRow`, `UnserviceableBanner`, `StepIndicator` · `CheckoutSession.kt` (`selectAddress` invalidates slot) |
| Reusable components | `SelectionRow`, `RadioDot`, `StatusChip` · registry `AddressCard` |
| Required APIs | ID+LOC `AddressRepository.getAddresses` [MOCKED] |
| Implementation status | **EXISTS** |
| Android screenshot | baseline: `docs/screenshots/phase6-after/g3_checkout_address.png` · PENDING |
| iOS screenshot | baseline: `docs/screenshots/phase6-after/h0_checkout_addr.png` · PENDING |
| Functional test | UI: `CheckoutJourneyTest` · Unit: `CheckoutTest` |
| Accessibility | Selection rows have role/state; back unwinds steps (`handleSystemBack`) |
| Remaining issues | Recipient/instructions summary not shown here (instructions are on Review) |

| Field | T24 — Choose delivery slot [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T24 · MISSING (TZ-024) |
| Implementation files | `ui/checkout/CheckoutScreen.kt` `SlotStep` (805–873) · also chosen from cart `DeliveryChoiceCard` · `data/model/Checkout.kt DeliverySlot(fee, feeReason, group, recommended)` |
| Reusable components | `SelectionRow` · registry `SlotCard`, `SlotDayGroup`, `FeeExplainer` (fee reason inline today) |
| Required APIs | LOC `CheckoutRepository.getSlots(addressId)` [MOCKED — three fixed windows 6–9 AM · 12–3 PM · 6–9 PM] |
| Implementation status | **EXISTS** [MOCKED] — slot follows order to receipt (`c2_05_order_detail.png`) |
| Android screenshot | baseline: `docs/screenshots/slots/c2_02_slot_choice.png` · PENDING |
| iOS screenshot | baseline: `docs/screenshots/phase6-after/h1_slot.png` · PENDING |
| Functional test | UI: `CheckoutJourneyTest` · Unit: `DeliveryFeeTest`, `CheckoutTest` |
| Remaining issues | Real capacity/expiry/re-selection `[BACKEND REQUIRED]` · no-slots and expired-during-review states not designed |

| Field | T25 — Payment methods [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T25 · MISSING (TZ-025) |
| Implementation files | `ui/checkout/CheckoutScreen.kt` `PaymentStep` (874–934) · `CheckoutRepository.getPaymentMethods` |
| Reusable components | `SelectionRow` · registry `PaymentMethodRow`, `FinalPayBar` |
| Required APIs | PAY methods [MOCKED — COD enabled; UPI/CARD `enabled=false`, note "Coming soon"] |
| Implementation status | **EXISTS** — unsupported methods visibly unavailable (blueprint-compliant); no CVV in app (PCI) |
| Android screenshot | baseline: `docs/screenshots/phase6-after/h2_payment.png` · PENDING |
| iOS screenshot | PENDING |
| Functional test | UI: `CheckoutJourneyTest` |
| Remaining issues | Gateway `[BACKEND REQUIRED]` · COD fee eligibility `[BUSINESS DECISION]` · tip row `[BUSINESS DECISION]` (built, no payout rail — do not ship) |

| Field | T26 — Payment processing [P0 when online] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T26 · MISSING (TZ-026) |
| Implementation files | Grocery: none (COD only). Club: `ui/club/ClubCheckoutScreen.kt` `PhaseMessage`/`StatusCard` (creating → gateway → verifying) · `membership/MembershipPurchase.kt` · `data/repository/PaymentGateway.kt` (`MockPaymentGateway`, `isTestMode`) |
| Reusable components | registry `PaymentStatus` — exists for Club only; must be shared with grocery when online payments exist |
| Required APIs | PAY create-order / verify `[BACKEND REQUIRED]`; Razorpay-shaped mock [MOCKED] |
| Implementation status | **PARTIAL** — pattern exists for ₹99 Club; grocery flow has no online payment |
| Functional test | Unit: `MembershipTest` (purchase state machine) |
| Remaining issues | Shared component extraction · backend webhook reconciliation |

| Field | T27 — Final checkout review [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T27 · MISSING (TZ-027) |
| Implementation files | `ui/checkout/CheckoutScreen.kt` `ReviewStep` (935–1153), `ReviewCard`, `IssuesCard` · `order/OrderPlacement.kt` (idempotency key, replay) |
| Reusable components | `ReviewCard`, `BillRow` (duplicated from Cart? — check for a second bill renderer; STEP 4 consolidation target) |
| Required APIs | `CheckoutRepository.validateCart` + `placeOrder(OrderRequest)` → `Placed/Rejected/Failed` [MOCKED] |
| Implementation status | **EXISTS** — revalidation before commit, idempotent placement, duplicate-tap guarded |
| Android screenshot | baseline: `docs/screenshots/c2-after/c2_03_review_slot_instruction.png` · PENDING |
| iOS screenshot | baseline: `docs/screenshots/phase6-after/h3_review.png` · PENDING |
| Functional test | UI: `CheckoutJourneyTest` · Unit: `OrderPlacementTest`, `CheckoutTest` |
| Remaining issues | Consent/policy line `[LEGAL REVIEW]` · slot-expired-at-review state |

| Field | T28 — Payment recovery [P0 when online] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T28 · MISSING (TZ-028) |
| Implementation files | Club only: `ClubCheckoutScreen.kt` failed/cancelled/pending states; `MembershipStatus.PAYMENT_PENDING/PAYMENT_FAILED` |
| Required APIs | PAY server status query `[BACKEND REQUIRED]` |
| Implementation status | **GATED** for grocery (no online payment); PARTIAL pattern for Club |
| Remaining issues | "Check status" against backend before retry — not possible without backend |

| Field | T29 — Order confirmation [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T29 · MISSING (TZ-029) |
| Implementation files | `ui/home/OrderSuccessScreen.kt` (237 L): order ID, slot, `SummaryRow`s, Club progress note, Track/Continue |
| Reusable components | `PillButton`, `SummaryRow` · `ClubProgressCard` |
| Required APIs | ORD [MOCKED] · PAY (COD) · CLUB progress [MOCKED] |
| Implementation status | **EXISTS** — `goHome()` fixes F6; no confetti/autoplay |
| Android screenshot | baseline: `docs/screenshots/c2-after/c2_04_confirmation.png` · PENDING |
| iOS screenshot | baseline: `docs/screenshots/phase6-after/h4_confirm.png` · PENDING |
| Functional test | UI: `CheckoutJourneyTest` · Unit: `GoHomeTest` |
| Remaining issues | Club contribution must read "provisional until delivered" — **verify copy** (see T34 credit-on-placed defect) |

| Field | T30 — Cart price / stock resolution [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T30 · MISSING (TZ-030) |
| Implementation files | `ui/checkout/CheckoutScreen.kt` `IssuesCard` (454–516) rendering `CartIssue.OutOfStock/QuantityReduced/PriceChanged` · `data/local/CartRestore.kt` (restore-time reconciliation + notice) |
| Reusable components | `IssuesCard` · NEW: item-level Resolve/Remove actions, before/after price columns |
| Required APIs | `validateCart` [MOCKED — fixture issues] |
| Implementation status | **PARTIAL** — issues are listed and block placement; explicit per-item accept/remove and refreshed total not a dedicated surface |
| Functional test | Unit: `CheckoutTest`, `PersistenceTest` (reconcile) |
| Remaining issues | Coupon/Club threshold-lost messaging on resolution |

### Membership & rewards (T31–T36)

| Field | T31 — Tazzzo Club landing [P1] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T31 · MISSING (TZ-031) |
| Implementation files | `ui/club/ClubScreen.kt` (277 L): `ClubHero`, `BenefitRow`s, `WhyJoinCard` (worked example from *this* cart), `JoinFooter` "Join Club — ₹99" · `config/MembershipConfig.kt` |
| Reusable components | registry `ClubMembershipHero`, `MembershipCTA`, `ClubBenefitCard` |
| Required APIs | CLUB config [MOCKED] · LEGAL terms **absent** |
| Implementation status | **EXISTS** [MOCKED] — economics are `[BUSINESS DECISION]` (₹99, 5%/₹500, ₹5,000/10%, 3-order fruit) |
| Android screenshot | baseline: `docs/screenshots/club-after/04_club_landing.png` · PENDING |
| iOS screenshot | PENDING |
| Functional test | UI: `JourneyWalkthroughTest` (Club from Account) · Unit: `MembershipTest` |
| Remaining issues | Renewal/cancellation/refund terms `[LEGAL REVIEW]` — blueprint: hide Pay if material terms absent → **current build shows Pay without terms; flag for Batch** |

| Field | T32 — Club ₹99 purchase confirmation [P1] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T32 · MISSING (TZ-032) |
| Implementation files | `ui/club/ClubCheckoutScreen.kt` `OrderSummaryCard`, `PaymentFooter` (TEST PAYMENT badge when `isTestMode`) |
| Required APIs | CLUB quote + PAY create-order [MOCKED] |
| Implementation status | **EXISTS** [MOCKED] |
| Android screenshot | baseline: `docs/screenshots/club-after/05_confirm.png` |
| Functional test | Unit: `MembershipTest` |
| Remaining issues | Tax line `[BUSINESS DECISION]` · renewal link `[LEGAL REVIEW]` |

| Field | T33 — Club activation / welcome [P1] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T33 · MISSING (TZ-033) |
| Implementation files | `ui/club/ClubCheckoutScreen.kt` `WelcomeToClub`, `ReceiptRow` |
| Required APIs | CLUB verified activation `[BACKEND REQUIRED]` — today `LocalMembershipRepository.activate` **grants membership locally** [MOCKED] |
| Implementation status | **EXISTS** [MOCKED] — **contract STEP 7 violation in production terms**: membership is trusted from editable local storage. Acceptable only with `isTestMode` badge; must be blocked in release until server-authoritative |
| Android screenshot | baseline: `docs/screenshots/club-after/07_success.png` |
| Functional test | Unit: `MembershipTest`, `GoHomeTest` (F6) |
| Remaining issues | Server verification; return-to-cart origin handling exists |

| Field | T34 — Club dashboard [P1] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T34 · MISSING (TZ-034) |
| Implementation files | `ui/club/ClubProgressCard.kt` (108 L) embedded in Account + confirmation; no standalone dashboard route |
| Reusable components | `ClubProgressCard` · registry `SpendProgress`, `OrderMilestone`, `RewardTile`, `MembershipBadge` |
| Required APIs | CLUB ledger + ORD settled events `[BACKEND REQUIRED]` — `MembershipRepository.recordEligibleOrder` is called at **placement**, not delivery |
| Implementation status | **PARTIAL** — progress card exists; status/validity, distinct lifetime vs period savings, reversal handling, history absent |
| Remaining issues | Progress credits on placed order → must become pending until qualifying completion (blueprint D4 stack) — **logic change, flag as business/backend** |

| Field | T35 — Reward details [P1] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T35 · MISSING (TZ-035) |
| Implementation files | none (model only) |
| Implementation status | **MISSING** |
| Remaining issues | Depends on T34 ledger semantics |

| Field | T36 — Club savings history [P1] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T36 · MISSING (TZ-036) |
| Implementation files | none (`MembershipState.cumulativeSpendRupees` only; no per-order savings ledger) |
| Implementation status | **MISSING** |
| Remaining issues | Requires financial ledger `[BACKEND REQUIRED]` |

### Orders & fulfilment (T37–T44)

| Field | T37 — Orders list [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T37 · MISSING (TZ-037) |
| Implementation files | `ui/home/OrdersScreen.kt` (329 L): `OrderCard`, `OrderStatusChip`, `OrderProgressRow`, `ReorderPill`, `EmptyOrdersState` |
| Reusable components | shared `OrderStatusChip`/`OrderProgressRow` (internal, reused by detail) |
| Required APIs | ORD `getOrders` [MOCKED — status fixed at PLACED, never advances] |
| Implementation status | **EXISTS** |
| Android screenshot | baseline: `docs/screenshots/final-gate/android/a_orders.png` · PENDING |
| iOS screenshot | baseline: `docs/screenshots/final-gate/ios/f_orders.png` · PENDING |
| Functional test | UI: `JourneyWalkthroughTest` |
| Remaining issues | Active-first grouping by date not implemented · cancelled/failed-payment states have no source |

| Field | T38 — Order details / receipt [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T38 · MISSING (TZ-038) |
| Implementation files | `ui/home/OrderDetailScreen.kt` (208 L): `StatusCard`, `ItemsCard`, `BillCard`, `DetailsCard` (slot, address, instructions), "Need help" → order-scoped Help |
| Required APIs | ORD snapshot [MOCKED] |
| Implementation status | **EXISTS** — immutable snapshot (order stores lines+bill) |
| Android screenshot | baseline: `docs/screenshots/c2-after/c2_05_order_detail.png` |
| Functional test | UI: `CheckoutJourneyTest` (receipt slot) |
| Remaining issues | Invoice download `[BACKEND REQUIRED]` · refund lines · reorder from detail |

| Field | T39 — Order tracking [P0 if status available] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T39 · MISSING (TZ-039) |
| Implementation files | `OrderProgressRow` (4 steps PLACED/PACKED/ON_THE_WAY/DELIVERED) in list + detail |
| Required APIs | ORD fulfilment events `[BACKEND REQUIRED]` — no event feed; no rider API |
| Implementation status | **PARTIAL** — static timeline; no refresh, no delay state, no map (correctly absent) |
| Remaining issues | Registry `OrderTimeline` component; "don't promise 10-minute delivery" honoured (D4) |

| Field | T40 — Order help entry [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T40 · MISSING (TZ-040) |
| Implementation files | `ui/home/HelpScreen.kt` `OrderContextCard` (474–555) + issue routes (`HelpListRow` ×5) · `AppState.helpOrderId` |
| Required APIs | SUP case creation — `SupportRepository` has `getFaqs` only [MOCKED]; every contact route ends in "Opening WhatsApp… (demo)" dialog (no URL-open expect/actual) |
| Implementation status | **PARTIAL** — order-scoped context exists; no case creation, no open-case state |
| Android screenshot | baseline: `docs/screenshots/redesign/r3_help.png` |
| Remaining issues | URL-opening expect/actual (Android Intent / iOS UIApplication) · SUP contract `[BACKEND REQUIRED]` |

| Field | T41 — Cancellation request [P0 if supported] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T41 · MISSING (TZ-041) |
| Implementation files | none |
| Implementation status | **GATED** — no cancel eligibility API; local toggle forbidden by blueprint |
| Remaining issues | `[BACKEND REQUIRED]` `[BUSINESS DECISION]` (cancellation policy/fee) |

| Field | T42 — Refund / issue request [P0 if live] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T42 · MISSING (TZ-042) |
| Implementation files | none (Help issue tree routes to contact only) |
| Implementation status | **GATED** — `[BACKEND REQUIRED]` (SUP cases, photo upload) |
| Remaining issues | Redesign spec rejected "instant refund within 2 minutes" (D6) |

| Field | T43 — Refund status [P0 if launched] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T43 · MISSING (TZ-043) |
| Implementation files | none |
| Implementation status | **GATED** — `[BACKEND REQUIRED]` (PAY/ORD/SUP) |

| Field | T44 — Reorder review [P1] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T44 · MISSING (TZ-044) |
| Implementation files | `ReorderPill` adds available items at current price with a transient message; `CartRestore.reconcile` logic reusable |
| Implementation status | **PARTIAL** — reconciliation happens silently-with-toast; no review surface flagging removed/changed SKUs before adding |
| Functional test | Unit: `OrderPlacementTest` (reorder uses current price) |
| Remaining issues | New sheet/route `Screen.ReorderReview(orderId)` |

### Account & support (T45–T50)

| Field | T45 — Account hub [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T45 · MISSING (TZ-045) |
| Implementation files | `ui/home/AccountTabContent.kt` (407 L): `ProfileHeaderCard`, quick tiles (real counts), `CoinBalanceCard`, `ClubProgressCard`, grouped `TazListRow`s (Notifications toggle, Voice, Language, About), log out |
| Reusable components | `TazGroupedCard`, `TazListRow`, `TazSwitchTrack` |
| Required APIs | ID/CLUB/ORD [MOCKED] |
| Implementation status | **EXISTS** — Privacy/Terms row deliberately not drawn (no content) |
| Android screenshot | baseline: `docs/screenshots/redesign/r2_account.png` · PENDING |
| iOS screenshot | baseline: `docs/screenshots/phase6-after/r5_account.png` · PENDING |
| Functional test | UI: `JourneyWalkthroughTest`, `RedesignEvidenceTest` · Unit: `GoHomeTest` |
| Remaining issues | **Coin balance has two sources that can disagree** (`app.user.coinBalance` vs `CoinRepository.getBalance`) — recorded defect, data-authority decision needed · Privacy/Terms `[LEGAL REVIEW]` |

| Field | T46 — Profile [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T46 · MISSING (TZ-046) |
| Implementation files | `ProfileHeaderCard` shows name/phone with an edit affordance; **no edit screen** (grep `EditProfile` → 1 file, header only) |
| Required APIs | ID update `[BACKEND REQUIRED]`; `UserProfile` has name/phone only (no email, no consent fields) |
| Implementation status | **MISSING** (route + form) |
| Remaining issues | Model extension (email, consents) · validation states |

| Field | T47 — Address book [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T47 · MISSING (TZ-047) |
| Implementation files | `ui/home/AddressesScreen.kt` (126 L): list with `NotServiceableChip`; **no set-default/edit/delete/add from here** (`AddressRepository` has get/add only) |
| Implementation status | **PARTIAL** |
| Android screenshot | baseline: `docs/screenshots/final-gate/android/a_addresses.png` |
| Remaining issues | Repository needs `setDefault/update/delete` · delete-last/current guards · shares T06 form |

| Field | T48 — Coins / wallet [P1; gated] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T48 · MISSING (TZ-048) |
| Implementation files | `ui/home/CoinsScreen.kt` (217 L): `CoinsHeroCard`, how-it-works (`CoinStepRow` from `CoinRules`), `LedgerRow`s |
| Required APIs | CLUB/coins ledger [MOCKED] · D5 economics unapproved |
| Implementation status | **EXISTS** [MOCKED] — no rupee wallet (correct: PPI regulation); redemption not offered at checkout |
| Android screenshot | baseline: `docs/screenshots/qa-gate/q2_coins.png` |
| iOS screenshot | baseline: `docs/screenshots/final-gate/ios/f_coins.png` |
| Remaining issues | D5 `[BUSINESS DECISION]` · expiry/reversal states · balance dual-source defect (T45) |

| Field | T49 — Help center [P0] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING |
| Specification file | blueprint §B/T49 · MISSING (TZ-049) |
| Implementation files | `ui/home/HelpScreen.kt` (561 L): `HelpSearchField`, `OrderContextCard`, issue routes, `FaqCard`/`FaqContent`, "Still stuck?" |
| Required APIs | SUP `getFaqs` [MOCKED — 6 FAQs] · contact channel/hours **not verified** |
| Implementation status | **EXISTS** — FAQ search, order shortcut; contact ends in demo dialog |
| Android screenshot | baseline: `docs/screenshots/redesign/r3_help.png` · PENDING |
| iOS screenshot | baseline: `docs/screenshots/final-gate/ios/f_help.png` · PENDING |
| Functional test | UI: `JourneyWalkthroughTest` |
| Remaining issues | WhatsApp deep-link expect/actual · approved FAQ copy `[LEGAL REVIEW]` · contact hours `[BUSINESS DECISION]` |

| Field | T50 — Support conversation + Legal/About [P0; two surfaces: T50a, T50b] |
|---|---|
| Design version | blueprint-1.0 |
| Approved visual reference | MISSING (need **two** frames) |
| Specification file | blueprint §B/T50 · MISSING (TZ-050a/b) |
| Implementation files | **T50a Support conversation:** none (no chat vendor, no case model). **T50b Legal/About:** `ui/home/AboutScreen.kt` (139 L) brand story + `AboutValueRow`s; **no Terms/Privacy/Refund/Club T&C content or routes**; version string not shown |
| Required APIs | SUP conversation `[BACKEND REQUIRED]` · LEGAL approved documents **absent — launch blocker** (BLOCKERS P2→P0) |
| Implementation status | T50a **MISSING/GATED** · T50b **PARTIAL** (About exists; legal pages missing) |
| Android screenshot | baseline: `docs/screenshots/qa-gate/q4_about.png` |
| Remaining issues | Legal content `[LEGAL REVIEW]` · app version/build display (derivable, honest) · D6 claims on About |

---

## 3. STEP 4 — Design system audit (what exists vs the blueprint registry)

**Existing single sources of truth** (do not duplicate; extend here only):

| Concern | File | State |
|---|---|---|
| Colours | `theme/Theme.kt` `TazColors` | Contrast-audited ramp (measured table in `docs/DESIGN_SPEC.md §2`). Brand identity is **not reopened** per design owner (2026-09-01) and founder (2026-09-06). Any new value needs the approved tokens. |
| Typography | `theme/Tokens.kt` `TazType` + Poppins in `TazzzoTheme` | 7-step scale + commerce roles. Note `TazType.microSize = 10.sp` while DESIGN_SPEC says Micro floor is 11sp — **inconsistency to resolve**. |
| Spacing / radius / elevation / motion / sizes | `theme/Tokens.kt` `TazSpace`, `TazRadius`, `TazElevation`, `TazMotion`, `TazSize` | Complete. |
| Icons | `theme/TazIcons.kt` | One Material family; emoji = content only. |
| Ambient-motion gate | `theme/MotionSettings.kt` | Exists; OS reduce-motion wiring open (E6). |
| Press/haptics/nav motion | `ui/interaction/Press.kt`, `Haptics.kt`, `NavMotion.kt` | E1/E2 complete — **preserve**. |
| States | `ui/common/States.kt` | Skeleton/Empty/Error/Offline(unwired). |
| Product image | `ui/common/ProductImage.kt` | Fixed-aspect, Fit, three states, loader seam. |

**Registry components (Mindset §5) not yet present as named reusable units** — these are the STEP 4 build list once tokens are approved:
`DeliveryChip` (slot-aware) · `SlotCard`/`SlotDayGroup` · `AddressCard` (list/select/manage variants) · `PaymentMethodRow` · `FinalPayBar` · `PaymentStatus` (shared grocery/Club) · `OrderTimeline` · `VariantPill` · `ProductGallery` · `FactSection`/`NutritionTable` · `OfferEligibility`/`PromotionRow` · `FeeExplainer` · `RewardTile`/`RewardTerms` · `SavingsHistory` · `MembershipBadge` · `SupportIssueSelector` · `RefundStatus` · shared `PhoneOtpForm`.

**Duplication to eliminate (contract STEP 4: "no different versions of the same component"):** two bill renderers (Cart `BillRow` vs Checkout `ReviewCard`), two OTP forms (`OnboardingScreen` vs `LoginScreen`), two `Hairline`s (`CheckoutScreen.kt:517`, `ProductDetailScreen.kt:340`), two `SectionLabel`s (`ClubScreen.kt:152`, `AccountTabContent.kt:240`), two `StatusCard`s (`ClubCheckoutScreen.kt:205`, `OrderDetailScreen.kt:114`).

---

## 4. Cross-cutting facts every batch must respect

1. **Every repository is a mock** (`ServiceLocator`). No screen may be labelled `[IMPLEMENTED]` for money, stock, slots, membership or payment until a `Remote*` implementation exists.
2. **Membership is granted from local storage** (`LocalMembershipRepository`). The `isTestMode` badge is the only guard. Release builds must not expose the purchase CTA until server verification exists.
3. **Club progress credits at order placement**, not delivery — contradicts blueprint T29/T34. Needs a lifecycle event source.
4. **Coin balance dual source** (T45/T48). Data-authority decision, not a UI task.
5. **Analytics logs raw search query** (`AppState.recordSearch`) — blueprint T08 forbids. Fix when T08 is batched.
6. **Preserve**: E1 press states/haptics, E2 directional nav + `SaveableStateHolder`, F4/F6/F7/F8 fixes, `goHome()`, cart-row stepper width, duplicate-label rule. Regression suites: 169 unit + 21 instrumented.
7. **Business decisions outstanding**: D4 delivery promise, D5 coins, D6 marketing claims, tip, store hours, Club economics/terms, promo stacking, cancellation/refund policy, guest browsing, Fresh/Dairy/Pet taxonomy exclusion.
8. **Legal content absent**: Terms, Privacy, Refund policy, Club T&C — launch blocker independent of design.

---

## 5. Batch log

| Batch | Screens | Artifacts received | Status | Report |
|---|---|---|---|---|
| 0 | — (inventory) | Mindset skill · Blueprint | **This document created; baseline 169/169 unit tests green** | — |
| 1 | awaiting ChatGPT's first five | none yet | **BLOCKED on approved visual references + TZ specs** | — |

---

## 6. Change history

- 2026-09-21 — Created. Repo inspected (22,199 Kotlin LOC, 19 routes in `Screen`, 5 tabs, 26 UI files). Handoff inventory: 2 of 5 artifact classes received. 50/50 IDs accounted for. Baseline unit suite run and parsed.
