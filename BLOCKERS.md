# Production blockers — single source of truth

> **Phase status (2026-08-31):** UI/UX Final Gate **PASSED**, UI **FROZEN**.
> Production readiness **NOT passed**. Next: backend integration, gated on the
> readiness report (`docs/BACKEND_INTEGRATION_READINESS.md`) and explicit GO.

Rule: never silently remove a blocker. Resolution requires a dated entry in
the PRODUCTION_READINESS.md verification log describing how it was verified.

## P0 — blocks any launch
- [x] Persistence — resolved 2026-08-30, verified by 8 on-device process-death
      tests (see PRODUCTION_READINESS.md log). Checkout session intentionally
      does not persist. NOTE: storage is plain NSUserDefaults/SharedPreferences;
      auth tokens (when real auth lands) must move to Keychain/Encrypted
      storage — tracked under Real backend.
- [ ] Real backend: auth, catalogue, stock, serviceability, orders, payments
      all mocked. Contracts: docs/BACKEND_CONTRACTS.md.
- [ ] Real payments: COD only; UPI/card disabled placeholders.
- [ ] D4 delivery promise unapproved — app ships neutral "Fast delivery" copy.
- [ ] D5 coin economics unapproved — current values are dev config only.

## P1 — blocks production quality
- [x] Android compile + critical journey — verified 2026-08-30 (see log).
      Progress 2026-08-30: keyboard behaviour (typed search + address form),
      process-death matrix (8 tests), small-screen 480x854 pass, back
      navigation — all verified. Remaining: PDP tap-test, scroll/fling feel
      review, screen-reader (TalkBack) pass, physical-device verification.
- [ ] Automated UI/journey tests (46 domain tests exist; no UI harness yet).
      COUNT CORRECTED 2026-09-01: this line read "30 domain tests" while the
      repository actually held 41 (XML-verified); it was stale, not wrong at
      the time it was written. 41 → 46 in the same session with the D-1
      regression suite. See the PRODUCTION_READINESS.md log entry.
- [ ] Accessibility: atoms done; CONTRAST AUDIT DONE 2026-08-30 (4 token fixes,
      measured table in DESIGN_SPEC.md); screen-reader human pass + dynamic
      type still open.
- [ ] Analytics: vendor-neutral boundary + 13 funnel events wired 2026-08-30;
      vendor adapter + crash reporting still open (vendor unselected).
- [ ] Production photography licensing (category tiles are Wikimedia
      placeholders; attribution in docs/IMAGE_ATTRIBUTIONS.md).

## P1 — visual ceiling (new, Phase 6)
- [ ] **Real product photography (ASSET DEPENDENCY, not an engineering gap).**
      The image ARCHITECTURE is complete and verified: fixed-aspect container,
      Fit-never-Crop, uniform neutral ground, loading skeleton, graceful
      unavailable state, and a `ProductImageLoader` seam driven by
      `Product.imageUrl` — real packshots drop in with NO screen redesign.
      What is missing is the photography itself (~63 SKUs) plus an image CDN
      and a Compose image-loading library. Until then products render an emoji
      placeholder, explicitly documented as temporary in docs/DESIGN_SPEC.md.
- [x] Re-verify search / orders / account / coins / help / about on device —
      DONE 2026-08-30 (docs/screenshots/qa-gate/).
- [x] Addresses + Voice sheet — verified on Android 2026-08-30 (final-gate);
      voice sheet's surviving "Cart ready in seconds" claim removed on sight.
- [ ] **Pre-launch verification gates** (do NOT reopen UI design for these):
      · Human TalkBack + VoiceOver session — service-on smoke test and full tree
        audit done; gesture traversal via adb blocked by the service's own dialog.
      · Dynamic type / font-scaling pass.
      · Physical Android device + physical iPhone QA (emulator/simulator only so far).

## P2 — polish (expanded by the Phase-5 UX audit — deferred items, not dropped)
- [ ] Shared PhoneOtpForm component (login flows aligned but still duplicated).
- [ ] Vector icon system to replace emoji iconography (cross-platform consistency,
      tinting, semantics). Needs a design asset decision.
- [x] Banner carousel — HorizontalPager landed 2026-08-30 (fixes opaque-
      crossfade ghosting AND adds swipe).
- [ ] Brand filter as multi-select sheet ("Brand ⌄" pattern) once catalogue grows.
- [ ] Terms of Service & Privacy Policy pages (legal content required before
      launch — link affordance removed until real pages exist). ← arguably P0 at launch.
- [ ] Splash warm-start: prefetch home data during the logo beat.
- [ ] "India's first Voice Commerce" + "SAVE 8–20%" are founder-supplied claims,
      now config-sourced (BrandCopy). DECISION D6: substantiate or amend before launch.

## P2 — original polish list
- [x] Palette (D1), tagline (D2), Poppins (D3) — RESOLVED in Phase 6: final
      ramp in docs/DESIGN_SPEC.md, tagline is the lockup line, Poppins adopted.
- [ ] Micro-interactions, transitions, checkout polish.
- [ ] Performance pass (emulator cold start is slow under swiftshader;
      profile on hardware).
