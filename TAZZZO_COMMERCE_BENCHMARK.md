# TAZZZO vs BENCHMARK — Commerce UX Matrix

**Opened:** 2026-09-05 · **Status:** BENCHMARK COMPLETE · implementation waves follow
**Benchmarks:** noon, noon Minutes, Zepto, Flipkart Minutes, Amazon grocery

---

## 0. How to read the competitor columns — sourcing, stated plainly

I did **not** browse or drive any competitor app while writing this. Claiming
otherwise would be the same category of dishonesty as calling a screen 5/5
because it compiles. Every "benchmark observation" below carries one of three
tags:

| Tag | Means |
|---|---|
| `[CTO-RESEARCH]` | A noon pattern the CTO reported after reviewing noon's current commerce/help documentation. Taken as given, attributed. |
| `[PRINCIPLE]` | Well-established commerce practice across mature platforms. Principle-level; I am not asserting how any one named competitor implements it today. |
| `[TAZZZO]` | Verified in Tazzzo's source, with file references. |

Where a row's competitor column says only `[PRINCIPLE]`, that means: **this is
how mature commerce behaves; I have not verified Zepto / Flipkart Minutes /
Amazon specifically.** I would rather leave a cell honest than fill it
impressively.

The point of the exercise is not "what does noon do". It is "what does a
customer need to trust a commerce app with their money", and where Tazzzo falls
short of that.

---

## 1. The single most important finding

**Tazzzo has no promotions layer.** `grep -rniE "coupon|promo|voucher"` across
the app returns a banner *display* object and one enum value. There is:

- no promotion model,
- no eligibility or stacking rules,
- no coupon entry,
- no product- or category-level offer,
- no place in `BillSummary` for a promotion discount,
- and therefore no way to *explain* a discount to a customer.

The Club discount exists and is correct (`BillSummary.clubDiscount`, computed
once in `BillCalculator`). But it stands alone. The moment a second kind of
saving is introduced — a coupon, a festival offer, a Buy-X-Get-Y — there is
nothing to decide which applies, whether they combine, or what to tell the
customer. That decision layer is the difference between "a cart that shows a
total" and "a cart that explains itself", and it is where the CTO is right that
Tazzzo can beat a prettier clone.

Everything else in this matrix is real, but that gap is structural.

---

## 2. The matrix

Scores: **Tazzzo current** on the audit's 1–5 scale. Not inflated.

### 2.1 Discovery & Home

| | Benchmark | Tazzzo current `[TAZZZO]` | Gap | Tazzzo solution |
|---|---|---|---|---|
| **Home hierarchy** | Search primary; categories scannable; then contextual modules (seasonal, buy-again, deals, bestsellers). Curated, not a feed. `[PRINCIPLE]` | Search dominant ✓; category grid ✓; banners → bestsellers → order-again → rails → voice last. Auto-advancing carousel. **3.5** | No seasonal/campaign module. No "complete the basket". Hero banner carries an unsubstantiated claim (D6) and third-party trade dress. Order-again shows fixture data. | Add a **config-driven campaign slot** (§2.4) and a compact "Complete your basket" rail. Hero stays claim-free until D6 resolves. **Do not add height** — replace, don't stack. |
| **Seasonal / festival** | Temporary, config-driven commerce moments — collections, not ad walls. `[PRINCIPLE]` | None. **0** | Entire capability. | `FestivalCampaign` model + `CampaignRepository` (mock → backend). Home slot renders it only inside its date window. One campaign visual language, reused. `[ASSET REQUIRED]` for artwork. |
| **Personalisation** | Buy-again from real history; recently viewed; relevance over volume. `[PRINCIPLE]` | Order Again tab reads mock orders. No recently-viewed. **2.5** | Real history is `[BACKEND REQUIRED]`. Recently-viewed is buildable now (local). | Local recently-viewed ring (last 12 ids). Buy Again shows **current** price and availability and flags changes — never silently substitutes. |

### 2.2 Search & Product

