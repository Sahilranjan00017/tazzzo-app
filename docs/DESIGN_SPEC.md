# Tazzzo Visual & UX Specification

> **STATUS: FROZEN — 2026-08-31.** The UI/UX Final Gate passed and the design
> system and customer-facing screens are frozen. Change them only to fix a real
> defect, never for variation's sake. Backend integration must fit this UI; if
> integration appears to require a redesign, raise it as a contract problem
> instead of redrawing screens.

The single source of truth for how Tazzzo looks and behaves. Screens compose
from these tokens and components — no screen styles itself independently.

## 1. Brand personality (extracted from the logo, not invented)

The wordmark is a **geometric sans with rounded terminals**: TA in deep forest
green, ZZZ in vivid orange, a leaf on the O, a cart-swoosh underline.
It reads **fresh, fast, friendly, Indian, trustworthy** — confident but not
corporate, warm but not childish.

The UI inherits that skeleton: geometric type, generous radii, rounded but
purposeful shapes, and colour used sparingly enough that **food and prices are
the loudest things on screen**.

## 2. Colour

| Token | Hex | Only used for |
|---|---|---|
| `Green` | `#00411C` | Identity, primary actions, selected state |
| `GreenDark` | `#0A2B16` | Dark surfaces (hero strips, voice) |
| `GreenMid` | `#1B6B3A` | Gradients, secondary brand |
| `GreenSoft` | `#E8F1EA` | Tinted/selected backgrounds |
| `Orange` | `#F04E1E` | **Savings, offers, urgency — never decoration** |
| `OrangeSoft` | `#FFF1EA` | Offer chip backgrounds |
| `Cream` | `#FAF9F6` | Page background |
| `Surface` | `#FFFFFF` | Cards |
| `SurfaceSunken` | `#F1EFE9` | Image wells, inset groups |
| `CardBorder` | `#EBE8E0` | Hairlines |
| `BorderStrong` | `#D8D4C9` | Inputs, dividers that must read |
| `TextPrimary` | `#16190F` | Body/headings |
| `TextSecondary` | `#5B6157` | Supporting |
| `TextTertiary` | `#8A9083` | Metadata |
| `TextDisabled` | `#B2B7AA` | Disabled |
| `Success` / `Warning` / `Danger` | `#15803D` / `#B45309` / `#B3261E` | Semantic only |
| `CoinGold` | `#E8A200` | Tazzzo Coins |

**Rule:** the brand green is an accent, not a wash. If a screen is mostly
green, the hierarchy has failed. Orange appears only where money is saved.

**Contrast — measured, not estimated** (audit script run 2026-08-30; an earlier
version of this document claimed orange passed at 4.6:1, which was wrong — it
measured 3.61:1 and was corrected):

| Pair | Ratio | AA |
|---|---|---|
| TextPrimary on Cream / Surface | 16.9 / 17.8 | pass |
| TextSecondary on Cream / Surface | 6.06 / 6.38 | pass |
| TextTertiary on Surface | 4.53 | pass *(was 3.28 — darkened)* |
| Green on Cream / Surface | 11.25 / 11.85 | pass |
| Orange on Surface / Cream / OrangeSoft | 5.03 / 4.78 / 4.56 | pass *(was 3.61 — darkened)* |
| White on Orange (discount badge) | 5.03 | pass |
| White on Green / GreenDark | 11.85 / 15.33 | pass |
| Success on Surface / SuccessSoft | 5.15 / 4.58 | pass |
| Warning on WarningSoft | 4.58 | pass |
| Danger on Surface / DangerSoft | 6.54 / 5.67 | pass |
| CoinInk on Surface | 4.50 | pass *(bright CoinGold was 2.19 on light — split into two tokens)* |
| CoinGold on GreenDark | 7.01 | pass |

`CoinGold` is for DARK grounds only; `CoinInk` is its light-surface counterpart.
Disabled text (`TextDisabled`, 2.05:1) is intentionally low and is exempt under
WCAG 1.4.3, which excludes inactive components.

## 3. Typography — **Poppins** (final decision)

Chosen for functional reasons, not looks:
1. Geometric sans with rounded terminals — the same skeleton the wordmark is
   drawn in, so UI and logo belong to one family.
