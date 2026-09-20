# TAZZZO — Commerce Experience Audit

**Opened:** 2026-09-05 · **Scope:** the commerce benchmark wave (see `TAZZZO_COMMERCE_BENCHMARK.md`)
**Companion:** `TAZZZO_5_STAR_EXPERIENCE_AUDIT.md` (E1/E2 interaction work, still authoritative for those)

Every claim carries one of: `[IMPLEMENTED]` `[MOCKED]` `[BACKEND REQUIRED]`
`[ASSET REQUIRED]` `[DEVICE REQUIRED]` `[BUSINESS DECISION]`. Nothing below is
called done because it compiles or looks right in a screenshot.

---

## 1. Wave C1 — promotion engine and the cart as a control centre

### INSPECTED
Money layer (`BillCalculator`, `BillSummary`), cart bill card, checkout review
step, membership integration, `ServiceLocator`, every `resetTo(Screen.Home)`
call site, and the on-device behaviour of the resulting cart.

### FOUND
| # | Finding | Class |
|---|---|---|
| — | **No promotions layer existed.** The Club discount was correct but stood alone; nothing could decide which of two savings applied, whether they combined, or what to tell the customer. | Structural gap |
| **F6** | **"Start shopping" landed on the last-open tab, not the shopping feed.** `resetTo(Screen.Home)` reset the back stack but the shell's selected tab is separate state. A customer who joined Club from the Account tab was dropped back on Account. Same bug in **7 files** (Orders, empty cart, order success, Login, Onboarding, Club checkout, order placement). | **App defect**, found by the on-device journey test |
| **F7** | **The cart's Club card contradicted the bill.** It read from an isolated Club evaluation ("Club savings applied ₹29") while the post-stacking bill had applied ₹0 Club because a coupon won. Two numbers on one screen disagreed. | **App defect**, found in a screenshot *after* semantic assertions passed |
| — | Three unit failures during integration were **test-fixture errors**: a Club-only test met a competing dairy offer (correct engine behaviour), and a Club-vs-coupon test built a ₹480 cart, below the ₹500 Club minimum, so nothing competed. | Test failure |
| — | Scripted `adb` driving of the app failed silently: a cold Home load took **six minutes** on the 2-core software-GL emulator and every fixed-sleep tap was dropped. Zero ANRs. | Environment |

### CHANGED `[IMPLEMENTED]`
- **`Promotion` model** — percent / flat / buy-X-get-Y / free-delivery; cart,
  product or category scope; audience; minimum measured on the promotion's
  **own** scope; cap; coupon code; `stackable` + `priority`; usage limit;
  validity labels (display only — the client never gates money on its clock);
  exclusions; campaign id.
- **`PromotionEngine`** — pure, deterministic, `BillCalculator` is its only
  caller. Stackables all apply; exclusives resolve to the single best; ties
  break by priority then id so input order cannot change money; Club joins the
  exclusive contest under policy and **the loser is named**: *"Best offer
  applied — TAZZZO50 saves you ₹50; your Club discount would have saved ₹29."*
  Un-entered coupons and members-only offers to guests are silent, not nagged;
  a typed coupon below its minimum says exactly how much more.
- **Money guards** — a promotion never discounts more than its own base; all
  discounts together never exceed the item total; fees are never discounted
  below zero; coins redeem against the **post**-discount amount.
- **`BillSummary`** — `promotionDiscount`, `appliedPromotions`,
  `declinedPromotions`, `bestOfferNote`, `freeDeliveryByPromotion`, all
  appended and defaulted so every existing fixture keeps compiling; and
  `realisedSavings` = promotions + Club, kept **separate** from MRP `saved`.
- **Cart** — each applied offer is its own bill line with its explanation;
  declined offers listed with reasons; coupon entry with immediate honest
  feedback; "You saved ₹X" from realised savings only; the Club card now reads
  the bill and says *set aside* when an offer won (F7).
- **Review step** — itemises the same discounts and the note before "Place
  order". Nothing the cart shows is hidden at the final step.
- **`AppState.goHome()`** — resets tab **and** stack; all seven "go shopping"
  sites use it (F6).
- **Test environment (§41)** — Gradle heap 4 GB → 2 GB, Kotlin daemon capped,
  parallel off, AVD 4 → 2 cores, daemon stopped before instrumenting;
  `docs/TESTING.md` records smoke/regression split and the four failure
  classes (app / test / environment / **test-state leak**).