| | Benchmark | Tazzzo current `[TAZZZO]` | Gap | Tazzzo solution |
|---|---|---|---|---|
| **Search** | Instant feel, recovery on empty, filters attached to results. `[PRINCIPLE]` | Debounced, non-blocking progress, Hindi synonyms, empty-state recovery, filters, **query now survives navigation (E2)**. **4.5** | Result-arrival transition; brand filter as sheet once catalogue grows (P2). | Keep. E3 motion for result arrival. |
| **Product card** | Compact but complete: brand, name, pack, price, MRP, savings, availability, limit, member benefit, ADD. `[PRINCIPLE]` | Name, unit, price, MRP strike, discount badge, availability, ADD/stepper. No brand, no rating, no unit price, **no Club benefit**. **3.5** | Missing brand line and member-saving hint. Rating/review count: `ratingCount` is 0 across the fixture catalogue by design (no fake ratings) — correct to hide. | `CommerceProductCard` showing brand + a **one-line Club hint only for members on eligible items**. Ratings render only when `ratingCount > 0`. Stay compact: two lines of metadata max. |
| **Product detail** | Answers: what, which variant, how much, what do I save, available?, when?, what's inside, who makes it, reviews. Empty sections hidden. `[PRINCIPLE]` | Hero, identity, highlights, info card (unit/brand/max per order), similar rail, pinned purchase footer. **4.0** | No delivery line beyond neutral promise (D4). No ingredients/nutrition/shelf-life — **and the model has no fields for them**. Reviews absent (correctly, no data). | Extend `Product` with **optional** `ingredients`, `nutrition`, `shelfLife`, `manufacturer`, `variants`. PDP renders a section **only when the field is non-null**. No section is ever shown empty; no fact is ever invented. `[BACKEND REQUIRED]` to populate. |
| **Delivery on PDP** | Delivery option/promise visible before purchase, consistently through checkout and Orders. `[CTO-RESEARCH]` | Neutral "Fast delivery" everywhere via `DeliveryCopy`; renders nothing where a time would go. **Honest, but thin.** **3.0** | No slot preview, no fee preview. | Once a slot is chosen (or default resolved), PDP/cart/checkout/review/confirmation/orders all show **the same** `DeliverySlot` from one source. Until serviceability exists: neutral copy stays. **Never fabricate ETA.** |

### 2.3 Cart — the control centre

| | Benchmark | Tazzzo current `[TAZZZO]` | Gap | Tazzzo solution |
|---|---|---|---|---|
| **Bill transparency** | Item total, MRP savings, product discounts, promo, member discount, credits, delivery, fees, total — each on its own line; a **total savings** figure that never blends unrelated savings. `[PRINCIPLE]` | Item total (MRP struck), delivery (with free threshold), handling, coins to earn, **Club savings line (new)**, grand total. `saved` = MRP only, kept separate from Club ✓. **4.0** | No promotion line (nothing to show). No "total savings" summary. Coins redemption hidden (D5). | `BillSummary` gains `promotionDiscount` and `appliedPromotions: List<AppliedPromotion>`. Cart shows each line **only when non-zero**, then one **"You saved ₹X"** that sums *only* realised savings, itemised on tap. |
| **Promotion eligibility** | Offers state eligibility, minimum, cap, limits; the customer is told **why** an offer does or does not apply. `[CTO-RESEARCH]` | **None.** **0** | Entire capability. | `Promotion` model with eligibility/min/max/limits/validity/exclusions/stacking; `PromotionEngine` (pure, tested) returns `AppliedPromotion` + `PromotionOutcome.NotApplied(reason)`. Reasons are **customer-visible copy**, not codes. |
| **Stacking** | Deterministic; mutually exclusive offers resolve to the best; the customer is told what won and what lost. `[PRINCIPLE]` | N/A. **0** | Entire capability. | Stacking is **config**: each promotion declares `stackable` + `priority`. Engine picks the best allowed combination; UI says *"Best offer applied — Club saves you ₹31; the coupon would have saved ₹24."* Nothing changes the total silently. |
| **Price / stock change** | Disclosed before checkout; never silently replaced. `[PRINCIPLE]` | `validateCart` on entry and in `placeOrder`; `PriceChanged` removes the line so the customer re-adds at the new price; `QuantityReduced` disclosed; restore reconciliation disclosed. **5.0** | None. This is a strength. | Keep. It already exceeds the benchmark principle. |
| **Club in cart** | Member value visible; non-members shown the exact value of joining. `[PRINCIPLE]` | **Four context-aware states (new)**: applied / member below threshold / non-member qualifying / non-member below. Never blocks checkout. **5.0** | — | Keep. |

