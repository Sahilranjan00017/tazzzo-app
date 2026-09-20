# Tazzzo — Complete 50-Screen Customer UX Blueprint + 20-Surface Admin Design Map

**Version:** 1.0 · 21 September 2026  
**Companion skill:** `TAZZZO_DESIGNER_MINDSET_SKILL.md`. **Architecture:** `TAZZZO_Dynamic_Commerce_Platform_Flow_and_Architecture.md`.  
**Audience:** founder, product, Figma designer, Compose Android/iOS engineers, API engineers, QA.  
**Nature:** proposed production design SPECIFICATION, not claim of completed implementation. Existing code is the source for present status; backend/business decisions require separate approval.  
**Scope count:** 50 unique customer design surfaces (some implemented as sheets, dialogs or steps); 20 proposed web-admin surfaces; shared states and reusable components are additional Figma frames, not extra screens.

## START HERE — the brief in 90 seconds

Design Tazzzo as an original, premium, India-first FMCG and everyday grocery app. Benchmark the **completeness, information density and operational clarity** of mature apps, not their proprietary pixels. Own brand green for identity/CTA, restrained orange for savings, Poppins, genuine imagery and transparent commerce. Deliver working user flows, not an attractive but inert Home. Put the right fact next to the relevant decision: location and slot in header/checkout, pack and price next to ADD, ingredients/weight on PDP, realized savings in cart, membership terms before ₹99, final money on review, order status and support after purchase. Content rearranges via the existing proposed CMS/BFF manifest; pricing, stock, rewards and fees never become CMS/client-authoritative. Design Android and iOS states separately where platform behavior differs. No unsupported delivery promise, fake urgency, fabricated rating, stock, reward or savings.

**The 7 customer questions:** (1) Is my location serviceable? (2) Can I find it? (3) Is it the correct product and pack? (4) What exactly will I pay/save? (5) When will it arrive? (6) What is happening to my order/payment? (7) Can I resolve a problem/reorder easily?

## Legend used below

- **P0:** necessary for coherent live buying and/or safety; **P1:** strong commerce maturity; **P2:** optional, defer when evidence/ops absent. Existing implementations are not assumed production-ready.
- **Widgets:** visible components in top-to-bottom order; `[sticky]` indicates a pinned section.
- **Human benefit:** design hypothesis (recognition, visibility, trust, choice structure, habit), NOT proven uplift.
- **Authority:** `CMS` for editorial content; `CAT` catalogue; `PRC` pricing/promotions; `INV` inventory; `LOC` serviceability/slot capacity; `ID` auth/customer; `CLUB` server membership/rewards; `ORD` order state; `PAY` verified payment; `SUP` support; `LOCAL` non-financial session only.
- **Minimum states:** baseline each page = loading, empty if applicable, error with retry, offline treatment, long copy, accessibility focus, low-screen, font scaling and returning/back. Additional page-specific cases are below.
- **Commitment:** a screen may render an internal demo fixture, but label demo screenshots `[MOCKED]`. Never use it as evidence of a functioning backend.

## A. Global shell and page placement rules

1. **Header**: serviceable location/address and selected delivery context before commercial banners; change address opens clear selector. Never render invented ETA.
2. **Search**: obvious near top of Home and PLP where needed; accessible mic action is independent and genuinely actionable. Persistent query state across navigation.
3. **Bottom tabs**: use only real destinations, proposed Home / Categories / Deals / Orders / Account; do not silently alter live tab semantics. Selected state and Android back proven. A prominent floating cart may appear above nav only when nonempty and never occlude a control.
4. **Primary CTA**: one clear action at decision point, label with exact amount where financial (`Pay ₹X`, `Join Club — ₹99`) and action (`Choose slot`, `Place order`). Stick to safe area; survive keyboard and 200% text.
5. **Disclosure proximity**: if a badge says X% off, its threshold/cap/eligibility is one clear tap away. Fee's `Why?` is actually tappable. Membership validity/renewal is before pay.
6. **Product list hierarchy**: real image, full understandable name (allow 2–3 lines), pack/variant, current price, MRP if valid, ADD; contextual stock/realized savings. Don't make every card an encyclopedia.
7. **Product PDP hierarchy**: gallery + selection + price/delivery + ADD above information details; specs and terms progressively disclosed without hiding essential restrictions.
8. **Persistent accuracy**: client renders one server quote and explanation; any cart price/stock change requires disclosure and reacceptance before order placement.
9. **Support and recovery**: every irreversible action has confirmation/undo as appropriate; pending payment never shown as failure; back/close never silently charges or discards a paid membership.
10. **Personalization**: missing history means no fake Buy Again; anonymous user receives neutral bestsellers based on actual permitted data, not fabricated “for you” behavior.

## B. Page-by-page deliverables (50 total)

### Onboarding & login — 6

#### T01 — Splash / launch routing [P0]
- **Job/entry:** return to usable app quickly; never trap user in a branded loading animation. Route by valid auth/session and serviceability.
- **Widgets/order:** restrained logo and neutral background → small real loading state only if needed → app shell or genuine recovery action. No sale popups.
- **Human benefit:** clarity/continuity. **Action:** no artificial Continue; optional retry on real initialization failure. **Data:** LOCAL + ID + non-financial cached CMS.
- **States/accept:** cold/warm start, expired auth, offline, corrupted cache, reduced motion, screen reader. Time-to-first-usable measured on device rather than guessed.

#### T02 — Welcome / value proposition [P0]
- **Job:** help a first-time visitor understand the service and choose to proceed.
- **Widgets/order:** original hero with licensed high-res milk/bread/fruits/grocery basket → Tazzzo wordmark → short promise (“Everyday essentials, made easier” if approved) → phone/login CTA → browse-as-guest if policy permits → Privacy/Terms links.
- **Human benefit:** first-impression trust; show real product relevance, not 5 carousel ads. **Data:** approved brand assets + LEGAL.
- **States/accept:** safe hero crop on compact screens, CTA above safe area, alt text, no unsupported instant delivery claim, permissions deferred until needed.