### WHY
A total that changes silently is a total the customer does not trust. Every
rupee taken off is a line with a sentence; every offer that did not apply says
why; and when two offers compete the winner **and** the loser are named. That
sentence is the product — the engine exists to produce it correctly.

### VERIFIED `[RUN]` — Android emulator, 2 cores, software GL
- **`CartPromotionJourneyTest` — OK.** Join → pay → verify → Welcome → basket
  (3 bananas, 10 onions, 4 potatoes = ₹582) → cart shows Buy-2-Get-1 −₹42,
  Club −₹29, **You saved ₹71**, total **₹516** → coupon TAZZZO50 → best-offer
  note verbatim → Club set aside, card agrees → coupon −₹50, **You saved ₹92**,
  total **₹495**. Every figure asserted on the semantics tree; three evidence
  PNGs captured from inside the process: `docs/screenshots/c1-after/`.
- Smoke suite (semantics + cart row): **OK (4 tests), 18.6 s** — was 340 s
  and never green before §41.
- **Full regression — two runs, and the difference is the evidence:**
  - Run 1 (before per-class state reset): **9/11, 275 s.** The two failures were
    exactly the tests that assume an empty cart, running after
    `CartPromotionJourneyTest` had left a member with 17 items in the persisted
    store. A test-state leak, not an app or environment failure.
  - Run 2 (every class calls `TestState.reset()` in `init`): **OK (11 tests),
    153 s**, one pass, no timeout widened. The reset targets
    `com.tazzzo.app_preferences.xml`, verified on device to be the file the app
    actually persists into.

### VERIFIED `[DEVICE]`
**Not performed.** No physical hardware. No performance number is quoted from
this emulator, and none will be.

### TEST RESULTS
Unit **92 → 116**, 0 failures (22 promotion, 2 goHome). Instrumented
**10 → 11**. Smoke 4/4.

### BEFORE / AFTER
| | Before | After |
|---|---|---|
| Promotion layer | 0 | **5.0** `[IMPLEMENTED]` / set `[MOCKED]` |
| Stacking + explanation | 0 | **5.0** |
| Cart bill transparency | 4.0 | **5.0** |
| "Start shopping" destination (F6) | 2.0 | **5.0** |
| Club card ↔ bill consistency (F7) | 2.0 | **5.0** |
| Review-step completeness | 4.0 | **4.5** (slot still missing → C2) |
| Test environment reliability | 1.0 | **4.5** (`[DEVICE REQUIRED]` for the rest) |

### REMAINING (not built, not claimed)
Delivery slots with fees and end-to-end echo (C2) · Club progress card on
confirmation/Account, order detail, extended status timeline (C3) · payment
step UPI-first (C4) · PDP optional facts and richer product card (C5) ·
festival campaign system (C6) · Buy Again with current price/availability (C7)
· order-scoped support and idempotent reversal (C8) · design-system component
extraction · MRP-strip vs realised-savings **copy clarity** ("You save ₹151"
next to "You saved ₹71" is correct but reads alike — reword the strip).

### BLOCKERS — carried, none removed
`[BACKEND REQUIRED]` live promotion set, per-user usage counters, validity ·
Razorpay order + verification · authoritative Club state · serviceability ·
slots · catalogue · stock · tickets/refunds
`[ASSET REQUIRED]` login imagery · campaign art · category art · product photos ·
hero image without third-party trade dress
`[DEVICE REQUIRED]` real Android · real iPhone · every performance claim
`[BUSINESS DECISION]` promotion stacking policy · membership economics/caps ·
reward rules · refund/cancellation rules · D4 / D5 / D6

### NEXT WAVE
**C2 — delivery slots**: `DeliverySlot.feeRupees` + reason, `Order.slot`, the
chosen slot echoed cart → review → confirmation → orders, fee "Why?" from real
rule output only. Replaces the generic "Fast delivery" card at the top of the
cart with the customer's actual choice — the last place a mature commerce
principle from the benchmark is still unmet on the purchase path.

---

## 2. Wave C2 — delivery as a choice, the cart as a control centre, the order as a receipt

### INSPECTED
The reference screenshots (first-hand, see benchmark §8), `DeliverySlot`/`Order`
models, `BillCalculator`, `SlotStep`/`ReviewStep`, confirmation, orders list,
Account, Home rails, product card, and every one of these on device.

