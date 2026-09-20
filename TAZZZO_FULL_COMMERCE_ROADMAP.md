# Tazzzo — full commerce build

Scope agreed 2026-09-06: take Tazzzo to the feature depth of a full ecommerce
app, using noon.com and Blinkit as the benchmark, and make every feature
actually work rather than appear.

Everything below was observed first-hand on the live sites (noon.com UAE mobile
web, blinkit.com with a Bengaluru address) on 2026-09-06, not recalled. No
branding, artwork, copy, layout or proprietary asset is copied.

---

## 0. Two honest limits, stated before the plan

**1. noon is a marketplace; Tazzzo is a dark store.** noon sells millions of
SKUs from many sellers across fashion, electronics and furniture, with seller
ratings, installments, a co-branded credit card and 3-day delivery. Tazzzo
sells a curated local basket in minutes from one store. Copying noon *whole*
would replace Tazzzo's business, not improve it. So this plan takes noon's
**commerce mechanics** — depth of search, deals as a destination, variants,
wishlist, order lifecycle, account — and leaves its **marketplace machinery**
out. Where a noon feature is deliberately excluded it says so and why.

**2. Several noon features cannot be built honestly yet.** Ratings, review
counts, "150+ sold recently", "#4 in Immunity Boosters" and "Lowest price in 30
days" are all claims about real customer behaviour. Tazzzo has no such data.
The standing rule is that none of these may be fabricated. So each is built as
a **capability that renders only when data exists** and stays invisible until
the backend supplies it — the pattern `RatingRow` already uses. They are listed
in wave N8 and marked `[BACKEND REQUIRED]`.

---

## 1. What the benchmarks actually do

### noon.com (mobile web, UAE, 2026-09-06)
| Surface | What it carries |
|---|---|
| Bottom nav | Home · Categories · **Deals** · My Account · Cart — deals is a first-class destination, not a filter |
| Home | Category tile grid, then named rails: Best picks for you, Bestsellers for you, In the spotlight, Previously browsed products, Offers for you, Lowest prices on top brands |
| Campaign band | A dated, self-contained block with its own background — "Grocery Saver week \| 1–7 September" — with products inside it and View All |
| Offer card | Product composite, a promo-code chip on the image, a benefit headline ("15% Cashback"), a qualifier ("up to AED 75") |
| Listing card | rating + count · price · was-price · % off · **price per piece/100 g** · category rank · "Only 5 left in stock" · "150+ sold recently" · "Lowest price in 7 days" · Free Delivery · GET IN 56 MINS · "Extra 10% off" · variants ("+ 1") · Mega Deal badge |
| Facets | Brand, Deals, Age Group — per-category, not global |
| PDP | 6-image carousel · variants · "Inclusive of all taxes" · Top products in this category · People also bought |
| Elsewhere | Wishlist, e-gift cards, print store, app-exclusive offers |

### Blinkit (web, Bengaluru address, 2026-09-06)
| Surface | What it carries |
|---|---|
| Home | Three campaign banners and an 18-tile category grid. **No product rails at all** — the depth is in the category pages |
| Listing card | % off · **per-item ETA on every card** · name · pack · price · was-price · ADD · "2 options" |
| Catalogue | ~30,000 SKUs, 26 categories, ~300 sub-categories; 30 items in the first produce screen against Tazzzo's 7 |
| Localisation | Every produce item carries the local name — Onion (Eerulli), Banana (Baale Hannu), Cucumber (Southekayi) |
| Pack sizes | 50 g coriander, 100 g chilli, 250 g carrot — quick-commerce baskets |
| Festivals | **Not a banner.** A permanent Pooja Needs aisle, Party & Festive Needs, Decorative Lights, Flowers & Leaves, and gift packs as a sub-category inside seven food categories |

---

## 2. Waves

Each wave ships working, tested and verified on device. None is "wired later".

| # | Wave | Delivers | Status |
|---|---|---|---|
| **N0** | Taxonomy alignment | Pooja & Religious Needs aisle mirroring TZV-000225..233 · `Product.verticalId` · per-unit price · local names | **done** |
| **N1** | Search and browse depth | Sort, brand and in-stock facets, recent and popular searches, empty and no-result states were **already built**. Added this wave: a **Deals facet** meaning MRP genuinely above price, with state that survives navigation and restores from bundles written by older builds | **done** |
| **N2** | Deals as a destination | Fifth nav tab · dated campaign band · offers listed with the condition that gates them · members-only offers hidden from non-members · savings grid ranked by real rupees off · `getDeals()` on the catalogue contract | **done** |
| **N3** | Variants | `variantGroupId` · "N options" on the card · variant selector on PDP and in the cart | next |
| **N4** | Wishlist | Heart on card and PDP · Saved list in Account · persisted across launches | |
| **N5** | PDP depth | Image carousel · attribute-driven detail table · tax line · "Top in this category" and "People also bought" derived from real catalogue and order data | |
| **N6** | Order lifecycle | Status timeline · cancel · return and refund request · invoice | partly `[BACKEND REQUIRED]` |
| **N7** | Account depth | Address book CRUD · payment methods · notification preferences · help centre · e-gift cards | |
| **N8** | Trust facts | Ratings, reviews, sold-recently, price history, category rank — capability only, hidden until real data | `[BACKEND REQUIRED]` |

### Deliberately excluded, with reasons
- **Marketplace sellers, seller ratings, seller storefronts** — Tazzzo is one store.
- **Installments and a co-branded credit card** — regulated credit, not a mobile-app feature.
- **Fashion, furniture, electronics depth** — a different fulfilment model and returns policy.
- **Per-item delivery minutes** — the component is buildable today, but the delivery promise itself is unresolved founder decision **D4**. Building it would ship a promise nobody has approved. Gated behind that decision.
- **Fabricated ratings, reviews, sold counts and price history** — see §0.2.

---

## 3. What blocks the finish line

These are recorded in full in `BLOCKERS.md`, verified 2026-09-06 against the
master CSV, the seed JSON and the service's own `openapi.json`.

1. **No browse, list or search endpoint exists.** `GET /api/v1/products`
   requires `canonicalKey` and returns one product; there is no node-children
   call. Waves N1, N2 and N5 work perfectly against `MockCatalog` and have
   nothing to bind to at integration. This is a contract change and it is not
   scoped.
2. **The collections plane is not built.** Campaigns, deals rows and curated
   rails have no backend home, so N2 stays local-only.
3. **Fresh produce, dairy and pet care are excluded from taxonomy v0.9.0** by
   recorded decision, yet they are the app's entire spine. Needs a founder
   ruling before produce depth resumes.
4. **Real product photography.** 33 of 63 SKUs now carry verified openly-licensed
   photographs; packaged branded goods are poorly covered by open sources and
   keep the honest no-photo state. Production photography replaces the set
   through one table.

---

## 4. Corrections to this plan, found by building it

- **N1 was largely already built.** Sort, brand and in-stock facets, recent
  searches and empty states existed before this wave. Only the Deals facet was
  missing. Recorded so the plan is not credited with work it did not do.
- **A price-band facet was dropped, not deferred silently.** Fixed bands
  ("Under ₹100") produce empty results on a 72-SKU catalogue, and bands derived
  from the visible list change meaning as filters change. It needs a design
  decision, not a quick chip.
- **Per-item delivery minutes stays gated on D4.** The component is trivial;
  the promise is not mine to make.