#### T03 — Phone number entry [P0]
- **Job:** start authentication with minimal friction and no unsolicited marketing consent.
- **Widgets/order:** title → country code + labeled number field → input help/error → [sticky] Continue → privacy/terms and optional marketing consent separate from core auth.
- **Human benefit:** single-purpose form. **Data:** ID. **Actions:** validate, submit once, show spinner without changing CTA height.
- **States/accept:** invalid length, rate limit, offline, disabled button explanation, paste, keyboard open/back, accessible label, preserved number on retry.

#### T04 — OTP verification [P0]
- **Job:** prove phone possession; clearly distinguish test mode from live auth.
- **Widgets/order:** masked destination → accessible code input/autofill → verify → resend countdown if backend provides rate limit → change number → error/help.
- **Human benefit:** user control/feedback. **Data:** ID backend. **Action:** verify + route, not local magic OTP.
- **States/accept:** wrong/expired code, resend rate limit, delayed SMS, offline, loading, app background/restore, voiceover focus and clipboard limitations.

#### T05 — Location permission & serviceability [P0]
- **Job:** determine whether Tazzzo can serve user without forcing precise location unnecessarily.
- **Widgets/order:** why location matters → Use current location / Enter address or PIN manually → permission explanation → verified area result → unsupported-area alternative.
- **Human benefit:** autonomy and predictable eligibility. **Data:** LOC; OS permission. **Action:** request OS location only after intent.
- **States/accept:** denial, approximate permission, GPS timeout, unsupported Ejipura/other area, PIN mismatch, recovery via manual entry, don't assume city implies serviceability.

#### T06 — Add address [P0]
- **Job:** capture delivery-ready address before fulfilment; reduce failed drop-offs.
- **Widgets/order:** map/search or manual entry → building/flat, street, landmark optional, recipient, phone as needed → address type → delivery note → Save.
- **Human benefit:** error prevention. **Data:** ID + LOC; sensitive fields protected. **Action:** validate with serviceability; never infer exact unit from map.
- **States/accept:** geocode mismatch, unsupported PIN, blank required fields, no Maps API, address edit vs new, keyboard/IME, privacy, saved-address confirmation.

### Shopping & discovery — 11

#### T07 — Home [P0]
- **Job:** get users to a relevant product within seconds while keeping live delivery context clear.
- **Widgets/order:** location/slot + account/Club shortcut → prominent search/mic → category shortcuts → ONE contextual festival/offer hero → relevance-ranked collections (Buy Again if actual history; daily essentials; deals; fresh; basket complement) → floating cart above nav.
- **Human benefit:** recognition and task relevance; do not present 8 full-height banners. **Data:** CMS/BFF + CAT/INV/PRC/LOC/ORD/CLUB. **Actions:** every hero/rail goes to a valid route.
- **States/accept:** first-time/returning/member, no campaign, missing image, no serviceability, empty history, expired manifest fallback, scroll restoration, no unbounded ambient animation.

#### T08 — Search entry & suggestions [P0]
- **Job:** directly find the user's intended product without retyping.
- **Widgets/order:** focused labeled search field + clear/mic → recent searches (local/private, removable) → suggestions grouped Products/Categories/Brands → trending only if grounded → search button.
- **Human benefit:** recognition over recall. **Data:** search API + LOCAL. **Actions:** tap suggestion carries query/route and returns with same context.
- **States/accept:** Hindi/English synonyms, typo tolerance, keyboard close/back, mic permission denied, offline recent search, empty query, privacy-safe analytics (no raw query).

#### T09 — Search results [P0]
- **Job:** compare exact matches quickly and resolve ambiguous pack sizes.
- **Widgets/order:** query header → suggestion spelling correction with undo → result count when real → sort/filter → applied filter chips → PLP products → no-results recovery.
- **Human benefit:** clear information scent. **Data:** CAT/search + INV/PRC. **Actions:** product opens PDP, ADD inline; preserve query/filter/scroll on return.
- **States/accept:** no matches, partial results, serviceability exclusion, different variant, stale item, keyboard, empty category, relevance feedback not fabricated.

#### T10 — All Categories [P0]
- **Job:** let unfamiliar users browse using household language.
- **Widgets/order:** sticky title/search → icon/photographic tiles by approved taxonomy → secondary subcategory labels → contextual collection promo only if active → bottom tabs/cart.
- **Human benefit:** familiar mental model, scannability. **Data:** CAT taxonomy + CMS. **Actions:** category tile always reaches a populated or honest empty PLP.
- **States/accept:** taxonomy breadth, category with zero serviceable stock, icons/assets, long localized labels, scroll state, accessibility grid order.

#### T11 — Category / PLP [P0]
- **Job:** browse a manageable subset and add without losing place.
- **Widgets/order:** category title → subcategory rail/sidebar where justified → optional authentic merchandising header → sort/filter → applied chips → stable-key product grid/list → floating cart.
- **Human benefit:** progressive narrowing/comparison. **Data:** CAT/search + INV/PRC + CMS. **Actions:** filter, sort, ADD, PDP, remove chip.
- **States/accept:** stable image and text geometry, 2-column only when controls fit, lazy-list keys, full-product-name layout, pagination, sold out, scroll pixel retention.

#### T12 — Deals / price-drop collection [P1]
- **Job:** find actual discounted, eligible items without marketing misinformation.
- **Widgets/order:** collection title + campaign terms → filter by relevant categories → product cards with genuine MRP/price comparisons → applied promotions and caps link.
- **Human benefit:** price confidence. **Data:** PRC/validated price history + CAT/INV + CMS. **Actions:** product view/ADD, conditions expand.
- **States/accept:** expired campaign, zero eligible stock, no historical price (no 'price drop' claim), location-specific eligibility, discount changes in cart.

#### T13 — Festival / seasonal collection [P1]
- **Job:** complete festival preparation rather than just view artwork.
- **Widgets/order:** original time-bounded hero (Janmashtami as example only) → Shop by need (pooja/dairy/sweets/fruits/fasting where appropriate) → eligible favourites → collection products → Club offer when approved → practical essentials.
- **Human benefit:** task framing and category recognition. **Data:** CMS schedule + CAT/INV/PRC/LOC. **Actions:** every subcategory and CTA valid; sold-out products excluded or honestly marked.
- **States/accept:** start/end timezone, campaign gone after expiry, missing art fallback, seasonal claims reviewed, original IP, no fake countdown.