### FOUND
| # | Finding | Class |
|---|---|---|
| — | Delivery was a label ("Fast delivery"), not a choice: `DeliverySlot` had **no fee**, orders did not store the slot, review/confirmation/orders never showed it. | Structural gap |
| — | Two "save" figures sat on the cart with near-identical wording (MRP strip vs realised line). Correct, but indistinguishable. | Copy clarity |
| — | Waived delivery was a silent gift: "FREE" with no amount and no reason, and it counted for nothing in savings. | Transparency gap |
| **F8** | Club and Offers cards rendered **narrower** than the delivery and bill cards — a double gutter (own padding inside an already-padded column). Found in a screenshot; semantics could not see it. | **App defect (layout)** |
| — | Three regression breaks were **test assumptions** broken by legitimate UI change: a renamed label asserted verbatim; two tests waiting for a lazy-rail control without scrolling after Home grew a coupon rail. | Test |
| — | One break was **process memory**: `MockOrderRepository` is process-global, so an earlier order put Fresh Onion in Home's Order-again rail and a singleton matcher found two steppers. `TestState.reset()` clears the store, not the heap. | Test (new class, recorded) |
| — | Post-reinstall first launch exceeded 60 s on the 2-core software-GL emulator → whichever test ran first failed. **Zero ANRs.** Fixed by a one-time warm-up before the suite, not by widening timeouts. | Environment |

### CHANGED `[IMPLEMENTED]`
- **`DeliverySlot`** gains `feeRupees`, `feeReason`, `group`, `recommended`; **`Order`/`OrderRequest`** carry `slot` + `instructionIds`; `CheckoutSession.instructionIds` (transient, never persisted).
- **`BillCalculator`** takes the chosen slot: a slot's own fee overrides the flat rule; threshold and promotions still win; **waived delivery is its own realised-savings line with the reason** ("Free on orders above ₹199" / "Free for this slot" / "Free delivery offer applied"). 6 money tests.
- **Slot step**: grouped (Next available / Today / Tomorrow), fee on every row, reason on paid slots, `Recommended` tag (never auto-selected), `Full` on sold-out.
- **Review**: slot with fee echoed; **delivery-instruction chips** from `AppConfig.deliveryInstructions` (market-configurable), `Role.Checkbox` + selected semantics.
- **Payment order**: UPI → Card → COD (India-first). UPI/Card stay honestly `Coming soon`.
- **Confirmation**: slot, instructions, "You saved", `ClubProgressCard` for members, **Track order → OrderDetail**.
- **`OrderDetailScreen`** (new): status, slot, items with struck MRP, bill with struck waived delivery, Club line, total, details, **order-scoped "Need help"** (`helpOrderId` carried into Help), Order again via `addToCart` (current price and stock win).
- **Orders list**: "Saved ₹X" and slot on each card; card opens the receipt.
- **Cart**: realised-savings header with expandable breakdown by type; delivery-choice card previewing the next slot and fee from the same repository checkout uses; MRP strip relabelled **"₹X below MRP on these items"**, bill line **"Extra savings today"**; delivery fee struck through when waived. **F8 fixed.**
- **Product card**: brand line (when present); **`₹X OFF` badge in Tazzzo orange** replaces the percent badge — rupees are what a customer can verify.
- **Account**: `ClubProgressCard` for members (progress bar, next milestone, next reward, total saved).
- **Home**: coupon rail from `PromotionConfig` **stating thresholds and codes**; tapping pre-fills the cart's coupon.
- **`TestState.reset()`** in every instrumented class; warm-up rule; two new failure classes in `docs/TESTING.md`.

### WHY
The reference apps are not richer because they have more banners; they are
richer because every screen answers the next question before it is asked —
what will delivery cost, why is it free, what did I save and of what kind, when
is it coming, and where do I go if it goes wrong. Each of those is now a line
with a number and a reason, and the same number appears at every stop.

### VERIFIED `[RUN]` — Android emulator, 2 cores, software GL
- **`CheckoutJourneyTest` — OK (13.5 s).** Cart → checkout → address → picks the
  **paid** "Today, 8–10 PM · ₹15" (Recommended tag, Free labels and Full state
  asserted) → COD ("Coming soon" asserted on UPI/Card) → review shows
  "Today, 8–10 PM · ₹15" and the "Leave at my door" chip → Place order ₹84 →
  confirmation repeats slot, fee, instruction → **Track order** → receipt repeats
  them with Total paid ₹84 and order-scoped help. To pay moved **₹94 → ₹84** the
  moment the ₹15 slot replaced the ₹25 flat fee — the total followed the choice.
