# Tazzzo UI asset manifest — photography

**Status (2026-10-03):** the six UI-01 slots (Splash, Showcase ×3, Login, OTP) are **FILLED** with plates derived from
the supplied reference renders, per the Product direction that the supplied images are the visual source of truth.
The Home and PDP slots remain OPEN (UI-02 / UI-03). Every slot is still a single resource swap if a higher-quality
master is produced later.

**How the UI-01 plates were made** (`design/brand/tools/` has no copy of this; the pipeline lives with the job notes and
is described here so it can be repeated): the reference render → the baked UI (status bar, headline, support line,
pagination, pill button, Skip, input, legal line, bag lettering, back chevron, OTP cells, home indicator) is masked and
inpainted (OpenCV Telea, radius 7) → regions where a control covered detailed photography are softened into
out-of-focus foreground (they sit under the native CTA band) → upscaled ×3 → resized to 1290 px wide → WebP q82.
No text, control or system-bar pixel from the references survives in a shipped asset; all UI is native Compose.
Limitation: the sources are 588 px renders, so the plates are upscaled photography (soft at 1:1 on a 1440-wide
device); the compositions, colour and lighting are the reference's own.

## Delivery rules (all assets)

- Text-free, chrome-free plates. No headline, button, status bar or pagination in the image.
- Minimum width **1290 px** (3× of the 430 dp reference frame) unless stated otherwise; portrait assets at least
  1290 × 2796 px. Larger is fine; we downscale once and ship WebP.
- Preferred delivery: lossless PNG or TIFF master. We export the shipped **WebP** (quality 85–90) from it.
- Transparency only where the row says so (PNG master with alpha).
- Where a *background* and *foreground* layer are listed, deliver them separately so the crop can breathe on
  different screen heights without cutting through hero objects.
- Licence/provenance for each plate (stock licence id, commissioned shoot, or generated-and-owned).

## Slots

| Asset ID | Screen | Target slot | Aspect / crop | Minimum size | Composition requirements | Alpha | Ships as |
|---|---|---|---|---|---|---|---|
| TZ-ASSET-SPLASH-001 — **FILLED** `bg_splash_grocery.webp` | Splash | Full-bleed background | Portrait 9:19.5, `ContentScale.Crop`, vertically centred | 1290 × 2796 | Bright, airy, near-white studio ground with soft-focus floating produce around the edges. The **centre 60% × 30%** (where the wordmark and tagline sit) must stay clear and light. A tote/bag of greens may rise from the bottom-right but must not pass the lower third. | No | WebP |
| TZ-ASSET-SHOWCASE-001 — **FILLED** `bg_showcase_wholesale.webp` | Showcase 1 — "Wholesale Prices, Delivered." | Full-bleed background | Portrait 9:19.5, crop anchored to the **bottom** | 1290 × 2796 | Deep forest-green wall (≈ #143528 family). Pantry staples (bananas, milk bottle, eggs, tomatoes, paper bag, rice sack) occupying the **lower 45%**. The **upper 55%** is plain wall: headline, support line and dots sit there and need ≥ 4.5:1 contrast for cream text. | No | WebP |
| TZ-ASSET-SHOWCASE-002 — **FILLED** `bg_showcase_farm.webp` | Showcase 2 — "Farm Fresh, Everyday." | Full-bleed background | Same as above | 1290 × 2796 | Deep green wall with a wooden shelf top-right; crate of tomatoes, lettuce, tote bag of greens in the **lower 45%**. Upper 55% clear. | No | WebP |
| TZ-ASSET-SHOWCASE-003 — **FILLED** `bg_showcase_kitchen.webp` | Showcase 3 — "Kitchen Essentials, Simplified." | Full-bleed background | Same as above | 1290 × 2796 | Deep green kitchen wall, shelves top-right, counter with bowl, avocado, utensils in the **lower 45%**. Upper 55% clear. | No | WebP |
| TZ-ASSET-LOGIN-001 — **FILLED** `bg_auth_phone.webp` | Login (phone) | Full-bleed background | Portrait 9:19.5, crop anchored to the **bottom** | 1290 × 2796 | Warm cream plaster wall with soft leaf shadows; jars, lentils, a kraft bag on a stone counter in the **lower 40%**. **Upper 60% must stay plain cream** (headline, input and Continue sit there). The reference prints "Best Value. Smart Shopping." on the bag — optional, but then it must be the exact canonical tagline. | No | WebP |
| TZ-ASSET-OTP-001 — **FILLED** `bg_auth_otp.webp` | OTP | Full-bleed background | Same as LOGIN-001 | 1290 × 2796 | Cream wall; flour sack, jars, scoop on the counter in the **lower 40%**. Upper 60% plain. May be the same shoot as LOGIN-001 with a different crop. | No | WebP |
| TZ-ASSET-HOME-HERO-001 | Home — hero banner | Right-hand 55% of a 16:10 card | Landscape, crop anchored right | 1600 × 1000 | Tote bag with greens, olive oil, baguette, jars and tomatoes on a light wooden table against a cream wall. Left 45% stays plain for the copy. The bag may carry the **new** wordmark (we will supply the SVG) — never the old logo. | No | WebP |
| TZ-ASSET-HOME-QUALITY-001 | Home — "Quality you can count on." banner | Right-hand 50% of a 2.2:1 card | Landscape | 1600 × 730 | Rice sacks, jars, a bowl of grains on a pale sage ground (≈ #DEDBC8). Left half plain. | No | WebP |
| TZ-ASSET-HOME-BULK-001 | Home — "Stock up on everyday essentials." band | Right-hand 55% of a full-width band | Landscape | 1600 × 900 | Sacks and jars on a dark green ground (≈ #0E2F1E). Left half plain dark green. | **Yes** (cut-out preferred) | WebP (lossless) or PNG |
| TZ-ASSET-CAT-STAPLES-001 … CAT-SNACKS-001 | Home — category circles | 1:1 circle, object centred | 600 × 600 each | One hero object per category on a pale neutral ground (flour sack, tomatoes+carrots, chicken on a board, milk+curd, snack bowl). Object fills ~70% of the circle. Final category set follows the **live taxonomy names**, not the reference. | **Yes** | PNG → WebP (lossless) |
| TZ-ASSET-PDP-HERO-001 | PDP (Veg Page direction) | Top 42% of the screen, curved bottom edge | 4:3 landscape, crop anchored centre | 1600 × 1200 | Product photography comes from the **backend thumbnail/image URL**; this slot is only the neutral fallback plate (stone counter, soft shadow) used while an image loads or is absent. | No | WebP |

## Not requested (deliberately)

- Handwritten annotations ("Groceries that feel good.", "Pantry better. Live better.", "Farm fresh. Naturally better."):
  omitted. No script font is added (Decision Z1); if Product wants them later they become small SVG graphics.
- Any image that implies an unbacked claim (delivery timers, "picked today", discounts) — see Decision Z5.
- The old `tazzzo_logo.png` raster is superseded by `design/brand/tazzzo_wordmark.svg`.

## Development placeholders

`PhotoBackdrop(photo = null)` draws a brand-tinted gradient that preserves the slot's aspect ratio, crop anchor,
legibility scrim and content-safe area. It is used only by slots that are still OPEN (Home, PDP fallback); the six
UI-01 screens pass a real `Res.drawable.bg_*` and never draw the gradient. Replacing a slot = adding the WebP to
`composeApp/src/commonMain/composeResources/drawable/` and passing its `Res.drawable.*` id.