#### T14 — Product Detail Page [P0]
- **Job:** answer 'Is this exactly what I want and can I trust it?'
- **Widgets/order:** image gallery + image count → brand/full name + exact SKU/pack → variant → price block/MRP/real savings/unit price → stock + verified slot info → [sticky] ADD/quantity → highlights → ingredients/nutrition/specs/manufacturer/storage/shelf life only when sourced → related goods → policy/support.
- **Human benefit:** uncertainty reduction, essentials before deep detail. **Data:** CAT verified product facts + INV/PRC/LOC + real reviews if they exist. **Action:** changing variant must recalculate every relevant field.
- **States/accept:** image failure, very long name, dairy/fresh/household-specific fields, no reviews means no stars, weight range disclosure, product unavailable, changing price, back to original grid offset.

#### T15 — Variant / pack selector [P0 when multi-variant]
- **Job:** pick correct size/type without confusing distinct SKUs.
- **Widgets/order:** current selection → variants with size, effective unit price, price, stock → single selected state → confirm/ADD (or immediate selection with feedback).
- **Human benefit:** compare alternatives. **Data:** CAT+INV+PRC. **Actions:** update active SKU, image, price and cart reference consistently.
- **States/accept:** one variant unavailable, pack out of stock, per-order limit, long units, price update mid-selection; do not silently substitute.

#### T16 — Buy Again collection [P1]
- **Job:** refill known products without repeatedly searching.
- **Widgets/order:** previous delivered orders grouped logically → current images/names/pack/current prices/availability → ADD individually → optional Add available items after review.
- **Human benefit:** recognition/habit. **Data:** ORD completed history + current CAT/INV/PRC. **Actions:** do not use old SKU/old price blindly.
- **States/accept:** no history hides from Home, discontinued SKU, new pack size, partial availability, old address change, item cap.

#### T17 — Wishlist / saved items [P1]
- **Job:** preserve intentional future purchases, separate from cart.
- **Widgets/order:** saved items → live price/availability → ADD → remove → collections if supported → empty explanation.
- **Human benefit:** external memory. **Data:** ID saved list + CAT/INV/PRC. **Actions:** save/unsave in place, appropriate confirmation.
- **States/accept:** logged-out/guest policy, removed SKU, stale price, rapid save/un-save idempotence, empty list.

#### T18 — Voice shopping [P1]
- **Job:** capture spoken household shopping items and convert safely to SKU-level draft basket.
- **Widgets/order:** meaningful mic action → permission/transcription status → interpreted phrase → candidate product match and variant/quantity clarifier → editable draft → confirm ADD.
- **Human benefit:** reduced typing but strong user control. **Data:** authorized voice/STT + CAT/search; don't silently place orders. **Actions:** repair unknown item and reject wrong quantity.
- **States/accept:** Hindi/code switch, noisy environment, denial, timeout, missing SKU, multi-item ambiguity, transcript privacy, screen-reader mic activation (F4 regression).

### Cart & offers — 4

#### T19 — Cart [P0]
- **Job:** inspect items, reconcile money and proceed with confidence.
- **Widgets/order:** delivery address + slot summary/edit → honest realized savings title → stock/price notices → product rows (IMAGE + NAME/PACK + PRICE + STEPPR) → compact offer/Club/coupon affordances → optional basket complements → bill (separate MRP, applied discounts, fee, coins, final) → [sticky] Continue/Choose delivery with actual amount.
- **Human benefit:** price transparency and quick corrections. **Data:** server quote PRC + CAT/INV/LOC/CLUB; LOCAL cart is only a draft. **Actions:** quantity updates recalc one consistent bill; no duplicate clicks.
- **States/accept:** empty/expired stock, name-price column never starved by stepper, coupon/Club sentence agrees with bill, threshold base explained, price change acknowledged, offer conflict, 200% text, floating CTA unobscured.

#### T20 — Offers & coupons [P0 if coupons launched]
- **Job:** understand applied, available and unavailable promotions.
- **Widgets/order:** applied offer + real savings → coupon input + Apply → available offers grouped → unavailable offers with reason → offer terms → Save/return.
- **Human benefit:** choice transparency and control. **Data:** PRC promotions quote, usage limits. **Actions:** apply/remove, show explicit exclusivity and best-offer logic.
- **States/accept:** invalid/expired/exhausted, below threshold, exclusive Club conflict, input typo, network pending, exact updated quote mirrored in cart; no promise if not applied.

#### T21 — Offer details [P1]
- **Job:** answer conditions before user changes basket.
- **Widgets/order:** benefit statement → eligible products/cart threshold → caps/validity/usage → exclusions → stacking policy → current eligibility → explicit Apply/Shop eligible.
- **Human benefit:** reduce surprises. **Data:** PRC/approved CMS copy. **Actions:** return to prior cart without clearing input.
- **States/accept:** location exclusions, member-only gating, changing campaign, undisclosed cap forbidden; long terms expandable but key restriction visible.

#### T22 — Reward / gift selector [P1; only when redemption live]
- **Job:** let eligible users redeem an earned gift with no hidden conditions.
- **Widgets/order:** available rewards with expiry and terms → eligible gift products, e.g. fresh fruit up to approved cap → live stock/weight → selection → apply → bill reflects reward.
- **Human benefit:** earned value without confusion. **Data:** CLUB reward ledger + CAT/INV/PRC. **Actions:** server reserves/redeems exactly once, revert if failed.
- **States/accept:** locked vs pending vs earned, reward already redeemed, cart change below minimum, substitution, sold out, expiry, refund reversal; hide live redemption when backend absent.

### Checkout & payment — 8

#### T23 — Checkout address selection [P0]
- **Job:** select a validated destination with minimal re-entry.
- **Widgets/order:** selectable saved address cards → serviceability/delivery context → edit/add → recipient and instructions summary → Continue.
- **Human benefit:** error prevention/recognition. **Data:** ID + LOC. **Actions:** update address; invalidate previous slot if new address differs.
- **States/accept:** unsupported address, missing detail, default mismatch, logged-out flow, keyboard/back, private address not in analytics.