- **Full regression: OK (12 tests), 115 s, one pass,** after warm-up. The two
  runs before it (9/12, 11/12) were classified line by line: 4 test-assumption
  breaks from legitimate UI change, 1 process-global-mock singleton, 1 cold
  start — **0 app defects among them**; F8 was found by eye.
- Evidence: `docs/screenshots/c2-after/` (5 PNGs captured in-process).

### VERIFIED `[DEVICE]`
Not performed. No hardware.

### TEST RESULTS
Unit **116 → 122**, 0 failures. Instrumented **11 → 12**, 0 failures.

### BEFORE / AFTER
| | Before | After |
|---|---|---|
| Delivery as a choice (fee, groups, reason) | 3.0 | **5.0** (slots `[MOCKED]`) |
| Slot echoed cart → review → confirmation → orders → detail | 0 | **5.0** |
| Delivery-fee transparency (struck, reason, counted) | 3.5 | **5.0** |
| Order detail / receipt | 0 | **5.0** (invoice `[BACKEND REQUIRED]`) |
| Orders list usefulness | 4.5 | **5.0** |
| Confirmation as retention moment | 3.5 | **4.5** (Club card for members; next-reward copy present) |
| Cart savings clarity (two figures) | 3.0 | **5.0** |
| Product card hierarchy | 3.5 | **4.5** (`[ASSET REQUIRED]` imagery caps it) |
| Cart layout consistency (F8) | 3.0 | **5.0** |
| Home coupon discovery | 0 | **4.5** (config-backed) |

### NOT BUILT — stated plainly
**Tip / gratuity** `[BUSINESS DECISION]` — revenue-recognition and reversal
rules unset; not copying it from the reference. **"Rate order"** — no ratings
backend; a rating UI with nowhere to go is a fake. **Invoice download**
`[BACKEND REQUIRED]`. **Festival hero, login photography, product/category
imagery** `[ASSET REQUIRED — PRODUCTION PHOTOGRAPHY]`; architecture ready,
emoji remain. **PDP optional facts, Buy Again with change indication, basket
completion, price drops, recently viewed, extended status timeline, refund
reversal, payment-method sheet** — C3+.

### BLOCKERS — carried, none removed
`[BACKEND REQUIRED]` serviceability + real slots/fees · orders/invoice ·
tickets · promotions set · Razorpay · authoritative Club.
`[ASSET REQUIRED]` everything photographic. `[DEVICE REQUIRED]` all performance.
`[BUSINESS DECISION]` tip · instruction set · stacking · D4 / D5 / D6.

### NEXT WAVE
**C3 — post-purchase loop and product confidence**: extended order status
timeline (Confirmed → Preparing → Packed → Out for delivery → Delivered, model +
UI together), Buy Again with current-price/availability change indication, PDP
optional facts (render only when data exists), `ReorderCard`, and the
milestone-unlock moment with E3 motion.

---

## 3. Wave C3 — Home as a commerce destination, the card as a price-first unit

### INSPECTED
The founder's reference screenshots (Zepto, Blinkit, noon Minutes — first-hand,
benchmark §8), `HomeTabContent` (banner carousel, rails, category grid),
`ProductCard`, `Components.kt`, `CategoryArtTile`, and each on device through
in-process snapshots after the two scripted `adb` capture runs landed on the
wrong screens.

### FOUND
| # | Finding | Class |
|---|---|---|
| — | Home opened on a **generic banner carousel** with placeholder copy. Nothing on the first screen said *what is on today*. | Structural gap |
| — | `ProductCard` was ~156 dp with a 4:3 image and a percent badge: the three-per-row density of every reference was impossible, and the **price was not the first thing read**. | Density / hierarchy |
| — | No deals module. Real MRP–price gaps existed in the catalogue but nothing surfaced the largest ones. | Merchandising gap |
| **F9** | After the festival hero landed, **"Vegetables & Fruits" was announced by two controls** (hero tile and grid tile). Found by the regression — `Expected exactly '1' node but found '2'` — not by reading. A screen-reader user has the same problem the test had. | **App defect (accessibility)** |
| **F10** | A `taz_start_home` launch could land on the **login wall**: the flag routed to Home but never marked the device onboarded, so routing could bounce it. Two evidence captures showed Account and Login instead of Home. | Dev tooling (ships nowhere) |
| — | The single largest visual gap to the references is **real product photography**. The card is now laid out for it; the assets do not exist and will not be fabricated. | `[ASSET REQUIRED — PRODUCTION PHOTOGRAPHY]` |

