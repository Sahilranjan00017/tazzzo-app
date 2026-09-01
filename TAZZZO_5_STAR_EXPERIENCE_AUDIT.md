# TAZZZO — 5/5 EXPERIENCE AUDIT

**Opened:** 2026-09-01 · **Status:** AUDIT COMPLETE / FIXES NOT YET STARTED
**Target:** 5/5 across the complete experience (raised from the 4.8/5 gate passed 2026-08-31)

---

## 0. STATUS OF THE DESIGN FREEZE

`docs/DESIGN_SPEC.md` carries a **FROZEN** banner dated 2026-08-31, and
`BLOCKERS.md` records the Final Visual QA Gate as PASSED at the 4.8/5 target.

**The freeze is reopened by the design owner on 2026-09-01**, with a new target
of 5/5, for an experience-quality pass only. This is recorded here rather than
by silently editing the frozen document, so the next session does not inherit a
contradiction. The brand identity — palette, wordmark, Poppins, the green/orange
semantic split — is **not** reopened. Refinement, not redesign.

---

## 1. METHOD, AND WHAT EACH SCORE IS WORTH

Scores are **not** inflated to make the report look good. A component scores
against nine axes, and it is **not 5/5 if any single axis is obviously weak**:

`Visual · Interaction · Motion · Responsiveness · Content clarity · State handling · Accessibility · Performance · Platform consistency`

**Verification legend — every finding below carries one:**

| Tag | Means |
|---|---|
| `[CODE]` | Verified by reading the source. Exact file and line given. |
| `[VISUAL]` | Verified against rendered evidence in `docs/screenshots/`. |
| `[RUN]` | Verified by driving the running app. **Not yet performed for this pass.** |
| `[DEVICE]` | Requires physical hardware. **I cannot perform this.** |
| `[HUMAN]` | Requires a human session (screen-reader traversal). **I cannot perform this.** |
| `[DEP]` | Capped by an external dependency (asset, backend, or your decision). |

**Honesty rule applied throughout:** where a score cannot honestly reach 5
because of something outside this codebase, the row says so and the dependency
is named. It is not marked "done".

---

## 2. THE HEADLINE FINDING

The previous phase built a genuinely good *static* design system — tokens,
contrast-audited palette, commerce-specific type scale, honest empty/error
states, real out-of-stock semantics. That work is sound and is why the app
photographs well.

**What is missing is almost entirely the layer between the pixels and the
finger.** Four structural gaps account for the majority of the distance between
4.8 and 5, and every one of them is invisible in a screenshot:

| # | Gap | Evidence | Severity |
|---|---|---|---|
| **S1** | **No component in the app has a designed pressed state.** `collectIsPressedAsState` appears **zero** times across 65 `clickable` call sites. Feedback is whatever Material's default ripple happens to do — and on 5 of those sites it is explicitly disabled. | `[CODE]` grep: 0 hits | **Critical** |
| **S2** | **Navigation has no direction.** `Crossfade` is the *only* transition in the app — Home→Category, Category→PDP, PDP→Cart and every back gesture are the same 300 ms cross-dissolve. Forward and back are visually identical. | `[CODE]` `App.kt:39` | **Critical** |
| **S3** | **Zero state is preserved across navigation.** `rememberSaveable` appears **zero** times against 27 scroll/list/query state holders. Combined with S2 (Crossfade destroys the outgoing composable), returning from a PDP resets the category grid to the top, and losing the search screen loses the query, the filters and the results. | `[CODE]` grep: 0 vs 27 | **Critical** |
| **S4** | **No haptics anywhere.** `performHapticFeedback` appears **zero** times. Add-to-cart, quantity limit reached, order placed, error — none of them touch the one channel that makes a phone feel like a product rather than a web page. | `[CODE]` grep: 0 hits | **High** |

These four are the backbone of the fix plan. Nothing else on this list moves
the needle as much.

---

## 3. COMPONENT AUDIT

Scores are **before** the fix pass.

### 3.1 Buttons & actions