#### T24 — Choose delivery slot [P0]
- **Job:** choose an available, honest time window and understand fee.
- **Widgets/order:** address summary → 'Choose delivery' → real day/time groups → slot cards (window, fee, eligible member benefit, availability) → optional fee 'Why?' → [sticky] Continue.
- **Human benefit:** agency and expectation management. **Data:** LOC backend capacity/fee. **Actions:** tap reserves/selects per backend contract, preserved across checkout.
- **States/accept:** no slots, only next-day, expires during selection/review, capacity lost, changed fee, timezone, no invented ASAP option, require explicit re-selection.

#### T25 — Payment methods [P0]
- **Job:** select a usable, secure India-relevant method.
- **Widgets/order:** final payable bar → UPI (when live), cards (when live), COD if eligible/live → coins/rewards as separate deductions, not payment rails unless explicitly supported → selected indicator → [sticky] Continue/Pay ₹X.
- **Human benefit:** clear choice and exact amount. **Data:** PAY available methods + PRC quote + LOC/COD eligibility. **Actions:** unsupported methods visibly unavailable with honest reason.
- **States/accept:** offline, amount updates, failed preflight, unverified payment method, COD fee eligibility; never expose inactive UPI/Card as working.

#### T26 — Payment processing [P0 when online]
- **Job:** explain what is happening while protecting against double charge.
- **Widgets/order:** selected method + exact amount → gateway handoff/progress → truthful 'checking payment' if uncertain → safe return/status check.
- **Human benefit:** system-status visibility. **Data:** PAY backend/webhook reconciliation. **Actions:** one purchase intent, no duplicate Pay, no generic retry while pending.
- **States/accept:** app kill/background, success callback lost, pending/captured mismatch, bank return, duplicate callback, accessibility announcement. Distinguish test and live.

#### T27 — Final checkout review [P0]
- **Job:** make the last decision informed and reversible until commit.
- **Widgets/order:** delivery address/edit → selected valid slot/edit → items/edit → promo/Club/reward lines → all fees/taxes → **TOTAL ₹X** → selected payment → consent/policy as required → [sticky] Place order or Pay ₹X.
- **Human benefit:** eliminate price surprises. **Data:** authoritative PRC/INV/LOC/CLUB/PAY quote. **Actions:** final revalidation; if anything changes, explain before confirmation.
- **States/accept:** price/stock conflict, slot expired, no network, auth expiry, duplicated place taps, back preserves inputs and cart.

#### T28 — Payment recovery [P0 when online]
- **Job:** resolve pending/failed/cancelled outcomes without accidental extra charge.
- **Widgets/order:** precise status heading → what it means → transaction reference → Check status → conditional Retry only after backend confirms safe to do so → help link.
- **Human benefit:** reduce financial anxiety. **Data:** PAY server state. **Actions:** query existing order before new payment intent.
- **States/accept:** pending for long time, payment succeeded but app missed callback, definitive failure, user cancellation, duplicate gateway events, offline.

#### T29 — Order confirmation [P0]
- **Job:** confirm the order truly exists and set delivery expectations.
- **Widgets/order:** backend-confirmed success → order ID + selected slot + payable/paid state → next step → Track order / Continue shopping → Club contribution clearly provisional until qualifying completion.
- **Human benefit:** closure and status visibility. **Data:** ORD + PAY + LOC + CLUB; **do not** count 'placed' as completed reward order.
- **States/accept:** COD vs prepaid, pending capture, backend order unknown, slot change, failed gift fulfillment, accessibility success feedback, no autoplay carnival.

#### T30 — Cart price / stock resolution [P0]
- **Job:** reconcile changes before an incorrect order can proceed.
- **Widgets/order:** explanation of changed products → before/after actual price or available qty → item-level Resolve/Remove → refreshed total → [sticky] Review changes/Continue.
- **Human benefit:** prevent surprise/substitution. **Data:** CAT/INV/PRC. **Actions:** explicit acceptance, no hidden edits.
- **States/accept:** several changes, zero stock, quota limit, coupon becomes invalid, Club threshold lost, retry conflict; don't erase unaffected cart.

### Membership & rewards — 6

#### T31 — Tazzzo Club landing [P1; launch only with approved economics]
- **Job:** understand benefits and whether ₹99 is worth it.
- **Widgets/order:** badge/hero → plain-price + period/renewal policy → 5%/₹500 eligibility only if approved → worked example clearly illustrative → approved milestone/gift/terms → [sticky] Join Club — ₹99 → decline/back.
- **Human benefit:** informed value comparison and autonomy. **Data:** CLUB config + LEGAL. **Actions:** no prechecked renewal, no unsupported 'unlimited'.
- **States/accept:** already active, expired, non-serviceable, config missing, discount cap undecided, refund/cancellation terms, different price/validity; hide pay if material terms absent.

#### T32 — Club ₹99 purchase confirmation [P1]
- **Job:** consent to exact product being purchased before leaving to payment.
- **Widgets/order:** membership name/term/price → benefits/eligibility summary → tax/total, renewal/cancellation links → TEST PAYMENT badge in non-live env → [sticky] Pay ₹99.
- **Human benefit:** transparency at irreversible point. **Data:** CLUB authoritative purchase quote + PAY order creation. **Actions:** single idempotent intent.
- **States/accept:** pricing changed, user already member, creating order, declined payment, app background; do not charge twice or grant membership locally.

#### T33 — Club activation / welcome [P1]
- **Job:** confirm verified activation and immediately show unlocked *actual* benefits.
- **Widgets/order:** success only after server confirms active → badge → validity date/period → approved benefits → receipt/payment reference → [sticky] Return to cart or Start shopping depending origin.
- **Human benefit:** positive closure and next-step clarity. **Data:** CLUB verified membership + PAY. **Actions:** return to initiating cart preserved, correct Home tab reset for Start shopping (F6).
- **States/accept:** pending shows 'checking', not welcome; test badge in mocks; no fake benefit 'unlocked' if policy inactive; reduced-motion check reveal.

#### T34 — Club dashboard [P1]
- **Job:** answer membership status, savings and proximity to real milestones.
- **Widgets/order:** status + validity → actual lifetime/current-period savings distinct → benefit summary → eligible spend progress → eligible completed-order progress → redeemable/pending reward → history and terms.
- **Human benefit:** accurate progress with no artificial gamification. **Data:** CLUB server ledger + ORD settled/eligible events. **Actions:** benefit/receipt/reward details.
- **States/accept:** no qualifying order 0/N, paid but pending activation, cancellations/reversals, cap reached, membership expired, offline last-updated stamp, progress > cap clamped visually.

