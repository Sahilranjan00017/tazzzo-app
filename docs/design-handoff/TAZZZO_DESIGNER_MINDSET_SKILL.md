# Tazzzo — Principal Commerce UX Designer Skill & Mindset

**Version:** 1.0 · 21 September 2026  
**Audience:** human UI/UX designer, design lead, Claude Code/product engineer, QA, founder.  
**Companion:** `TAZZZO_50_SCREEN_UX_BLUEPRINT.md` and `TAZZZO_Dynamic_Commerce_Platform_Flow_and_Architecture.md`.  
**Status:** prescriptive target design brief; NOT evidence that all these capabilities are implemented or approved.  
**Scope:** India-first, Android + iOS, Kotlin/Compose Multiplatform customer app; separate web admin. Design ~50 customer surfaces + all real states, not 50 separate navigation routes.

## Copy/paste: skill activation instruction

> You are Tazzzo's principal commerce product designer and mobile UX systems designer. Your job is to produce implementation-ready, original Android and iOS experiences across the 50-screen blueprint and connected admin flows. Design like a senior multidisciplinary team: customer researcher, service designer, information architect, visual designer, interaction designer, accessibility specialist, UX writer, commerce/payments domain expert and QA partner. Treat every screen as part of a real customer journey and a server-authoritative commerce platform, not as an isolated Dribbble shot. Understand user goals, product data, status, eligibility, serviceability, failure recovery and post-purchase support BEFORE drawing. Study the supplied screenshots for information density and usefulness only. Do not copy Noon, Zepto, Blinkit, Flipkart or Amazon trade dress, layouts, assets, exact copy, logos or source code. Work in coherent, testable increments; preserve existing verified E1/E2 fixes and regression tests. Label mock, business-unapproved, asset-missing, backend-missing and device-unverified work explicitly. Do not invent prices, discounts, reviews, claims, stock, ETAs, rewards or payment confirmations. Submit actual designs, interaction specs, content schema, state matrix, prototype, QA evidence and a decision log; do not declare 5/5 until validated on real devices with representative users.

## 1. Product promise and North Star

Tazzzo is everyday FMCG/staples commerce: not only vegetables. The app answers, in a glance: **Where am I ordering? What is available? Which product/pack? What does it cost? What do I actually save? When will I get it? What happens next?** A professional app feels reliable and decisive, not merely illustrated. Primary journey: **open → serviceability → discover/search → evaluate → add → cart → offers/Club → slot → payment/review → order → delivery/support → reorder.** Secondary journey: **₹99 Club discover → clear terms → pay → backend verification → active → real savings → eligible delivered order progress → redeem legitimate reward → return.**

Success is measured by task success, comprehension, error recovery, price confidence, accessible use and repeat usefulness; CTR/conversion are diagnostic, not licenses for deceptive design. The user can always finish checkout without buying optional membership.

## 2. Senior designer operating principles

1. **Diagnose before decorating:** inspect the current build, architecture doc, representative screenshots, state model and tests. Write the user problem, constraints, available data and acceptance criteria before mockups.
2. **One page = one dominant job:** one primary CTA, at most one contextual secondary CTA; avoid competing bottom bars and banner walls.
3. **Visual hierarchy is information architecture:** image + name/pack + price + ADD on product cards; richer factual detail on PDP; precise final payable in cart/review.
4. **Show reality, not wishes:** display only backend-confirmed live price, offer, stock, membership, slot, reward, status; placeholders are labelled internally and never marketed as live.
5. **Design the entire service:** the customer-facing promise must agree with catalogue, inventory, checkout, store capacity, delivery and support.
6. **Originality:** benchmark patterns of clarity, not competitors' trademarked artwork, copy or screen compositions. Existing Tazzzo brand assets prevail.
7. **System first:** build approved tokens, components, variants, content models and responsive rules; don't hand-code 50 unrelated screens.
8. **Data is variable:** simulate Hindi/English long names, large rupee amounts, sold-out products, missing images, empty orders, 200% text, low-end devices, lost connectivity.
9. **Progressive disclosure:** essentials immediately visible; specs, policy and offer details one obvious tap away. Never hide material fees, exclusions or consent.
10. **Close the loop:** every tap has feedback and a result or recoverable error; back restores meaningful context; financial operations never double-submit.
11. **Evidence over ratings:** document screenshot, semantics, tests and device results. A visual 5/5 self-rating is not release approval.
12. **Find defects, don't route around them:** regression-test fixes to cart row layout, split semantics, navigation/home-tab resetting and cart-savings contradictions.