| Component | Before | Issue | Root cause | Planned change |
|---|---|---|---|---|
| **`PillButton`** (the app's primary CTA) | **3.5** | Has default + disabled only. **No loading state and no success state.** Callers fake loading by rewriting the label ("Sending OTP…", "Verifying…") while the button stays **fully enabled and tappable**; the second tap is swallowed by an `if (!sending)` guard, so the user gets a ripple and *nothing happens*. `[CODE]` `Components.kt:226-271` | The component models `enabled` but not `busy`. | Add `loading` and `success` states with an in-button progress indicator; make `loading` imply not-clickable; add a pressed-scale response. |
| **`QuantityStepper` − / +** | **3.5** | Hit boxes are `buttonHeightSm` = **40 dp**, below the app's own stated **44 dp** accessibility minimum in `DESIGN_SPEC.md` and `TazSize.touchTarget`. `[CODE]` `Components.kt:521-560` | Silhouette height was reused as touch size. | Keep the 40 dp visual pill, expand the *touch* target to 44 dp without changing layout. |
| **`QuantityStepper` ADD→stepper** | **4.0** | Hard cut between "ADD" and "− 1 +". Silhouette is correctly preserved (no reflow — a good decision), but the swap is instant and unacknowledged. | No content transition. | Fast (150 ms) content transition inside the fixed silhouette + a light haptic on add. |
| **At-limit `+`** | **4.0** | Correctly shows a message, but delivered by a toast with no motion and no haptic. Dimmed `+` at 45 % alpha is the only visual. | See Toast row. | Haptic rejection tick + animated toast. |
| **Out-of-stock chip** | **4.5** | Honest, legible, correct (dims the image, never the name or price). Only gap is that it is inert — no affordance to be notified. | Notify-me needs a backend. | `[DEP]` Backend. Leave as-is; do not fake a waitlist. |
| **Hero banner "Fast delivery" pill** | **2.5** | **A fake CTA.** It wears the exact filled-dark-green pill silhouette of the primary button, with a bolt icon — but it is not interactive. The *whole banner* is clickable and navigates to **Categories**, with `indication = null` so there is **no press feedback at all**. A customer taps a delivery-looking button, gets no acknowledgement, and lands on a category list. `[CODE]` `HomeTabContent.kt:415-419` `[VISUAL]` `final-gate/ios/frame_03.png` | Label styled as a control; banner tap feedback explicitly suppressed. | Restyle the pill as a badge (not a button silhouette), restore press feedback on the banner, and make the destination match the promise. |

### 3.2 Cards & surfaces

| Component | Before | Issue | Root cause | Planned change |
|---|---|---|---|---|
| **`ProductCard`** | **4.0** | Scanning order is correct (image → name → unit → price → action) and the two-line name block correctly reserves height so the grid aligns. But the whole card uses `indication = null` — **tapping a product gives no feedback whatsoever** before the screen cross-dissolves. `[CODE]` `Components.kt:578-581` | Ripple suppressed to avoid a rectangular ripple over rounded corners. | Restore feedback as a clipped press-scale + subtle surface shift, not a Material ripple. |
| **Category tiles** | **3.5** `[DEP]` | Photography is inconsistent in art direction: market-stall wide shot, single glass on grey, bowl of flour, **supermarket aisle shelf**, raw meat on ice. Different lighting, crops, subject distance and register. Reads as stock photography, not a curated set. The "Chicken, Meat & Fish" tile shows **raw fish**, mismatched to its label. `[VISUAL]` `frame_03.png` | Wikimedia placeholders, acknowledged as temporary. | `[DEP]` Photography commissioning. Cannot reach 5 without real assets. Interim: normalise crop/aspect and fix the fish/chicken mismatch. |
| **Hero basket banner** | **3.0** `[DEP]` | Carries "SAVE 8–20%" (**D6 unsubstantiated**) and a photograph containing **third-party trade dress** — Aashirvaad, Daawat, Maggi, Tata Salt, Colgate, Fortune packaging — in Tazzzo's own marketing banner. That is a brand and licensing exposure, not only a design one. `[VISUAL]` `frame_03.png` | Placeholder asset. | `[DEP]` D6 + photography. Flagged for your decision; I will not alter a founder claim. |
| **Cart bar** | **4.5** | Strong hierarchy, honest incentive copy ("Add ₹50 more for free delivery"). Gap: appears/disappears without transition. | No enter/exit animation. | Slide+fade on appear; it is the app's most frequent state change. |
| **Bottom nav** | **4.0** | Selected pill + tint + weight is clear. Two gaps: **(a)** the pill appears instantly with no transition; **(b)** **no `selected` semantics** — screen readers announce the label but never that it is the current tab. Icon family mixes filled (house, person) with outline/geometric (3×3 dot grid, circular arrow) — inconsistent weight. `[CODE]` `MainScaffold.kt:125-175` `[VISUAL]` `tb0_home.png` | `contentDescription` only; no `selected` role. | Add `selected` semantics, animate the pill, unify icon weight. |
| **Toast (`transientMessage`)** | **3.0** | Hard-cuts in and out with **no animation**. Positioned with a magic `120.dp` bottom offset that is outside the token scale and does not adapt to whether the cart bar is present. **Not announced to screen readers** (no live region). No manual dismiss. `[CODE]` `MainScaffold.kt:84-110` | Built as a minimum-viable notice. | Animate, tokenise the offset, anchor it to the cart bar, add `liveRegion` semantics. |

### 3.3 Loading, error, empty

| Component | Before | Issue | Planned change |
|---|---|---|---|
| **Home skeleton** | **3.5** | **Does not match the final layout.** Skeleton shows a generic header pill, one row of four squares and large blocks; the real Home has a section header + "See all", a **4×2** photo grid with two-line labels, and a pager with dots. Content jumps on arrival. `[VISUAL]` `tb0_home.png` vs `frame_03.png` | Rebuild the skeleton to mirror the real layout box-for-box. |
| **Shell-first loading** | **5.0** | Header (logo, coins, mic, location, search) renders fully while the body is skeleton. This is correct and better than most competitors. | None. Keep. |
| **`ErrorState`** | **4.5** | Classified, non-technical, retryable, kind-specific copy and icon. Strong. Gap: no motion on appearance. | Subtle entrance. |
| **`EmptyState`** | **4.5** | Well-written and recovery-oriented. Gap: no motion; illustration is an emoji glyph. | Entrance motion; icon treatment. |
| **`OfflineBanner`** | **N/A** `[DEP]` | Built, **never wired**. Three blockers already documented in `States.kt` and `BLOCKERS.md`: no connectivity source exists, placement is a design decision, and whether a global banner is wanted at all is undecided. | `[DEP]` Your decision + platform work. |

### 3.4 Interaction substrate

| Element | Before | Issue |
|---|---|---|
| **Pressed state** | **2.0** | See **S1**. Zero designed press states app-wide. |
| **Focus state** | **3.0** | Only the OTP/search fields get platform focus; no explicit focus styling, no keyboard focus order verification. `[CODE]` |
| **Selected state** | **4.0** | Present and clear on nav, filters, slots, payment — but never animated. |
| **Disabled state** | **4.5** | Genuinely good: `PillButton` carries real `disabled()` semantics plus a `stateDescription` explaining *why*. Above industry norm. |
| **Haptics** | **0.0** | See **S4**. None. |
| **Motion language** | **3.0** | Tokens exist (`fast 150 / normal 300 / ambient 700`) and are respected where used — but there are only 20 animation call sites in 11,767 lines, and navigation uses a single undirected Crossfade. |
| **Scroll restoration** | **1.5** | See **S3**. Nothing is preserved. |
| **Keyboard avoidance** | **3.0** | `imePadding` used in only **3** places against multiple input surfaces (search, help search, OTP, address form). `[CODE]` |
| **Safe areas** | **4.0** | `statusBarsPadding` (15) and `navigationBarsPadding` (21) used consistently; no `WindowInsets`/`safeDrawing` usage, which is acceptable but less robust on unusual cutouts. |

---

## 4. SCREEN AUDIT

| Screen | Before | Principal gap |
|---|---|---|
| System splash → app splash | **4.0** `[RUN]` | Continuity between the OS splash and the Compose splash is unverified for flicker/blank-frame; cold vs warm vs repeat launch not measured. |
| Splash | **4.5** | Choreography is genuinely good and brand-led. Exit is a Crossfade like everything else — no dedicated hand-off to Home. |
| Login / guest | **4.0** | Composition is strong. Gaps: no OTP paste/auto-fill handling, no per-digit transition, CTA has no loading state (S1), Skip is visually secondary but not *deliberate*. |
| Onboarding tour | **4.5** | Spotlight with `BlendMode.Clear` cutout is well built. No motion on step change. |
| Home | **4.0** | Skeleton mismatch, fake CTA in banner, no press feedback on cards or banner, no pull-to-refresh. `[DEP]` order-again/offers need real data. |
| Categories | **4.5** | Solid. Tap feedback missing. |
| Category listing | **4.0** | Two-pane is good; **scroll position lost on back from PDP** (S3). Sidebar selection not animated. |
| Search | **3.5** | Debounce + non-blocking progress bar is a genuinely good decision. But query/filters/results are lost entirely on back (S3), and there is no result-arrival transition. |
| PDP | **4.0** | Pinned purchase footer is correct. Image is an emoji glyph `[DEP]`. No shared-element continuity from the card that opened it. |
| Cart | **4.5** | Bill breakdown and savings strip are excellent and honest. Row removal is unanimated. |
| Checkout | **4.5** | The strongest screen in the app — 3-state indicator, "To pay" on every step, disabled CTAs that state *why*, honest failure card. Gaps: step changes use the same undirected Crossfade; Place Order has no in-button loading state (S1). |
| Order confirmation | **4.0** | Content is right (amount, items, paid-by, coins). No arrival moment — a successful order is the app's peak emotional beat and it simply appears. |
| Orders | **4.5** | Status rail is clear. Now has proper error/retry (Wave 2). |
| Order Again | **4.0** | `[DEP]` needs real order history to be meaningful. |
| Account | **4.5** | Clean. |
| Addresses | **4.5** | Serviceability chip is honest. |
| Coins | **4.0** | `[DEP]` D5. Redemption correctly hidden. Cannot reach 5 until economics are approved. |
| Help / FAQ | **4.5** | Now repository-backed with real error/retry (Wave 1–2). Expansion is unanimated. |
| About | **4.5** | `[DEP]` D6 claims. |
| Voice sheet | **4.0** | `[DEP]` Honest "coming soon", dismiss-only. Cannot exceed this without a real feature. |

---

## 5. WHAT CANNOT REACH 5/5 BY MY HAND

Stated plainly, per your instruction to mark dependencies rather than pretend:

| Dependency | Blocks | Owner |
|---|---|---|
| **Real product photography + CDN** | Every product card, PDP hero, cart row, order row | You (commissioning) |
| **Category tile photography** | Home, Categories | You (commissioning) |
| **Third-party trade dress in hero banner** | Home hero | You (legal/brand) |
| **D4 delivery promise** | Home header, PDP, cart, ETA chips | You |
| **D5 coin economics** | Coins screen, cart coin line, confirmation | You |
| **D6 marketing claims** | Home hero, About, splash, login | You |
| **Physical device QA** | Scroll feel, thermal, true cold start, real haptics | Requires hardware I do not have |
| **Human TalkBack / VoiceOver traversal** | Accessibility sign-off | Requires a human session |
| **Backend** | Real offers, order-again, pull-to-refresh honesty, live status | Backend |

---

## 6. FIX PLAN

Ordered by impact per unit of risk. Each lands behind its own verification.

| Wave | Scope |
|---|---|
| **E1 — Feedback substrate** | Press states app-wide (S1), haptics (S4), `PillButton` loading/success, 44 dp stepper targets, toast motion + live region, nav `selected` semantics. **Highest impact.** |
| **E2 — Navigation & continuity** | Directional transitions replacing the undirected Crossfade (S2), `rememberSaveable` for scroll/query/filter state (S3), per-tab state retention. |
| **E3 — Motion language** | Formalise motion tokens into named roles (micro/nav/sheet/success/error/content), apply consistently, remove competing simultaneous animations. |
| **E4 — Loading fidelity** | Rebuild skeletons to match final layouts box-for-box; entrance transitions for error/empty; cart-bar transition. |
| **E5 — Screen-level polish** | Order-success arrival moment, checkout step direction, search result transition, OTP paste/auto-fill, sidebar and filter selection motion. |
| **E6 — Accessibility & type** | Dynamic type pass, focus order, semantics sweep, `imePadding` completeness. |
| **E7 — Verification** | Both platforms, three screen sizes (480×854, standard, large), fresh screenshot evidence, scores re-rated honestly. |

---

## 7. THE 5/5 GATE

This document is not closed until every row above either:

1. scores 5/5 with `[RUN]` verification on **both** Android and iOS, or
2. carries an explicit `[DEP]` naming what blocks it and who owns it.

**No row may be marked complete on the basis of "it compiles" or "tests pass."**

**Current state: 0 of 7 fix waves executed. No score in this document has moved yet.**

---

## 8. LOG

- **2026-09-01** — Audit opened. Freeze reopened by design owner; target raised to
  5/5. Four structural findings (S1–S4) established by source verification.
  Component and screen inventories scored against rendered evidence. No fixes
  applied yet; every score above is a *before* score.