#### T35 — Reward details [P1]
- **Job:** understand exactly how a reward is earned and redeemed.
- **Widgets/order:** reward title/product/cap → earned/pending/locked status → order/spend criteria → expiry and exclusions → eligible items/Use reward only if truly redeemable.
- **Human benefit:** clarity and earned-value trust. **Data:** CLUB+CAT/INV+PRC. **Actions:** navigate to gift selector only when valid.
- **States/accept:** 1/3 order example must count delivered qualifying orders, in-flight pending clearly labelled, item unavailable, redeemed, reversed, expired, support path.

#### T36 — Club savings history [P1]
- **Job:** reconcile membership purchase cost and realised benefits over time.
- **Widgets/order:** actual savings total with definition → membership fee separately → itemized qualifying orders + applied Club discount → refunds/reversals → filter date if needed.
- **Human benefit:** verifiable value. **Data:** CLUB/PRC/ORD financial ledger. **Actions:** receipt/order drilldown.
- **States/accept:** no savings, non-stackable coupon won, partial refund, negative adjustment, expired period, no 'you earned' for estimate.

### Orders & fulfilment — 8

#### T37 — Orders list [P0]
- **Job:** see current order first and find historic purchases quickly.
- **Widgets/order:** active order with truthful status/window → history grouped date → item thumbnails/total/Club savings if applied → View order / Buy again on eligible.
- **Human benefit:** recognition and information scent. **Data:** ORD. **Actions:** status refresh and drilldown.
- **States/accept:** no orders, cancelled, failed payment, long histories/pagination, empty backend error, serviceability change on reorder.

#### T38 — Order details / receipt [P0]
- **Job:** permanent record of what was bought, paid and promised.
- **Widgets/order:** order ID/status → slot/address → immutable purchased product snapshots + pack/qty/paid price → original invoice/bill discounts/fees/Club → payment/refund → tracking/help/invoice/reorder.
- **Human benefit:** auditability. **Data:** ORD financial snapshot + PAY/LOC. **Actions:** download invoice only if generated; no fabricated tracking.
- **States/accept:** delivered/cancelled/partially refunded, replaced item, COD/prepaid, uncertain payment, item image removed from catalogue.

#### T39 — Order tracking [P0 if status available]
- **Job:** show what happens after ordering, including delays honestly.
- **Widgets/order:** current confirmed status → order timeline (placed/confirmed/preparing/packed/out for delivery/delivered as actually applicable) → verified delivery window → support shortcut → map only if real tracking data.
- **Human benefit:** visibility reduces uncertainty. **Data:** ORD + fulfilment events + LOC. **Actions:** refresh/help; no simulated moving rider dot.
- **States/accept:** delayed, partly packed, cancelled, no rider API, missed ETA, app background; don't promise 10-minute delivery.

#### T40 — Order help entry [P0]
- **Job:** start contextual resolution from the specific order.
- **Widgets/order:** order summary → issue categories (missing/wrong/damaged/quality/late/payment/refund) → existing open case → self-service action or Contact support.
- **Human benefit:** no need to remember order ID. **Data:** ORD+SUP. **Actions:** creates case with idempotency and consent.
- **States/accept:** eligible/ineligible issues, support unavailable, active case, wrong item photos opt-in, privacy and response expectations.

#### T41 — Cancellation request [P0 if cancellations supported]
- **Job:** decide whether cancelling is possible and understand consequences.
- **Widgets/order:** order + cancellable status → reason optional/required by policy → fee/refund/Club reward impact → Confirm cancellation / Keep order.
- **Human benefit:** informed control. **Data:** ORD cancel eligibility + PAY/CLUB. **Actions:** server-authoritative cancel, never local toggle.
- **States/accept:** already packed/dispatched, racing dispatch, cancellation succeeded, partial cancel, pending refund, lost network, duplicate tap.

#### T42 — Refund / issue request [P0 if issue resolution live]
- **Job:** report concrete item or payment problem without unnecessary friction.
- **Widgets/order:** order → item selector → reason → quantity/photos where genuinely needed → likely next step and eligibility → submit → case number.
- **Human benefit:** fairness and guided problem solving. **Data:** SUP+ORD/PAY. **Actions:** submit once and show next steps.
- **States/accept:** missing item/no photo, multiple affected items, upload fail, offline draft, partial refund and gift/coin reversals; don't promise auto-refund if not supported.

#### T43 — Refund status [P0 if refund process launched]
- **Job:** track resolution and expected next update.
- **Widgets/order:** case/order reference → request/approved/initiated/completed or denied with explanation → amount and method → verified timeline/date → contact support.
- **Human benefit:** status visibility and financial trust. **Data:** PAY/ORD/SUP. **Actions:** view evidence or follow up, no fake final status.
- **States/accept:** bank processing, failed payout, partial amount, membership/reward reversal, duplicate event, status stale stamp.

#### T44 — Reorder review [P1]
- **Job:** verify an older order can be recreated with current catalogue/prices.
- **Widgets/order:** previous order context → matched current SKUs with changes flagged → removed/substituted products requiring explicit selection → current total → Add available to cart.
- **Human benefit:** minimizes rework without silent errors. **Data:** ORD history + CAT/INV/PRC. **Actions:** reconcile before adding; never place automatically.
- **States/accept:** renamed/removed SKU, new size, price increase, delivery area changed, non-serviceable item, duplicate add; all changes disclosed.

### Account & support — 7

#### T45 — Account hub [P0]
- **Job:** find orders, support, Club and settings without long cluttered list.
- **Widgets/order:** profile/auth → contextual Club compact dashboard/status → shortcuts Orders/Support/Addresses → rewards/coins (only approved) → grouped preferences/Privacy/Terms/Logout.
- **Human benefit:** functional grouping. **Data:** ID+CLUB+ORD. **Actions:** each row real route, payment/Club status never locally invented.
- **States/accept:** guest, active/expired/pending Club, no history, dense label accessibility, correct Home tab after Start shopping (F6).

#### T46 — Profile [P0]
- **Job:** view/edit owned account details and privacy choices.
- **Widgets/order:** verified phone/read-only where appropriate → editable name/email with validation → notification preferences/consent → Save.
- **Human benefit:** control and error avoidance. **Data:** ID. **Actions:** update backend; don't expose sensitive data in analytics.
- **States/accept:** invalid email, offline update, session expiry, irreversible phone-change verification, keyboard accessibility.

