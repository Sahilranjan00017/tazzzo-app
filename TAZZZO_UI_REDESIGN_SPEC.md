# Tazzzo UI redesign — build contract

Source: six HTML screen mockups supplied by the founder on 2026-09-06 (Account,
Help & Support, Home, Categories, Checkout, Cart).

Instruction, verbatim: *"do these change but not change the logo and the voice
feature apart from that do this."*

So: implement all six, leave the Tazzzo logo and the voice feature exactly as
they are.

---

## 0. Three things the mockups cannot supply, and what happens instead

The mockups are visual comps. Three of their ingredients are not available to a
real build, and inventing them would break rules already agreed on this project.

**1. Every product photograph in the mockups is AI-generated.** Each `<img>`
carries a `data-alt` that is a text-to-image prompt, and the `src` points at
generated assets. The standing rule is: no fabricated or AI-generated product
photography, no random internet images, no third-party brand trade dress.
→ **Layouts are built with the image slots exactly as drawn.** They render the
verified openly-licensed photography already in the app, and the existing
tinted-well fallback everywhere else. Marked `[ASSET REQUIRED — PRODUCTION
PHOTOGRAPHY]`.

**2. Several numbers in the mockups are invented.** Wallet balance ₹140, "5%
cashback active", "₹85 processed to UPI ID", "12 Items" wishlist, "2 Active"
orders, "340+" per category, "1,840 Items", "5,000+ groceries", "150+ sold
recently", app version 2.4.1 build 4120.
→ **Every count is derived from real state or the element is omitted.** Category
counts, cart counts, savings and item totals are all computable today, so they
are computed. Where no data exists, the chip does not render rather than
render a number nobody can verify.

**3. A live countdown timer on Flash Steal Deals.** Manufactured urgency against
a deadline that does not exist.
→ **Omitted.** The deals block ships without it. If a campaign gains a real end
time from the backend, the component takes it then.

---

## 1. Design system

### Adopted from the mockups
| Token | Value |
|---|---|
| `display-lg` | 32 / 38, -0.03em, 800 |
| `headline-lg` | 24 / 30, -0.02em, 700 |
| `headline-md` | 20 / 26, -0.02em, 700 |
| `headline-sm` | 16 / 22, -0.01em, 700 |
| `body-lg` | 15 / 22, 400 |
| `body-md` | 13 / 18, 400 |
| `body-sm` | 11 / 15, 500 |
| `label-caps` | 10 / 12, +0.04em, 700, uppercase |
| `price-hero` | 15 / 18, -0.01em, 800 |
| `price-mrp` | 11 / 14, 400, struck |
| `stepper-count` | 14 / 18, 700 |
| spacing | xxs 2 · xs 4 · sm 8 · md 12 · base 16 · lg 20 · xl 24 · screen-edge 12 · grid-gutter 8 |
| radii | 4 · lg 8 · xl 12 · pill |
| surface ramp | `container-lowest` (cards) → `container-low` (wells) → `container` (dividers) → `container-high` (tiles) |

### NOT adopted: the colour values
The mockups ship a stock Material 3 scheme — primary `#005e3f`, surface
`#f8f9ff` (cool blue-white), ink `#0b1c30` (navy). Tazzzo's ramp is warm: green
`#00411C`, cream `#FAF9F6`, ink `#16190F`.

Keeping Tazzzo's, for three reasons:
1. **The logo is staying**, and it is drawn in `#00411C`. Swapping the app to a
   cool blue-white ground around a warm green mark is exactly the mismatch the
   "don't change the logo" instruction is guarding against.
2. **Tazzzo's ramp is contrast-audited**, with measured ratios recorded per
   colour (orange moved from `#F04E1E` to `#C74018` to clear 4.5:1; tertiary ink
   from `#8A9083` to `#73786E`). The mockup palette carries no such audit.
3. Orange stays reserved for savings, which the mockups also honour.

The **structure** of the mockup ramp is adopted in full — the four surface
levels are what give these screens their depth, and Tazzzo had only two.

→ If the founder wants the literal palette, it is one file: `TazColors`. Say so
and it flips.

---

## 2. Per-screen scope

### Home
Build: rotating search placeholder · 24×7 chip · festival hero with eyebrow,
validity and CTA · 4×2 category icon grid · price-point deals block (bands
derived from real prices, e.g. "Under ₹29") · Buy Again carousel · "Fresh &
Daily Essentials" 2-column grid with a real item count · floating cart bar with
count, total and real savings.
Omit: countdown timer, per-item delivery minutes (**D4**), "Up to 60% OFF"
unless derived.

### Categories
Build: search field + filter entry · scrollable group pills that actually filter
· promotion spotlight driven by `PromotionConfig` (real coupon, real minimum) ·
per-group sections with derived counts and See All · 4-column tile grid with
derived per-tile counts.

### Cart
Build: sticky expandable savings capsule (exists) · **free-delivery milestone
bar with real nodes** · item rows with stepper pill · "Add for less" upsell rail
(derived) · delivery-instruction chips · carry-bag preference · bill details ·
sticky CTA with total and savings.
Omit: "Eco Karma points" (a new loyalty currency — **D5**), ETA (**D4**).

### Checkout
Build: savings banner · address quick-bar · slot selector (exists) · coupon row ·
**tip selector** `[BUSINESS DECISION]` · instruction chips · payment list ·
bill · sticky pay bar.
Do NOT build: **the raw CVV input**. Card data must be entered inside the
gateway's own checkout, never in our view — collecting a CVV in app-owned UI is
a PCI violation and there is no gateway behind it. UPI providers render as
honest `Coming soon` until a gateway exists.

### Account
Build: profile card with edit · three quick-action tiles with real counts ·
balance card mapped to **Tazzzo Coins** (the currency that exists) · grouped
rows: Information / Preferences & Perks / Other · notifications toggle ·
language row · log out · footer.
Do NOT build as drawn: a **rupee wallet** ("Tazzzo Wallet", "Add Balance",
"5% cashback") — stored value is a regulated PPI product in India, not a UI
feature. **Refunds** and **E-Gift Cards** have no data or backend; they render
only when real data exists.

### Help & Support
Build: top bar with back and history · **active-order context card** with status,
time, total and item thumbnails (real order data) · issue tree of four routes ·
"Still stuck?" pair · FAQ row.
Omit: the "instant refund within 2 minutes" promise (**D6** — a marketing claim
about a service level nobody has committed to).

---

## 3. Untouched, by instruction
- The Tazzzo logo asset and its every placement.
- The voice feature: `MicButton`, `VoiceSheet`, `VoiceComingSoonSheet` and the
  mic in the search bar.