2. Ships a full **Devanagari** cut (Indian Type Foundry) — Hindi copy will
   render in the same family instead of falling back to a mismatched face.

Cost: ~630 KB for four weights (Regular/Medium/SemiBold/Bold). Accepted.
Applied once in `TazzzoTheme` via Material typography **and** `LocalTextStyle`,
so even bare `Text(fontSize = …)` calls inherit it.

| Role | Size / line | Weight |
|---|---|---|
| Display | 30 / 36 | ExtraBold |
| H1 | 24 / 30 | ExtraBold |
| H2 | 19 / 24 | Bold |
| Title | 16 / 21 | SemiBold |
| Body | 14 / 20 | Regular |
| Caption | 12 / 16 | Regular |
| Micro | 11 / 13 | SemiBold (floor — nothing smaller ships) |
| **Price (hero)** | 22 | Bold |
| **Price (card/row)** | 15 | Bold |
| MRP (struck) | 12 | Regular |
| Savings | 12 | SemiBold |
| Product name | 13 / 17 | Medium |
| Unit | 12 | Regular |
| Button | 15 | SemiBold |
| Nav label | 11 | Medium |

## 4. Space, radius, elevation, motion

- **Spacing** — 4dp base: 2 · 4 · 8 · 12 · 16 · 20 · 24 · 32 · 48. Gutter 16.
- **Radius** — chip 8 · card 14 · tile 18 · sheet 22 · pill 999.
- **Elevation** — flat 0 · raised 3 (cards) · floating 8 (bars/banners) ·
  overlay 14 (sheets). Shadows are soft and warm-black, never hard.
- **Motion** — fast 150ms (taps/state), normal 300ms (transitions),
  ambient 700ms (pulses). Nothing decorative; motion explains change only.
- **Touch** — 44dp minimum, 48dp for text links in the thumb zone.

## 5. Iconography — one family

`TazIcons` maps every UI control to a single Material vector family.
**Emoji are content** (a product's 🍅, a category tile) — never UI. Vectors
tint with the theme, scale with user font size, and carry semantics.

## 6. Components (in `ui/common`)

`PillButton(enabled, disabledHint)` · `ProductCard` · `QuantityStepper` ·
`CategoryTile` · `ProductRail` · `CartBar` · `TazTopBar` · `FilterBar` /
`SortSheet` · `StateHost` + `EmptyState` / `ErrorState` / skeletons ·
`CoinChip` · `MicButton` · banners.

## 7. Product imagery — architecture and current status

`ui/common/ProductImage.kt` is the ONE way a product image renders anywhere
(card, PDP hero, and any future cart/order surface).

- **Fixed-aspect container** — an image never dictates layout height, so a
  portrait bottle and a landscape pack produce identically sized cards.
- **`ContentScale.Fit`, never `Crop`** — cropping a packshot cuts off the brand.
  Nothing is stretched; Fit preserves aspect ratio by definition.
- **One neutral ground** (`SurfaceSunken`) behind every image, so photos shot on
  different backgrounds still sit in a uniform grid.
- **Three explicit states**: loading (skeleton), ready, unavailable (fallback).
- **Backend seam**: `Product.imageUrl` + a `ProductImageLoader` CompositionLocal.
  The default returns `Unavailable` because there is no image backend or
  networking layer yet — inventing one would be fabrication. When the catalogue
  serves URLs, implement the loader ONCE and every product surface updates with
  no screen changes.

> **TEMPORARY IMAGERY.** Until the catalogue serves real packshots, products
> fall back to an emoji glyph. That glyph is a DEVELOPMENT PLACEHOLDER and must
> not be presented as production photography. Tracked as a P1 asset dependency
> in BLOCKERS.md.

## 8. States are design, not fallbacks

Every data surface ships **loading (skeleton shaped like the real content),
empty (explains + offers an action), error (says what happened, whether retry
helps), offline, disabled (says why)**. Generic "Something went wrong" is
banned.

## 9. Honest content (non-negotiable)

No fabricated ratings, delivery times, counts, offers, trends or SLAs. Every
number needs a source of truth. Claims awaiting substantiation live in
`BrandCopy` and are tracked as decisions (D4 delivery, D5 coins, D6 claims).