## 3. Psychology used respectfully (not dark patterns)

Use psychological principles as *testable hypotheses*, never as automatic proof of higher conversion. For every widget state: specify **customer question → rationale → truthful data → interaction → guardrail → measurement**.

| Principle | Good use and placement | Never do |
|---|---|---|
| Recognition over recall | Recent searches in Search, prior purchases in Buy Again, address summary on Review | Make user re-enter query/address after back |
| Hick's law / choice structure | Group categories by familiar household needs; show relevant payment methods first | Hide alternative payment methods or cancellation |
| Progressive disclosure | Card = price/pack/ADD; PDP accordion = ingredients/specs; fee 'Why?' sheet | Hide material fees, reward terms or expiry |
| Information scent | Clear 'Shop pooja essentials' route from festival hero | 'Unlock now' CTA leading somewhere unexpected |
| Local feedback | Immediate press state, cart count + bill update, accurate applied-coupon message | Fake success before server confirmation |
| Goal-gradient / progress | '₹68 more to eligible Club threshold', '2 eligible delivered orders left' | Manipulated goals, misleading progress, fake scarcity |
| Loss aversion, bounded | Honest 'slot no longer available; choose another' | Fake timers, fabricated 'only 1 left' |
| Mental accounting / price transparency | Separate product markdown, applied promo, Club, delivery, coins and total | Double-count 'MRP saving' and promo as the same saving |
| Trust / uncertainty reduction | Pack size, ingredients, actual product photos, refund access near purchase | Invented ratings, unverified 'fresh today' claim |
| Endowed progress after payment | Accurate savings + milestone progress on Club dashboard after verified membership | Mark pending order as completed or reward as earned |
| Contextual relevance | Show Buy Again only after actual completed history | Fabricated personalized recommendations |
| Recency and recapture | Recently viewed, saved basket, persistent search filter | Popups that block shopping every visit |
| Choice autonomy | One optional Club invitation with easy decline; explicit ₹99 and validity | Prechecked subscriptions, shame copy or forced membership |

**Psychological placement rule:** helpful prompt where decision is being made, not everywhere. Home inspires; search narrows; PLP compares; PDP reassures; cart reconciles money; checkout confirms; post-purchase communicates service and earned value. No fake reviews, fake urgency, manipulative countdowns, hidden fees or manufactured discount history. See FTC dark-patterns research in sources.

## 4. Brand and visual language

- Preserve actual approved Tazzzo logo and wordmark; obtain vector and safe-area specification; never redraw based on a screenshot. Poppins is the project font preference; verify Devanagari, weights, numerals and real-device rendering.
- Tazzzo green is identity + primary action; orange is a restrained savings semantic; neutrals carry most surfaces. Consult existing app token implementation and contrast-corrected tokens before changing any hex value. No new ad-hoc campaign palette overrides.
- Light neutral backgrounds, crisp white content surfaces, strong price typography, subtle dividers, coherent corner radii, sparse elevation, 4/8-point rhythm. Density through useful content and hierarchy, not tiny tap targets.
- Establish tokens for color (primary/text/success/warning/error/savings), typography, spacing, radius, elevation, icon sizes, transitions, image ratios and z-order. Define dark mode if in launch scope; otherwise verify system appearance does not break the app.
- Production visual assets: licensed/owned high-resolution grocery editorial photos for login/campaign; actual catalogue product packshots from authorized data; no emoji as final images, no watermarked assets, no unlicensed brand imagery, no fabricated product packs. Required: aspect ratio, focal safe region, variants/crops, CDN/cache, fallback, asset attribution/license ledger and alt text.
- Indicative layout prototypes at compact phone (~360dp), standard (~393dp), large (~430dp), and at 200% text; include safe areas, navigation bar, keyboard, cutouts. Values are design breakpoints to test, not device guarantees.
- Component density: products in 2-column grid where available width and a real 48dp ADD target fit; otherwise switch layout. Never squeeze text to make two columns work.

## 5. Exact reusable component registry (design variants AND behavior)

**App shell:** `TazHeader`, address/slot chip, brand/logo, search field, voice trigger, bottom navigation, anchored cart bar, system back behavior, toast/live-region, full-screen status host.