#### T47 — Address book [P0]
- **Job:** manage multiple deliverable addresses.
- **Widgets/order:** saved address cards with default/serviceable flags → Set default/Edit/Delete → Add address; change delivery location action.
- **Human benefit:** memory and predictability. **Data:** ID+LOC. **Actions:** deletion confirmation, slot invalidation if changing checkout location.
- **States/accept:** zero addresses, unsupported address, attempted delete last/current selected address, privacy, address sync conflict.

#### T48 — Coins / wallet [P1; financial features gated]
- **Job:** distinguish earned coins, usable coins and actual cash/refund balances.
- **Widgets/order:** verified available balance with valuation/expiry terms → earning history → redemption eligibility → transactions → support. Never call coins cash unless actual wallet product exists.
- **Human benefit:** transparent mental accounting. **Data:** CLUB/coins backend ledger + PAY wallet if separately approved. **Actions:** redemption only if economics D5 and backend are live.
- **States/accept:** pending/expired/reversed coins, duplicate order events, no expiry decision, unsupported cash withdrawal, ledger mismatch; hide wallet if not a real feature.

#### T49 — Help center [P0]
- **Job:** self-serve simple questions and get human/available support fast.
- **Widgets/order:** contextual active order shortcut → searchable FAQ categories (delivery, product, Club, payment, refund, account) → current incident/known outage if verified → Contact support.
- **Human benefit:** recognition, reduced time to resolution. **Data:** SUP/CMS approved FAQ + ORD. **Actions:** route to relevant answer or support.
- **States/accept:** FAQ missing, unauthenticated, expired links, offline contact fallback, actual contact channel/hours not invented.

#### T50 — Support conversation + legal/about [P0, two surfaces in one scope item]
- **Job:** allow an actual help interaction and expose required legal/brand documents. **Designer must supply TWO separate Figma frames/routes; this combined accounting item is a planning exception.**
- **Widgets/order, support:** case context + clear agent/bot identity → messages → attachment (if supported) → send + case status + privacy. **Legal/about:** About Tazzzo, Terms, Privacy, cancellation/refund, Club T&C, contact and version.
- **Human benefit:** service continuity and informed trust. **Data:** SUP + LEGAL approved URLs/content. **Actions:** send message or open actual legal destination, not inert labels.
- **States/accept:** no live chat vendor, response delay, attachment failure, bot handoff, no legal content = launch blocker; designers may split into T50a/T50b, making 51 actual Figma surfaces while preserving legacy 50 planning IDs.

> **Counting clarification:** The earlier 50-item estimate grouped Help/Support and Legal/About. In reality, legal and support conversation need independent design frames. Plan **50 scope items, at least 51 distinct Figma frames** before adding variants. Likewise a membership payment status may share the payment-processing component, but must have distinct content states. Do not use the round number to omit a necessary page.

## C. Cross-screen psychological widget placement — practical guide

| Decision moment | Best widget and location | User need | Behavior and guardrail |
|---|---|---|---|
| Open Home | Address/availability at top | 'Can I shop here?' | location/serviceability backend; no phantom delivery promise |
| Home first screen | Search + compact taxonomy above most ads | 'Where do I start?' | one purposeful campaign, not stacked full-height heroes |
| Festival visitor | Need-based mini-categories below hero | 'What do I need to celebrate?' | only relevant available products, original imagery |
| Search typing | Real suggestions/recent searches below field | 'Find it faster' | preserve query; don't log raw PII |
| PLP | Filter/sort + visible selected chips | 'Narrow my choices' | results and selection stay on back |
| Product card | Pack + price + ADD in stable zones | 'Is this the right thing?' | real stock, large hit target, no fake ratings |
| PDP near ADD | Pack variant and delivery availability | 'Can I receive this exact variant?' | recalc price/stock/slot on change |
| Cart top | Accurate savings summary | 'How much am I actually saving?' | total realized savings, no double-count |
| Cart before checkout CTA | Club prompt *only if relevant* | 'Would ₹99 benefit me?' | actual eligible saving, decline without blocking |
| Cart offer row | Apply coupons with reasons | 'Which deal works?' | engine-decided stacking, explain losers |
| Cart near threshold | Progress toward legitimate benefit | 'How far away?' | correct eligible base; no pressure/fake goal |
| Checkout slot | Time + fee + explanation | 'When and why this fee?' | only backend slots; expiry recovery |
| Payment | Exact payable adjacent to Pay | 'What am I committing?' | no surprise fee or preselected paid add-on |
| Confirmation | Order ID + slot + status | 'Did it work?' | server-confirmed state only |
| Club dashboard | savings + milestones + terms | 'Was membership worth it?' | ledger-backed, progress after eligible order finality |
| Orders | timeline + Help/Buy Again | 'Where is it / can I repeat?' | no pretend moving rider; current price on reorder |

## D. Four exemplar layout stacks the designer must hand off precisely

### D1. Home visual hierarchy
```
[Status bar / safe area]
[Deliver to: address v]             [Club/account]
[Selected delivery choice if known; else Choose delivery]
[Search products...                 mic]
[Horizontal category navigation, 6-8 visible at a time]
[One active seasonal hero with clear CTA]           <-- may be absent
[Buy Again: 1-row, only if real history]             <-- may be absent
[Today's relevant offers / top essentials: compact]
[Product rails with actual packshot + price + ADD]
[Optional Club/reward context, never mandatory ad]
[Persistent cart above bottom nav if nonempty]
[Bottom tabs]
```
CMS may reorder eligible approved shopping modules, but **header, search, critical commerce facts, floating-cart safety and bottom nav remain app-controlled**.

### D2. Product card anatomy
```
[High-quality image with neutral background]    [context badge only if true]
[Brand, if provided]
[Readable full name, max lines with clear PDP fallback]
[Variant/weight/volume + optional unit price]
[Current price ₹X] [MRP ₹Y strikethrough if legitimate]
[Real saving label, never fabricated]           [ADD / quantity]
[Low stock only if actual] [Club saving only if quote confirms]
```
Design grid/list/compact variants. Real tappable actions >= platform guidance; width budgets must prevent the cart-row stepper layout regression.