### CHANGED `[IMPLEMENTED]`
- **`Campaign`** model + **`CampaignConfig.current`** (Janmashtami; `categoryIds` dairy / sweet / oil / fruits; `validFrom/UntilLabel`; `memberOnlyOffer`; `heroImageUrl = null` `[ASSET REQUIRED]`). Copy is Tazzzo's own; no reference wording or artwork.
- **`CampaignHero`** replaces `BannerCarousel`: title, subtitle, validity, four category tiles (own `CategoryArtTile` art) routed to `CategoryDetail`, CTA. **F9 fixed**: tile label is `"<campaign>: <category>"`, image decorative (`contentDescription = null`).
- **`ProductCard` compact anatomy**: 120 dp, square image, stepper overlaid bottom-end (full width once in cart), body = **price → struck MRP + `₹X OFF`** (orange, only when MRP > price) → name (2 lines) → unit; low-stock chip top-start. Rail spacing `TazSpace.sm`. Nothing invented: no ratings, reviews, stock counts or delivery promises the data does not carry.
- **"Today's deals" rail**: top-10 by real `mrp − price` across every rail — derived, never authored.
- **Section rhythm** `SectionGap = 20 dp`; rails hoisted into one `rails` map.
- **F10 fixed**: `AppState.enterDemoHome()` marks onboarded *and* routes Home in one step; `DemoTourRunner` uses it. Dev flag only.
- **Tests**: `NavigationJourneyTest` now matches actionable nodes (`and hasClickAction()`) — merchandising may legitimately duplicate an item; **`HomeEvidenceTest`** (new) locks *exactly one* actionable grid tile and one hero tile, asserts the deals rail shows a derived `₹X OFF`, opens a category, and checks Back preserves Home. Failure class recorded in `docs/TESTING.md`.

### WHY
Every reference answers three questions on the first screen — *what's on today,
what's cheap, what do I usually buy* — and on a small card the price is the
first thing the eye reads. None of that needs their branding: Tazzzo green
identity, orange reserved for savings, Poppins, our own hero copy and category
art. F9 is the cost of merchandising done honestly: the moment one category
appears in two modules, the two controls must *sound* different, not just look
different.

<!-- C3-RESULTS -->

### NOT BUILT — stated plainly
Product photography and hero artwork `[ASSET REQUIRED]`; login redesign
`[ASSET REQUIRED]`; extended order-status timeline; Buy Again with change
indication; PDP optional facts; basket completion; price-drop notices; refund
reversal; payment-method sheet; design-system component extraction. Tip remains
`[BUSINESS DECISION]`. D4/D5/D6 untouched.

### BLOCKERS — carried, none removed
Photography, hero artwork, D4/D5/D6, iOS runtime (`xcode-select`), backend
endpoints for campaigns/promotions/slots — all in `BLOCKERS.md`, dated.

### NEXT WAVE
On receipt of photography, wire `Product.imageUrl` into the existing card slot
(no layout change needed). Then the order-status timeline and Buy Again.

---

## 4. Wave N0–N2 — taxonomy truth, a savings destination, and the festival question answered properly

### INSPECTED
Blinkit's live store with a Bengaluru address and noon.com's UAE mobile web,
both first-hand on 2026-09-06 (findings in `TAZZZO_FULL_COMMERCE_ROADMAP.md`
§1). Then the frozen backend taxonomy, verified directly against
`Tazzzo_Taxonomy_V1_Master.csv` (293 rows), the master JSON and the catalogue
service's own `openapi.json`, rather than against the handoff document that
described them.

