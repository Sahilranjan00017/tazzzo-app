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

## Log
- **2026-09-06** — C2 complete on Android: delivery as a priced choice echoed to
  the receipt; cart savings header; OrderDetail; F8 (double gutter) fixed; suite
  12/12 after warm-up; two new test-failure classes recorded.
- **2026-09-05** — C1 complete on Android. F6 and F7 found by running, not
  reading. Regression: 9/11 with a state leak → **11/11 in 153 s** after per-class
  store reset. Unit 116/0.