### D3. Cart and final payable
```
[Deliver to + Choose slot / Edit]
[Realised savings ₹X; explainable]
[Stock/price changes inline if any]
[ITEMS: image + name/pack + line total + 48dp stepper]
[Apply offers & coupons] [Club message matches quote]
[Complete basket, at most one small optional rail]
[BILL]
  Items current selling-price subtotal                  ₹...
  Applied product/promo discounts                       -₹...
  Applied Club discount (ONLY IF APPLIED)               -₹...
  Eligible coupon (ONLY IF APPLIED)                     -₹...
  Delivery + actual fees/taxes                           ₹...
  Coins/rewards if actually redeemed                    -₹...
  TOTAL TO PAY                                          ₹...
[Why? accessible link for each non-obvious fee]
[sticky Choose slot / Continue · exact amount]
```
MRP comparison appears separately and must never be counted twice as a realized discount; no contradictory savings headline/card/bill.

### D4. Club membership purchase and aftercare
```
[Club plan benefit page: price ₹99 + period + renewal/cancel policy]
[Illustrative ₹700 x 5% = ₹35, clearly example and only if eligible]
[Terms/exclusions/cap disclosure]
[Join Club — ₹99] -> [confirmation] -> [backend creates order]
-> [gateway checkout] -> [backend verifies captured payment]
-> [active membership & receipt OR pending/failed/cancelled recovery]
-> [return to original cart or correct Home tab]
-> [actual savings history & milestone progress]
```
Do not treat illustrative sample savings as guaranteed; after placing a grocery order show progress as pending until the defined qualifying completion event, then credit once.

## E. Page state matrix: required Figma variants and engineering tests

Designer must produce at least the following grouped variants; this is a starting minimum, not a promise that 100–150 frames is sufficient after QA.

| Family | Core variants |
|---|---|
| App launch/auth | cold loading, guest, logged in, expired token, OTP error/rate limit, permission denied, offline |
| Serviceability | supported, unsupported, address mismatch, no slots, stale ETA |
| Home/CMS | new/returning/Club, no campaign, campaign ends, missing art, no order history, no relevant stock |
| Search/category | empty query, suggestions, typo, zero results, filters, long title, category unserviceable |
| Product | ready, missing image, no rating, sold out, low stock, variant change, max qty, price change |
| Cart | empty, 1 item, 17+ items, coupon exclusive conflict, Club eligible/ineligible, gift, stock fail, offline |
| Checkout | slot available/expired, changed fee, COD only, payment disabled, quote changed, invalid address |
| Payment | creating, gateway open, verifying, captured, pending, failed, cancelled, recovered, duplicate callback |
| Club | guest, not member, purchase pending, verified active, expired, 0-progress, near threshold, milestone, reversal |
| Order | placed, preparing, delayed, delivered, cancelled, refund initiated/completed, tracked/untracked, reorder changes |
| Support | FAQ available/unavailable, ticket open, agent unavailable, attachment fail, delayed reply |
| Global | font 200%, Android back, iOS swipe/back, TalkBack/VoiceOver, dark/system appearance, weak network, reduce motion |

### Acceptance test set per screen (minimum)

- One visible primary action, predictable back/close and no unlabelled interactive nodes.
- Actual clickable target >=48dp Android / >=44pt iOS, no hit-area overlaps. Test interaction with screen reader, not only semantic text presence.
- No cut-off content at compact screen width and 200% text; CTA not obscured by nav/cart/keyboard.
- If a statement mentions money/ETA/stock/reward, link to authoritative field and error fallback.
- Asynchronous actions have idle/loading/success/pending/failure and duplicate-action control; no false optimistic financial success.
- Verify precise screen screenshot + semantic assertions + navigation/scroll restoration + business assertions as relevant.
- Product names/prices visible in cart even with stepper; coupon conflict card and bill agree; start-shopping resets both route AND selected tab.

## F. Proposed admin product: 20 separately designed web surfaces

These are **proposed Tazzzo internal workflows**, not verified Zepto/Flipkart tooling. CMS owns content; the commerce backend owns transactions. Every admin mutation needs roles, draft/review/publish, audit and rollback where applicable.

| ID | Admin surface | Essential widgets / controls | Validation & safeguards |
|---|---|---|---|
| A01 | Command dashboard | orders, sales, exceptions, inventory alerts, failed payments, stale campaigns | real time window + source labels; no decorative vanity KPI |
| A02 | Home Builder | approved block palette, drag reorder, target audience/market, preview | allowlisted component types; static shell protected; manifest version |
| A03 | Campaign list/calendar | drafts/live/expired, schedule, owner, market, quick deactivate | timezone, overlapping priority, visibility/audit |
| A04 | Campaign editor | title/copy, licensed hero, CTA, SKU collections, dates, location | image license, no false claims, CTA resolvable, approval |
| A05 | Banner/asset library | upload, crop, ratio, alt, mobile preview, license metadata | no blurry/watermarked images, accessibility, CDN version |
| A06 | Category/taxonomy | category tree, display rules, assigned SKUs, preview | follow ratified schema; no generalizing unresolved rice axes |
| A07 | Product catalogue | SKU, brand, images, barcode/pack, descriptions, category attributes | data lineage, required category fields, no invented ingredients |
| A08 | Inventory/store | per-store sellable/reserved qty, threshold, exceptions | concurrency and audit; no client-authority |
| A09 | Price management | SKU price, MRP, effective time, approval, comparison rules | cap/error detection; immutable price-change history |
| A10 | Promotion builder | promo type, eligible base, min/cart, cap, date, audience, priority | stack conflict simulator, approval and exact quote tests |
| A11 | Coupon manager | codes, budgets, per-user/total usage, messaging | normalized errors, usage counters server-side |
| A12 | Club plans | ₹99 plan, validity/renewal, benefits, exclusions/caps, version | commercial/legal sign-off before payment live |
| A13 | Milestone/reward manager | thresholds, eligible order event, gift SKU/cap/expiry | one-time ledger, cancellations/refunds reversal |
| A14 | Fulfilment slots | store/day/window/capacity/fee/serviceable PIN | race control, TTL, never promise unavailable slot |
| A15 | Orders operations | queue, lifecycle, picking/packing, exceptions, history | role-limited edits, event journal, accurate customer status |
| A16 | Payments/refunds | Razorpay events, capture/verification, reconciliation, refund | secrets server only, ledger + idempotency, no raw PCI data |
| A17 | Customer/support | case triage, order-linked details, approved actions, SLAs | least privilege, PII redaction and consent |
| A18 | Analytics/experiments | campaign funnels, cohorts, crash/jank, experiment allocation | privacy-safe events; no winner claimed on insufficient data |
| A19 | Roles/approvals | role grants, two-person approval for money changes, audit logs | separation of duties and access review |
| A20 | Publish/control center | preview device/market, validations, publish/rollback/kill switch | immutable versions, canary, last-known-good fallback |