### FOUND
| # | Finding | Class |
|---|---|---|
| — | **Festivals are a supply problem, not a hero image.** Blinkit runs no festival banner today; it carries a permanent Pooja aisle, Flowers & Leaves inside produce, and gift packs as a sub-category of seven food categories. Our festival hero pointed at dairy and fruit, so tapping it reached ordinary milk. | Merchandising gap |
| — | Blinkit shows a **local-language name on every produce item** (Onion (Eerulli), Southekayi) and sells 50–250 g packs. Tazzzo had neither. | Localisation gap |
| — | noon gives **Deals a permanent nav slot** and shows **price per piece / per 100 g** on every card. Savings in Tazzzo were reachable only as a sort option. | Structural gap |
| **F11** | I began building a "Pooja & Festive" **category** before checking the frozen taxonomy. That is the wrong plane: the taxonomy's own three-plane rule puts festive in collections. Caught by reading the CSV, not by review. | **My design error, corrected** |
| **F12** | The handoff document states there is no festive category in a way that reads as though ritual goods are absent. **`Pooja & Religious Needs` does exist** — 2 sub-categories, 9 verticals, TZV-000225..233 — and §3 of the same document lists it. | Document defect |
| **F13** | **Every vertical id in the shipped master CSV is suffixed " (provisional)"** — all 293. Branch status is leaking into the identity column, contradicting the document's own stability contract. Anything keying on that string breaks when a branch locks. | Backend data defect |
| **F14** | **There is no browse, list or search endpoint.** `GET /api/v1/products` requires `canonicalKey` and returns one product. Category and search screens cannot be served at all. | Blocker, verified |
| **F15** | Fresh produce, dairy and pet care are **excluded from v0.9.0 by recorded decision**, yet they are the app's entire spine. Zero Dairy, Milk, Curd, Paneer, Dog or Cat verticals exist. Eggs do. | `[BUSINESS DECISION]` |
| — | A green regression turned red on `Failed waiting for PixelCopy!` — evidence capture failing on a loaded emulator, with every assertion already passed. | Environment |

### CHANGED `[IMPLEMENTED]`
- **Pooja & Religious Needs aisle**, mirroring the real taxonomy verbatim: the
  TZC name, its two sub-categories, and one SKU per vertical TZV-000225..233
  (agarbatti, dhoop, camphor, wicks, diya, deepam oil, havan samagri, roli
  chandan set, brass thali). Deliberately **not** called "Festive".
- **`Product.verticalId`** carries the bare backend id, so mock-to-service is a
  data change. Null marks exactly which SKUs the backend cannot serve today.
- **`Product.unitPriceLabel`** — per-kg, per-litre and per-piece prices derived
  from the pack size, with multipacks ("4 x 100 g") counted whole and
  unmeasurable packs declining rather than guessing. Rendered on every card.
- **`Product.localName`** rendered under the product name where a verified name
  exists; never transliterated at render time.
- **Deals facet** — MRP genuinely above price, composable with the other
  filters, surviving navigation and restoring from older state bundles.
- **Deals tab** — fifth destination: dated campaign band, offers stating the
  minimum spend that gates them, members-only offers hidden from non-members,
  and a savings grid ranked by rupees off. `getDeals()` added to the catalogue
  contract.
- **`cat_pooja` tile** sourced from Wikimedia Commons under CC BY-SA 4.0 and
  attributed. A first automated pick returned a 19th-century museum artefact;
  candidates are now reviewed visually before anything ships.
- **Evidence capture is best-effort in every test class.** Screenshots are
  documentation; assertions are the verdict. Failure class documented.
- **Produce depth stopped, not quietly shipped**, pending the ruling on F15.

### WHY
The reference apps answer "what is on today, what is cheap, what do I usually
buy" on the first screen, and they can serve a festival because the flowers and
the lamp are in stock on an ordinary Tuesday. Building that as a *category*
would have contradicted a frozen backend model that already has the right home
for it. Checking cost twenty minutes; not checking would have cost a taxonomy
release.

### TEST RESULTS
Unit **136/136**. Instrumented **12/13** before the PixelCopy fix, whose single
failure was the capture itself; re-run with the Deals journey added is reported
in the log below.

### NOT BUILT — stated plainly
Variants, wishlist, PDP carousel, order timeline, returns, account depth — all
scoped in the roadmap. Ratings, reviews, "sold recently", price history and
category rank stay `[BACKEND REQUIRED]` capabilities and are **not** faked.
Price-band facet dropped with a reason. Per-item ETA gated on **D4**.

### BLOCKERS — carried, none removed
F13, F14, F15, the unbuilt collections plane, all-provisional branch status,
production photography, native review of local names, and D4/D5/D6. All dated
in `BLOCKERS.md`.

---

## Log
- **2026-09-06** — N0–N2: taxonomy verified against source and the app realigned
  to it; Pooja aisle mapped to TZV-000225..233; per-unit price and local names;
  Deals facet and Deals destination. Found and recorded three backend defects
  and one of my own (F11–F15). Unit 136/136.
- **2026-09-06** — C2 complete on Android: delivery as a priced choice echoed to
  the receipt; cart savings header; OrderDetail; F8 (double gutter) fixed; suite
  12/12 after warm-up; two new test-failure classes recorded.
- **2026-09-05** — C1 complete on Android. F6 and F7 found by running, not
  reading. Regression: 9/11 with a state leak → **11/11 in 153 s** after per-class
  store reset. Unit 116/0.
