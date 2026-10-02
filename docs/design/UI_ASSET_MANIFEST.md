# Tazzzo UI asset manifest — photography

**Status:** every slot below is OPEN. The screens are built with their photo containers, crops, gradients and
content-safe areas in place; each one is filled by a single resource swap when the clean plate arrives.
Until then the container renders a named development placeholder (a brand-tinted gradient), never a cropped
reference screenshot.

**Why the reference screenshots cannot be shipped:** `UI Page/*.jpeg` are 588 px wide, JPEG, with the status bar,
headlines, buttons, pagination and navigation baked into the pixels. No pixel from them is used in the app.

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
| TZ-ASSET-SPLASH-001 | Splash | Full-bleed background | Portrait 9:19.5, `ContentScale.Crop`, vertically centred | 1290 × 2796 | Bright, airy, near-white studio ground with soft-focus floating produce around the edges. The **centre 60% × 30%** (where the wordmark and tagline sit) must stay clear and light. A tote/bag of greens may rise from the bottom-right but must not pass the lower third. | No | WebP |
| TZ-ASSET-SHOWCASE-001 | Showcase 1 — "Wholesale Prices, Delivered." | Full-bleed background | Portrait 9:19.5, crop anchored to the **bottom** | 1290 × 2796 | Deep forest-green wall (≈ #143528 family). Pantry staples (bananas, milk bottle, eggs, tomatoes, paper bag, rice sack) occupying the **lower 45%**. The **upper 55%** is plain wall: headline, support line and dots sit there and need ≥ 4.5:1 contrast for cream text. | No | WebP |
| TZ-ASSET-SHOWCASE-002 | Showcase 2 — "Farm Fresh, Everyday." | Full-bleed background | Same as above | 1290 × 2796 | Deep green wall with a wooden shelf top-right; crate of tomatoes, lettuce, tote bag of greens in the **lower 45%**. Upper 55% clear. | No | WebP |
| TZ-ASSET-SHOWCASE-003 | Showcase 3 — "Kitchen Essentials, Simplified." | Full-bleed background | Same as above | 1290 × 2796 | Deep green kitchen wall, shelves top-right, counter with bowl, avocado, utensils in the **lower 45%**. Upper 55% clear. | No | WebP |
| TZ-ASSET-LOGIN-001 | Login (phone) | Full-bleed background | Portrait 9:19.5, crop anchored to the **bottom** | 1290 × 2796 | Warm cream plaster wall with soft leaf shadows; jars, lentils, a kraft bag on a stone counter in the **lower 40%**. **Upper 60% must stay plain cream** (headline, input and Continue sit there). The reference prints "Best Value. Smart Shopping." on the bag — optional, but then it must be the exact canonical tagline. | No | WebP |
| TZ-ASSET-OTP-001 | OTP | Full-bleed background | Same as LOGIN-001 | 1290 × 2796 | Cream wall; flour sack, jars, scoop on the counter in the **lower 40%**. Upper 60% plain. May be the same shoot as LOGIN-001 with a different crop. | No | WebP |
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
legibility scrim and content-safe area. The placeholder is code, not an image file, so nothing generic is
committed as an asset. Replacing a slot = adding the WebP to `composeApp/src/commonMain/composeResources/drawable/`
and passing its `Res.drawable.*` id.