### 2.4 Membership

| | Benchmark | Tazzzo current `[TAZZZO]` | Gap | Tazzzo solution |
|---|---|---|---|---|
| **Value proposition** | Membership combines delivery benefits with exclusive offers and other member advantages; value shown before the ask. `[CTO-RESEARCH]` | Landing: benefits → worked example → price → explicit ₹99 CTA. All from `MembershipConfig`. **5.0** on structure. | Benefit *types* are fixed to today's four. | `MembershipBenefitType` enum — `FREE_DELIVERY`, `PERCENT_DISCOUNT`, `MEMBER_OFFERS`, `EXTRA_COINS`, `REWARD_GIFT`, `PRIORITY_SUPPORT`, `FESTIVAL_PERK` — each **toggleable in config**, rendered only when enabled. **Not** copying any competitor's bundle; making Tazzzo's configurable. `[BUSINESS DECISION]` on which ship. |
| **Payment integrity** | Server-created order, server-verified signature, activation only after verification. (Razorpay's documented requirement.) | Implemented exactly so, behind `PaymentGateway`; `MockPaymentGateway` refuses to verify a signature it did not mint; PENDING ≠ FAILED. **5.0** client-side. | `[BACKEND REQUIRED]` for the real gateway calls. | Keep. Write the two endpoint contracts when the backend exists. |
| **Post-join value** | Savings tracker, progress, rewards, member offers — the retention loop. `[PRINCIPLE]` | Account row shows ₹ saved and eligible-order count. **No** progress bar, **no** next-milestone, **no** confirmation-screen progress, **no** Club savings on Orders. **2.5** | The loop is modelled (`MembershipCalculator.nextSpendMilestone/nextOrderMilestone`) but **not surfaced**. | `ClubProgressCard` (Account + order confirmation), Club savings line per order, milestone-unlock moment with E3 motion. This is the highest-leverage membership work remaining. |

### 2.5 Delivery, Address, Payment, Review

| | Benchmark | Tazzzo current `[TAZZZO]` | Gap | Tazzzo solution |
|---|---|---|---|---|
| **Delivery choice** | Real slots, contextual fees, consistent through the journey. `[CTO-RESEARCH]` | Slot step exists with 4 mock slots; `DeliverySlot` has **no fee field**; selected slot **not** shown on review/confirmation/orders. Generic "Express — as soon as possible" label. **3.0** | No fee per slot; no slot persistence into the order; no "Why this fee?" | `DeliverySlot.feeRupees` + `feeReason`; `Order.slot`; slot echoed on review, confirmation, order detail. "Why?" explains only reasons the rule engine actually produced. `[BACKEND REQUIRED]` for real slots. |
| **Delivery fee explanation** | Free vs fee stated; reason available (minimum, slot, location, membership). `[CTO-RESEARCH]` | "Free above ₹199" shown under the fee. **3.5** | Only one reason is ever shown; slot/membership reasons don't exist yet. | `DeliveryFeeBreakdown` from `ChargeRules` + slot + membership benefit, each contributor named. Only produced reasons shown. |
| **Payment methods** | Multiple methods; wallet/credit usage; clear separation of credits from the payable amount. `[CTO-RESEARCH]` | COD enabled; UPI/Card disabled "Coming soon" — **honest**. Coins redemption hidden (D5). **3.5** | India-first hierarchy not expressed (UPI should lead). Coins/Club not yet shown as separate from payable. | Payment step ordered **UPI → Cards → COD → Coins** (India-first, per CTO), each `enabled` server-decided. "Pay ₹X" is **always** `bill.grandTotal`. Coins/Club shown as deductions above the line, never inside the method list. `[BACKEND REQUIRED]` to enable UPI/Card; `[BUSINESS DECISION]` D5 for coins. |
| **Review** | Nothing hidden: address, slot, items, every discount, fees, total, method. `[PRINCIPLE]` | Address, items, bill, **"To pay" on every step** ✓, payment. Slot **not** echoed. Club line present. **4.0** | Slot; promotion lines (once they exist). | Add slot card and applied-promotions list to review. |

### 2.6 Post-purchase

| | Benchmark | Tazzzo current `[TAZZZO]` | Gap | Tazzzo solution |
|---|---|---|---|---|
| **Confirmation** | Not a dead end: slot, total, savings, progress, track/view/continue. `[PRINCIPLE]` | Success mark, amount, items, address, **paid-by**, coins earned. Success haptic (E1). **3.5** | No slot; no Club savings; **no progress** — the "moved you closer to your reward" moment is unbuilt; no track/view actions. | `ClubProgressCard` here first. Slot line. Track / View order / Continue. |
| **Order detail** | Permanent receipt + service centre: status, slot, items, discounts, savings, rewards, payment status, support, refund state, reorder. `[PRINCIPLE]` | Orders **list** only — status chip + 4-step rail + reorder. **No detail screen.** **2.5** | Entire detail surface. `Order` model lacks slot, discounts, payment status, cancellation/refund state. | `OrderDetailScreen`; `Order` gains `slot`, `appliedPromotions`, `clubDiscount` (via bill), `paymentStatus`, `fulfilment` state. Status enum extended: `CONFIRMED, PREPARING, PACKED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED, REFUNDED` — a model + UI change, done together. |
| **Tracking** | Live status ready for backend. `[PRINCIPLE]` | 4 static statuses from mock. **3.0** | Extended states; live source. | Timeline component over the extended enum; `[BACKEND REQUIRED]` for live updates. **No fake countdown.** |
| **Buy Again** | Real previous products, quick ADD, **current** price/availability, changed-product indication. `[PRINCIPLE]` | Order Again tab: mock orders, reorder → cart. **3.0** | Silent price/stock drift on reorder; no per-item quick ADD. | `ReorderCard` per item: current price, current availability, "price changed" / "unavailable" chips, quick ADD. Reorder goes through `addToCart` (enforced limits) — never silently substitutes. |
| **Support / refund** | Context-aware from the order; wrong/missing/damaged/expired/quantity/payment; refund status. `[PRINCIPLE]` | Help screen FAQs + WhatsApp + "help with an order" rows. Not order-aware. **3.0** | No order-scoped issue flow; no refund state. | "Need help with this order?" on order detail pre-filled with order id and lines → issue type → handoff. `[BACKEND REQUIRED]` for tickets/refunds. **No fake SLA.** |
| **Refund ↔ money integrity** | Discounts, coupons, rewards, coins reverse correctly; never duplicated. `[PRINCIPLE]` | Club progress is idempotent on order id ✓; coins on `!replayed` ✓. **No reversal path at all.** **2.0** | Cancellation/refund reversal of Club progress, coins, promotions. | `reverseOrder(orderId)` in the same repositories, idempotent on order id; tested with replay. `[BUSINESS DECISION]` on partial-refund rules. |

### 2.7 Cross-cutting

| | Benchmark | Tazzzo current `[TAZZZO]` | Gap | Tazzzo solution |
|---|---|---|---|---|
| **Trust claims** | Never claim cheapest/fastest/guaranteed without backing. | Neutral delivery (D4) ✓; coins config-driven (D5) ✓; **"SAVE 8–20%" and "India's first Voice Commerce" still shown** (D6). **3.5** | D6 unresolved. | `[BUSINESS DECISION]` D6. App already falls back claim-free with `savingsClaim = null`. |
| **Accessibility** | Every actionable node: label, role, state, action; real targets. | E1/E2 fixed F3/F4/F5; **the split-node pattern recurred twice**, so it is systemic. **4.0** | Un-audited surfaces remain (Help, Account rows, checkout selection rows post-refactor, new Club screens). | Codebase-wide sweep of "parent semantics/minSize + child click"; instrumented activation tests for every new control (membership CTA, pay CTA, slot, payment row). |
| **Motion** | Purposeful; reduced-motion respected; no perpetual decoration. | Press language, nav direction, ambient gate (E1/E2). **E3 roles not yet formalised.** **4.0** | Sheet motion; success/milestone choreography. | E3 roles applied to Club success, milestone unlock, slot selection, sheets. |
| **Perceived performance** | Immediate feedback; no dead screens. | Press feedback everywhere; skeletons exist but Home skeleton mismatches layout (E4). **3.5** | Skeleton fidelity; `[DEVICE REQUIRED]` for any latency number. | E4. **No performance number will be quoted from this emulator.** |

---

## 3. Why Tazzzo's version is better for *our* customer — not a clone

| Benchmark habit | Tazzzo's deliberate difference | Why |
|---|---|---|
| Multiple stacked promo banners | **One** campaign slot, date-windowed, replaced not stacked | Indian quick-commerce customers are shopping for tonight's dinner on a mid-range phone. Calm decides faster than loud. |
| "Up to X% off" headlines | The **exact rupee** this basket saves, or nothing | A ₹31 the customer can check beats a 20% they cannot. |
| Silent best-offer selection | Best offer applied **and the loser named** | Trust is built by showing the decision, not the result. |
| Universal "10-minute" promise | Slot the customer **chose**, echoed everywhere | A promise you keep on every screen beats one you break on the doorstep. |
| Membership as a toll gate | Membership as an **offer**; checkout never blocked | A ₹99 that pays for itself in visible savings sells itself; one that blocks the cart gets refunded. |
| Coins/wallet inside the payment list | Coins and Club as **deductions above the line**; "Pay ₹X" is always the true payable | The amount on the button must be the amount that leaves the account. |
| Generic support form | "Need help with **this** order" pre-filled | The app already knows the order id; making the customer type it is contempt. |

---

## 4. What this benchmark does NOT do

- It does not claim any competitor is worse or better in aggregate. It compares
  principles.
- It does not fill cells I cannot verify. `[PRINCIPLE]` means principle.
- It does not turn into a roadmap for copying features. Several benchmark habits
  are listed above precisely so Tazzzo can **decline** them.

---

## 5. Implementation order derived from the matrix

Ranked by what unblocks the most rows per unit of risk:

| Wave | Delivers | Rows unblocked |
|---|---|---|
| **C1 — Promotion engine + bill** | `Promotion`, `PromotionEngine` (pure, deterministic), `BillSummary.promotionDiscount/appliedPromotions`, "You saved ₹X", stacking + reasons. **Financial tests first.** | 2.3 ×3, 2.5 review, 2.6 detail/refund |
| **C2 — Delivery slots** | `DeliverySlot.fee`, `Order.slot`, slot echoed through cart → review → confirmation → orders, fee "Why?" | 2.2 PDP delivery, 2.5 ×2, 2.6 ×2 |
| **C3 — Post-purchase loop** | `ClubProgressCard` on confirmation + Account, Club savings on orders, `OrderDetailScreen`, extended status timeline | 2.4 post-join, 2.6 ×4 |
| **C4 — Payment step** | UPI-first ordering, deductions above the line, methods server-enabled | 2.5 payment |
| **C5 — Product confidence** | Optional PDP fields, `CommerceProductCard` with brand + Club hint | 2.2 ×2 |
| **C6 — Campaign system** | `FestivalCampaign` config + Home slot + collection screen | 2.1 ×2 |
| **C7 — Buy Again / basket** | `ReorderCard` with current price/availability; recently viewed; compact recommendation rail | 2.1, 2.6 |
| **C8 — Support & reversal** | Order-scoped help; `reverseOrder` idempotent | 2.6 ×2 |

Each wave: inspect → build → unit tests → smoke on device → regression before
commit → report with `[IMPLEMENTED] / [MOCKED] / [BACKEND REQUIRED] /
[ASSET REQUIRED] / [DEVICE REQUIRED] / [BUSINESS DECISION]` on every claim.

---

## 6. Blockers carried, none removed

| Tag | Items |
|---|---|
| `[BACKEND REQUIRED]` | Razorpay order creation + verification · authoritative Club state · serviceability · delivery slots · catalogue · stock · promotions · rewards · tickets/refunds |
| `[ASSET REQUIRED]` | Production login imagery · campaign artwork · category art · product photography · hero image without third-party trade dress |
| `[DEVICE REQUIRED]` | Real Android · real iPhone · every performance number |
| `[BUSINESS DECISION]` | D4 delivery promise · D5 coin economics · D6 claims · membership economics + caps · reward rules · cancellation/refund rules · promotion stacking policy · which benefit types ship |

---

## 7. Log
- **2026-09-05** — Benchmark opened and completed. Headline: no promotions layer
  exists; Club discount is correct but isolated. Competitor columns sourced as
  `[CTO-RESEARCH]` / `[PRINCIPLE]`, never presented as first-hand verification.

---

## 8. Addendum (2026-09-06) — what the reference screenshots actually show

The CTO supplied screenshots of three live apps (a noon-Minutes-market checkout,
Zepto, Blinkit). Unlike §2, these observations are **first-hand from the
images**, tagged `[SCREENSHOT]`. They are read for information architecture
and density — not copied.

| Surface | `[SCREENSHOT]` observation | Tazzzo response |
|---|---|---|
| **Checkout** | One screen does five jobs: savings header → offers & coupons row → optional tip → delivery instructions → payment summary with struck fees (`25.00 FREE`) → savings breakdown **by type** (price discount vs delivery savings) → persistent CTA. | Cart: realised-savings header with expandable breakdown by type (promotion / Club / **delivery waived, with reason**); delivery struck-through when waived; delivery-instruction chips at review (config-driven). **Tip: `[BUSINESS DECISION]`, not built** — an optional gratuity has revenue-recognition and reversal rules Tazzzo has not set. |
| **Product card** | Dense and uniform: image with floating `+`, price pill, struck MRP, `₹X OFF` in green, name, pack size. Nothing else. | Brand line (when present), price bold + struck MRP, **`₹X OFF` badge in Tazzzo orange** (rupees, not percent — what the customer can verify). Emoji imagery remains `[ASSET REQUIRED]`. |
| **Savings** | One number at the top ("Yay! You saved ₹35"), expandable. The breakdown separates discount-on-price from delivery-fee savings. | Same structure. **MRP comparison kept out of it** and relabelled "₹X below MRP on these items" so it cannot be confused with money kept. |
| **Slots / delivery** | Delivery context in the header of every checkout screen ("9 minutes delivery"); fee shown against the option. | Grouped slots (Next available / Today / Tomorrow), fee per slot with reason, recommended tag, sold-out state; chosen slot echoed on review, confirmation, orders list and detail. **No universal minute promise — D4.** |
| **Orders** | Card: status ✓, amount, item thumbnails, Rate / Order Again. Detail: status + arrival, items with struck MRP, bill with struck fees, invoice download, order id copy, address, Get Help. | Card gains "Saved ₹X" and the slot; tap opens a new **OrderDetailScreen**: status, slot, items, bill with struck waived delivery, Club line, payment, instructions, **order-scoped "Need help"**, Order again. **"Rate order": not built — no ratings system, and a rating UI with no backend is a fake.** Invoice download: `[BACKEND REQUIRED]`. |
| **Home** | Campaign hero with sub-tiles; coupon rail **stating thresholds**; Buy Again with current prices; deal rails with `₹X OFF`. | Coupon rail from `PromotionConfig` with thresholds and codes (data-backed); Buy Again already renders current catalogue products. Campaign hero `[ASSET REQUIRED]`. |
| **Help** | "Need help with this order?" → issue types (delivered product / rider / not received). Order already known. | Help opened from an order carries the id ("Help with order #…"). Issue-type routing `[BACKEND REQUIRED]` for tickets. |
| **Third-party brand imagery** | Every reference card shows real branded packshots. | Tazzzo **will not** fabricate or borrow these. `[ASSET REQUIRED — PRODUCTION PHOTOGRAPHY]`. |

**What the screenshots do NOT license:** pink/purple palettes, "9 minutes"
headers, tipping by default, ad-labelled product slots, or a fifth bottom tab.
Density is the lesson; the design system stays Tazzzo's.