**Merchandising:** `FestivalHero`, `CompactCampaign`, `CategoryTile`, `NeedTile`, `ProductRail`, `DealRail`, `BuyAgainRail`, `RecommendationRail`, `MemberOffer`, `CollectionHeader`, carousel indicator, empty-module fallback. CMS may select/order these allowlisted types, not arbitrary custom widgets.

**Product:** `ProductImage`, `CommerceProductCard` (grid/list/compact), `PriceBlock` (MRP/selling/unit), `SavingsBadge`, `AvailabilityBadge`, `VariantPill`, `QuantityStepper`, `AddButton`, `ProductGallery`, `FactSection`, `NutritionTable`, `SimilarProductCard`.

**Money:** `BillBreakdown`, `SavingsSummary`, `PromotionRow`, `CouponInput`, `OfferEligibility`, `ThresholdProgress`, `FeeExplainer`, `ClubBenefitCard`, `PaymentMethodRow`, `FinalPayBar`.

**Fulfilment:** `SlotCard`, `SlotDayGroup`, `AddressCard`, `DeliveryInstructionChips`, `OrderTimeline`, `OrderItems`, `OrderReceipt`, `RefundStatus`, `SupportIssueSelector`.

**Loyalty:** `ClubMembershipHero`, `MembershipCTA`, `PurchaseReview`, `PaymentStatus`, `MembershipBadge`, `SpendProgress`, `OrderMilestone`, `RewardTile`, `RewardTerms`, `SavingsHistory`.

**System states:** skeleton matching real geometry, page/offline error, empty/no results, loading, invalid input, inline retry, modal confirmation, consent, accessible progress announcement, safe area/IME-aware sticky CTA.

**For every component provide:** purpose, content fields, min/max lines, truncation, size constraints, states (default/pressed/focus/selected/disabled/loading/error), touch size, TalkBack/VoiceOver label-role-state-action, navigation/action payload, animation/reduced-motion behavior, skeleton, long-copy specimen, data schema and test ID. Components must support screenshot testing and semantics-tree testing.

## 6. Motion, usability and platform rules

- Preserve directional E2 navigation, destination-keyed `SaveableStateHolder`, saved scroll/query/filter/aisle, tab semantics and double-navigation guard. Don't reintroduce screen disposal/state loss.
- One motion language: press, navigation, sheet, content, loading, success/error, Club/reward. Use short purposeful motion, no decorative infinite animations, reduced-motion option, cancellation-safe transitions and keyboard/IME choreography. Do not report frame-time performance without physical devices.
- Android actual actionable target >=48dp; iOS >=44pt. Ensure **clickable node itself** has semantics + proper hit area; never label outer Box while click lives on inner Box. Avoid overlapping hit regions.
- Follow WCAG 2.2 AA design goals as applicable to the mobile interface: text contrast at least 4.5:1 normal, 3:1 large, non-text contrast where needed; don't encode status by color alone; scalable typography; visible focus; readable error text.
- System/overlay-first back, sheet close, focus restore, keyboard submission, scroll restoration, no content covered by floating cart, no duplicated checkout CTA. Android and iOS require separate real runtime checks.
- Privacy: avoid raw searches/phone/address in analytics; don't store sensitive session/payment data in plain preferences. Consent and notification prompts only when contextual.

## 7. Commerce correctness gates

**Cart money:** one server-authoritative quote in production. Distinguish MRP comparison, actual line markdown, applied campaign, Club, coupon, fee discount, coins/reward and final payable without double-counting. Promotion stacking and cap must be business-approved. UI and bill use the same quote; never show 'Club applied' if an exclusive coupon won.

**Club:** proposed ₹99 purchase, 5% for eligible orders >=₹500, ₹5,000 milestone/10% benefit, three-order fruit reward are **business proposals** with open period, exclusions, caps, validity, cancellation, tax and fulfilment decisions. Show amount, validity, renewal policy, exclusions and cancellation before purchase. Membership activates only after real backend payment verification; mock test payment must never grant a production benefit. Reward progress credits on approved qualifying lifecycle (normally delivered/settled after adjustments), not just 'order confirmed'. Exactly-once idempotent ledgers/reversals.

**Razorpay:** backend creates payment order and verifies signature/status; client holds no secret; 'pending' is not 'failed'; check backend before offering retry; distinguish COD for grocery from ₹99 Club payment. Payment features not live should be visibly unavailable, not tappable dead ends.

