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

## Log
- **2026-09-05** — C1 complete on Android. F6 and F7 found by running, not
  reading. Regression: 9/11 with a state leak → **11/11 in 153 s** after per-class
  store reset. Unit 116/0.