**Admin designer must prototype a complete festival sale:** create campaign → select products → confirm per-store stock → configure capped promotion → check Club stacking → upload licensed assets → preview compact/large phones → approve → schedule → publish → observe → kill/rollback → expire and reconcile. A valid-looking CMS must not be able to create an invalid discount or delivery promise.

## G. Data/content contract per visual block

| UI type | CMS may control | Domain must resolve | On missing/invalid data |
|---|---|---|---|
| Festival hero | approved text, artwork, CTA route, schedule, campaign id | location/eligibility, live campaign validity | remove block; don't show expired CTA |
| Product rail | ordered SKU IDs, section label | products, live price/stock, permitted location | filter invalid SKUs; hide empty rail |
| Deal tile | campaign and message intent | genuine savings, cap, eligibility | remove invalid claim and actionable offer |
| Club card | copy variant, placement | status, paid membership, applied discount/progress | generic factual entry only; no fake ₹ saved |
| Delivery chip | layout placement | supported address, real slots, fees | 'Choose delivery' or unavailable state |
| Order summary | no monetary editing | verified server quote and order receipt | block commit until quote recovers |
| Buy Again | placement | delivered history and live SKU match | hide if no history |
| Reward card | approved artwork/copy | earned/redeemed/expired state, item stock | locked or unavailable explanation |

CMS manifest is versioned with safe fallback, release time, locale, market, component registry compatibility and route allowlist. New layout component requires native app build; CMS may not arbitrarily inject code/styles. See architecture companion.

## H. Handoff package, phased work and release gates

### Deliverables (designer → engineer)

- Figma file structured by tokens/components/50 page scopes/state matrix/prototypes/admin; include at least 51 distinct base frames due T50 split.
- A one-page screen spec for each ID (already seeded in section B), exact component/spacing/type variant and data source, copy and truncation, CTA route, permission, analytics and back behavior.
- Four clickable prototypes: first-order guest/new user; Club join → paid/verified → qualifying order progress; festival/offer conflict; failure/recovery (price change → slot expired → pending payment → help).
- Asset manifest: current filename, rights holder/license, ratio/resolutions/focal point/alt text, CDN identity, missing status.
- Server mapping: mock/client/authoritative status for every financial, inventory and fulfilment statement.
- Issue register for unresolved business choices: Club fee/period, 5% and cap, ₹5,000 milestone/10% validity, reward 3 orders/fruit cap, D4/D5/D6, offer stacking, delivery charges/slot periods, refund/returns and terms.
- Acceptance evidence: screenshot per key state, interaction recording, accessibility traversal, unit/instrumented coverage, physical Android/iPhone smoothness when available.

### Recommended implementation order

1. **Foundation:** brand tokens, high-quality licensed assets sourcing, design system, product image, screen shell, error state; document backend and business constraints.
2. **P0 shopping:** T01–T11, T14–T15, T19 and T23–T30, T37–T40, T45–T47/T49/T50 legal. Validate real quote+stock+delivery+order path before launch.
3. **Commerce depth:** T12–T13, T20–T22 only when offers/redemption operational; T16–T18, T41–T44 and T48 behind live support/reward capabilities.
4. **Club:** T31–T36 only with approved economics, exact terms, server-authoritative eligibility and real payment verification. The existing mock Club journey must not become a real charge/benefit by accident.
5. **Dynamic campaigns/admin:** A01–A20 phased; begin A02–A05/A07–A10/A14/A20 with permissions/audit so promotions and layout don't require APK releases.
6. **Quality & production:** feature-flagged rollout, app store/release checks, real photos and legal text, Android+iOS device regression, screen-reader validation and incident rollback.

### Definition of done: per-page vs full app

**Page design done:** correct hierarchy, widgets, content fields, fully linked actions, variants, accessible states, compact/large screenshots, correct mock labeling, developer spec and test assertions.  
**Journey implemented:** actual navigation works, state persists, errors resolve, no dead CTA, CI suites green or failures documented.  
**Production-live:** backend-authoritative price/member/slot/order, real payment reconciliation, licensed images, verified privacy/terms, accurate promotions and device/accessibility evidence. **50 screens alone do not equal a launchable app.**

## I. Primary research references

This playbook is a Tazzzo-specific synthesis of user requirements and external usability guidelines; placements, counts and prototypes are **design recommendations**, not experimentally established Tazzzo results.

- Baymard Product Page UX: https://baymard.com/research/product-page
- Baymard Product Lists: https://baymard.com/research-articles/collections/product-list
- Nielsen Norman Group, progressive disclosure: https://www.nngroup.com/articles/progressive-disclosure/
- Android accessibility / 48dp touch target: https://developer.android.com/codelabs/jetpack-compose-accessibility
- Apple 44pt hit targets and high-res asset quality: https://developer.apple.com/design/tips/
- WCAG 2.2 contrast/focus/text: https://www.w3.org/TR/WCAG22/
- FTC guidance on deceptive patterns: https://www.ftc.gov/news-events/news/press-releases/2022/09/ftc-report-shows-rise-sophisticated-dark-patterns-designed-trick-trap-consumers
- Razorpay integration security: https://razorpay.com/docs/payments/server-integration/nodejs/integration-steps/

**Instruction to developer:** Use `TAZZZO_DESIGNER_MINDSET_SKILL.md` as operating instructions and this blueprint as the pagewise acceptance checklist. First inventory existing screens/components and map each T-ID to existing/new/sheet; create the shared design system; redesign and verify complete customer journeys in batches. Do not assert anything is built merely because this document specifies it.