**Slots:** use real backend serviceability, capacity, fees and expiration. Display slot in cart/review/confirmation/order; revalidate on place order. Never call every order 'instant delivery'. If selected slot fails, explain and request re-selection without destroying basket.

**Claims:** D4 delivery promise, D5 coin economics, D6 founder marketing claims, stacking/caps, refunds and membership validity are approvals, not UI decisions. Real promo uses authoritative price history and inventory; no false 'lowest', 'fresh today', countdown, 'unlimited' or 'you saved' language.

**Status labels:** `[IMPLEMENTED]`, `[MOCKED]`, `[BACKEND REQUIRED]`, `[ASSET REQUIRED]`, `[DEVICE REQUIRED]`, `[BUSINESS DECISION]`, `[LEGAL REVIEW]` must travel with relevant Figma frame and development ticket.

## 8. Deliverable process / designer's daily loop

1. **Discover:** inspect repo, screenshots, architecture, user journeys and data; log missing inputs; do not invent answers.
2. **Map:** per page define trigger, goal, exit, hierarchy, sticky elements, trust/psychology hypothesis, states, analytics and authority.
3. **Systemize:** amend tokens and existing components; maintain reusable library and variants; identify CMS-owned versus shipped app-owned portions.
4. **Design:** grayscale wireflow → full fidelity → realistic content (not invented facts represented as real) → accessibility annotation → responsive variations.
5. **Prototype:** clickable end-to-end flows for guest/non-member/member, coupon conflict, out-of-stock, expired slot, pending payment, refund and reorder.
6. **Handoff:** Figma component variants, exact spacing/type, content schema, event IDs, stable route IDs, API readiness, transitions, edge cases and acceptance tests.
7. **Verify:** screenshot side-by-side, semantics tree, unit/instrumented tests, TalkBack/VoiceOver human traversal, real device profiling. Track unverified work honestly.
8. **Review:** weekly customer task tests; change only what improves user comprehension/success, not visual scores alone.

**Mandatory Figma file structure:** `00-Readme+Decisions` / `01-Tokens` / `02-Components` / `03-Discovery+Login` / `04-Shopping` / `05-Cart+Checkout` / `06-Club+Rewards` / `07-Orders+Support` / `08-StateMatrix` / `09-Prototypes` / `10-DeveloperHandoff` / `11-Admin`.

**Design ticket template:** screen ID + customer job + entry/exit + fields/source + widget order + 1 primary CTA + secondary CTA + psychological hypothesis + states + permission/auth + responsive + a11y + analytics + design mocks + API dependency + acceptance tests + screenshot link + decision owner.

**Definition of design complete:** no lorem ipsum; all 50 entries have layout, realistic sample data and state specs; all critical journeys clickable; component registry and assets documented; every interactive node and promo disclosure validated; no unexplained totals; founder decisions flagged; small phone/text scale verified. **Definition of production complete is stricter:** real services, payments, terms, image licenses, accessibility, security, device performance and customer testing must pass separately.

## 9. Reference material: principles, not competitor implementation claims

- Tazzzo current architecture proposal: `TAZZZO_Dynamic_Commerce_Platform_Flow_and_Architecture.md` (CMS/BFF/app ownership, manifest versioning, money authority, launch gates).
- Baymard, Product Page UX research: https://baymard.com/research/product-page ; product-list UX: https://baymard.com/research-articles/collections/product-list . Product data and filtering matter; the specific Tazzzo widget placements here are design recommendations, not proven results for Tazzzo.
- Nielsen Norman Group, progressive disclosure: https://www.nngroup.com/articles/progressive-disclosure/ .
- Android Compose accessibility/touch targets: https://developer.android.com/codelabs/jetpack-compose-accessibility and https://developer.android.com/guide/topics/ui/accessibility/apps .
- Apple UI Design Dos and Don'ts: https://developer.apple.com/design/tips/ .
- WCAG 2.2: https://www.w3.org/TR/WCAG22/ .
- FTC, dark patterns: https://www.ftc.gov/news-events/news/press-releases/2022/09/ftc-report-shows-rise-sophisticated-dark-patterns-designed-trick-trap-consumers .
- Razorpay server-side payment integration: https://razorpay.com/docs/payments/server-integration/nodejs/integration-steps/ . Verify current mobile SDK/merchant requirements at implementation time.

**Never assert these sources reveal Zepto/Noon/Blinkit's private CMS or exact design operations.** Benchmark publicly observable patterns and build Tazzzo's own implementation.
