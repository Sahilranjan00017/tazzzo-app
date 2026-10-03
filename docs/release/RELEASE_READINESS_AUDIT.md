# Tazzzo — Production and store release readiness audit

Audit date: 2026-10-03. Audited commit: `ae7cca2` (main after UI-08, PR #21).
Audit only. No business logic changed, no capability flipped, production ordering remains OFF, nothing submitted.
Backend, infrastructure and web repositories were read-only. Evidence that could not be obtained is marked BLOCKED, never assumed.

## Verdict summary

| Area | Status |
|---|---|
| Backend environment (dev / staging / production) | BLOCKED: no deployment behind any API host |
| OTP / auth in a real environment | BLOCKED: no production OTP provider exists in the backend |
| Real catalogue, images, E2E purchase | BLOCKED: nothing reachable |
| Android code | NOT READY: lint crash blocks `assembleRelease`, no signing, target SDK to re-verify |
| iOS code | NOT READY: no privacy manifest, no team, release archive not verified |
| Legal / privacy | NOT READY: no URLs, no in-app account deletion |
| Production ordering | NOT READY (gates in section 15) |

## 1. Environment and backend (Audit 1)

Probed 2026-10-03 with `curl` and `dig`.

| Host | DNS | TLS | HTTP |
|---|---|---|---|
| api.tazzzo.com | resolves (Vercel IPs) | valid wildcard `*.tazzzo.com`, Let's Encrypt, expires 2026-11-06 | 404 `DEPLOYMENT_NOT_FOUND` on `/actuator/health`, `/v1/categories`, `/v1/serviceability`, `/v1/auth/otp/request` |
| staging-api.tazzzo.com | resolves (Vercel IPs) | valid | same 404 |
| dev-api.tazzzo.com | resolves (Vercel IPs) | valid | same 404 |

The hosts are pointed at Vercel, which has no deployment for them. The backend is a Spring Boot / Java 21 service that cannot run on Vercel. `tazzzo-infrastructure` is a documentation-only placeholder: no Terraform, no AWS, no Docker.

| Item | Result |
|---|---|
| API host resolution | PASS (DNS only) |
| TLS / HTTPS | PASS (certificate valid; renewal needed before 2026-11-06 unless auto-managed) |
| Auth connectivity | BLOCKED |
| Mongo connectivity | BLOCKED (cannot verify; no service) |
| Redis connectivity | BLOCKED (cannot verify; no service) |
| Catalogue, inventory, pricing, serviceability | BLOCKED |
| Cart, addresses, checkout, order placement, order fetch | BLOCKED |

Backend documentation records six deployment gates, all PENDING / UNVERIFIED: the `orders` collection must be empty before the strict-schema deploy, plus five Membership gates (no conflicting `memberships` collection, SchemaBootstrap privileges, identical plan/rule/OIDC config across instances, no read-preference override, cluster default read/write concern).

## 2. Auth / OTP (Audit 2)

From backend code and docs (not exercised live):

- Implemented: request/verify lifecycle, keyed HMAC verifier (OTP never stored in plaintext), 5 min validity, 30 s resend cooldown, 5 max attempts, rate limiting through the shared Redis limiter, generic `OTP_INVALID` for all failures, session + refresh rotation, logout.
- **Blocker:** there is no production OTP delivery provider. Only an interface and a dev-only `LOGGING` provider that logs a masked phone and never the code. With no provider configured the service answers 503. A real SMS / WhatsApp vendor, its credentials and its DLT registration (India) are required.
- Secrets required and unset: access-token key, OTP key, refresh-token key (three separate base64 keys, no defaults, fail-closed). Secret management (store, rotation) does not exist because infrastructure does not exist.
- Staging cannot run a real-customer OTP test until a provider (or an approved test provider) exists. A staging reviewer account with a fixed OTP does not exist and must not be added in production code.

## 3. Real catalogue (Audit 3)

BLOCKED. No deployed data. The app side is verified only against contract fixtures and labelled harness renders. Image behaviour (crop, white background, transparency, resolution, orientation) cannot be judged without real thumbnails. A visual pass over a representative image set is required once staging has data.

## 4. Real purchase E2E (Audit 4)

BLOCKED. Staging does not exist. Contract-level behaviour (idempotent replay, PRICE_CHANGED, PAYABLE_CHANGED, ambiguous recovery, zero and positive payable) is covered by 1032 unit tests, not by a real run. Production ordering must not be tested before staging sign-off.

## 5. Production capability flags (Audit 5)

Current REMOTE values from `CatalogMode.kt` and `UiPolicyTest`.

| Capability | Production value | Class | Reason |
|---|---|---|---|
| Catalogue integration | ON | required | Reads real categories and products when a backend exists |
| Cart integration | ON | required | Server cart |
| Checkout integration | ON | required | Quote |
| orderIntegration | OFF | **B. release blocker** | An app that cannot place an order is not a shop. Turn on only through section 15 |
| orderHistoryIntegration | OFF | **B** for launch with orders ON; confirmation screen alone is not history | No list endpoint in the contract |
| Search | OFF | **A. acceptable for V1** | Browse hierarchy covers discovery. Home and Shop show truthful unavailable state |
| Deals, banners, bestsellers | OFF | A | Not promised in the UI |
| Coins | unavailable | A (post-launch feature C) | No contract; UI says so plainly |
| Support channel | none configured | **B** | Store review and customer care need a real contact |
| Voice / Genie | coming soon | A (C) | Truthful placeholder, no microphone permission |
| Legal links | none configured | **B** | Store submission requires privacy policy URL; app references Terms and Privacy on Login |

## 6. Android release (Audit 6)

| Item | Finding |
|---|---|
| applicationId | `com.tazzzo.app` |
| versionName / versionCode | `1.0` / `1` hard-coded in `composeApp/build.gradle.kts`; needs a per-release increment policy |
| minSdk / targetSdk / compileSdk | 24 / 35 / 35. **Verify against current Google Play target-API policy: the yearly requirement likely moved to API 36 by 2026-08-31, which would block a new submission** |
| Release signing | none configured; `assembleRelease` produces `composeApp-release-unsigned.apk` (17.9 MB). Keystore strategy not defined (Play App Signing recommended; upload key held outside the repo) |
| R8 / ProGuard | `isMinifyEnabled = false`, no resource shrinking; 104 MB uncompressed, MOCK product photos shipped in release |
| **`assembleRelease` default build** | **FAILS** in `lintVitalAnalyzeRelease`: lint crash `KaCallableMemberCall ... interface was expected` in a lifecycle detector. The APK built only with lint tasks excluded on the command line. Needs a lint or AGP/Kotlin version fix |
| Permissions | `INTERNET` only (plus the AndroidX dynamic-receiver guard permission). No microphone, location, contacts, storage |
| Exported components | `MainActivity` only (launcher). No services, receivers or providers of its own |
| Deep links / app links | none |
| Network security / cleartext | no custom config; targetSdk 35 default blocks cleartext; all URLs in code are https and image/legal URLs are validated https |
| Backup | default `allowBackup`, with explicit exclusion of the encrypted auth blob (`tazzzo_secure_auth.xml`) for cloud and device transfer. Plain preferences (cart, onboarded, selected address id) are backed up; no tokens |
| Debuggable | not set in release |
| Debug flags in release | see finding F-1 below |
| Logging | see finding F-2 below |
| Crash behaviour | no crash reporter integrated; failures degrade to calm states |
| Release installability | not verified: no signing, so the APK cannot be installed |

Signed-release readiness: **NOT READY.**

## 7. iOS release (Audit 7)

| Item | Finding |
|---|---|
| Bundle identifier | `com.tazzzo.app` |
| Marketing version / build | `1.0` / `1`, hard-coded in `Info.plist` |
| Signing / team | no `DEVELOPMENT_TEAM`, identity `iPhone Developer`; no distribution profile. Not modified |
| Entitlements | none file present (none needed today: no push, no Sign in with Apple, no associated domains) |
| Info.plist | portrait only, no usage-description strings (none needed: no camera, microphone, location, contacts). No `ITSAppUsesNonExemptEncryption` key: App Store Connect will ask the export-compliance question every upload unless declared |
| Privacy manifest | **missing** (`PrivacyInfo.xcprivacy`). The app persists with UserDefaults-backed settings, a required-reason API; the manifest is required for submission |
| ATS | default (no exceptions); all remote URLs https |
| Debug/demo flags | see F-1 |
| Deployment target | iOS 15.6 |
| Release archive | **NOT VERIFIED.** Unsigned `xcodebuild archive` (Release, generic iOS) failed in the Kotlin/Native release link with `OutOfMemoryError: Java heap space` at the project's 2 GB Gradle heap (`DevirtualizationAnalysis`). A retry with a 5 GB heap on this 8 GB host exhausted swap and was stopped after 20 minutes. CI builds only the simulator debug link, so no release archive has ever been proven. Needs a larger-memory runner and an explicit heap setting |
| App Store Connect prerequisites | Apple Developer Program team, app record, bundle id, distribution certificate and profile, privacy answers, review notes: all absent from the repo |

## 8. Privacy and legal (Audit 8) — factual app-data inventory from code

Sent to the backend (when a backend exists): phone number (OTP login), a random installation id (header, anti-abuse only), serviceability PIN code, delivery addresses (recipient name, recipient phone, address lines, PIN), cart lines, checkout quote and order placement (COD).

Stored on the device: encrypted session tokens in Android Keystore-backed storage / iOS Keychain (`AfterFirstUnlockThisDevice`); plain settings hold cart lines, selected address id, launch PIN, installation id, onboarding flags and the pending-order marker (no items, address, phone, price or payment data per the code comment). The signed-in profile does not persist the phone number in REMOTE mode.

Analytics: a local sink only. No vendor SDK, no advertising id, no crash reporter, no third-party SDKs in the dependency list.

Not collected: location, contacts, microphone, camera, photos, payment card data (COD only), advertising identifiers.

Product must provide: Privacy Policy URL, Terms URL, a support contact (email or phone) and an approved FAQ, data retention and deletion statements, and the company legal entity.

**Account deletion: not implemented.** The v1 contract has no delete-customer endpoint and the app has no deletion entry. Both Google Play and Apple require an in-app path (plus a web resource for Play) for apps that create accounts. **Release blocker.**

No legal claim is drafted here beyond what the code supports.

## 9. Store metadata checklist (Audit 9)

Do not fabricate URLs or contact details. Every item is OPEN unless marked.

Google Play: app name (brand name `Tazzzo`, confirm); short description (80 chars); full description (4000 chars); icon 512x512 (launcher icon exists, 512 export needed); feature graphic 1024x500; phone screenshots (2 to 8); category (Shopping or Food & Drink, Product decision); contact email, website, phone; privacy policy URL; Data Safety form (use the inventory above); content rating questionnaire; target audience (18+ recommended given delivery of groceries; Product decision); app access instructions with a reviewer account and working OTP; account deletion URL and in-app path; ads declaration (none); government/financial declarations.

Apple: name; subtitle (30); description; keywords (100); promotional text; screenshots for 6.9-inch and 6.5-inch iPhone (required) and iPad only if iPad is supported; app icon 1024 (present in asset catalogue, verify no alpha); support URL; marketing URL (optional); privacy policy URL; age rating questionnaire; App Privacy nutrition labels (use the inventory above); review notes with a demo account and OTP method (reviewer cannot receive a real SMS, so a documented test path is required); export compliance; content rights; sign-in requirement note.

## 10. Store screenshots (Audit 10)

No valid candidate exists today. Live frames from the production build show failure states because no backend is reachable. Populated frames in `UI Page/Generated Review/` come from a labelled test harness and must not be presented as production.

Approved candidate set once staging data exists, captured from the real app with a controlled staging account and real staging catalogue: Home, Shop, product list, product detail, cart, checkout (COD), order placed, Profile. Genie is not a store screenshot (placeholder). Sizes: Android 1080x1920 or taller, iPhone 6.9-inch and 6.5-inch.

## 11. Security scan (Audit 11)

`gitleaks` and `trufflehog` are not installed on the audit host and the repository has no secret scanning in CI. Substitute: a regex scan of the working tree and 300 commits for AWS, Google, Stripe, Razorpay, Slack, GitHub tokens, private keys, JWT shapes, Mongo URIs and `key|secret|password|token` assignments, plus tracked keystore, provisioning and `.env` files.

| Check | Result |
|---|---|
| Secrets in tree and history window | none found |
| Tracked keystores, profiles, `.env`, google-services | none |
| Hard-coded credentials / personal phone or email | none in shipped code (a legacy WhatsApp number constant exists in MOCK-only copy) |
| HTTP (non-TLS) URLs | none in shipped code |
| Debug endpoints | none |
| **F-1** debug/demo flags in release | Android `MainActivity` is exported and reads intent extras `taz_start_home`, `taz_fail_load`, `taz_mock_catalog` without the debug gate, and `DemoTourRunner` acts on `startAtHome` in release. Effect: any app can launch Tazzzo into the demo-home path that skips onboarding (catalogue stays REMOTE; MOCK switching is gated). On iOS the equivalent flags read the process environment ungated, including `TAZZZO_DEMO_START` added in UI-08. Practical exposure is low on iOS (environment cannot be set on a customer device) but the shared `allowsDevTooling` gate is not applied. Recommended fix: gate all demo actuals on `AppEnvironment.allowsDevTooling`. Not changed in this audit |
| Repo secret scanning | absent from CI: add gitleaks |

## 12. Privacy logging (Audit 12)

| Check | Result |
|---|---|
| **F-2** Analytics default sink | `Analytics.sink` defaults to `DevLogSink`, which calls `println`. The `allowsDeveloperLogging` guard exists but is never consulted. `println` reaches logcat on Android. In release this prints event names and properties |
| Content of those events | event names plus ids. Properties seen: `product_id`, `order_id` (order placement and reorder), plan id, stage, checkout step, and the raw search `query` (AppState search event). No tokens, phone, address or money amounts found in the 25 call sites. **Violation:** with ordering enabled, a release build would print order ids to the device log, and any search text would be printed (search is OFF in REMOTE today) |
| `DemoTour` search check `println` | runs only behind the demo-tour flag |
| HTTP client logging | no Ktor Logging plugin installed |
| Tokens, OTP, phone, email, addresses, cart, orders, money, voice transcript | not logged by app code; there is no voice transcript |

Report only: recommend making `Analytics.sink` start as `NoopSink` unless `allowsDeveloperLogging`.

## 13. Performance and stability (Audit 13)

Release APK could not be installed (unsigned). Observations carried from UI-03 to UI-08 emulator and harness runs on debug builds, not release measurements: no ANR or crash in journeys after the emulator background-app interference was removed; image pipeline has a 48 MB memory cache, 6 MB fetch cap and 1024 px decode bound; no retry loops (failures are not cached, retry is user-driven). Startup time, jank and memory on a signed release build on a physical mid-tier device are **not measured**. Release builds are unminified at 104 MB uncompressed, which affects install size and startup.

## 14. Accessibility final (Audit 14)

Re-confirmed from UI-08 evidence: merged semantics, roles, 44 dp targets, contrast ratios, 1.3x font scale pass on five harness screens, keyboard types set on phone and PIN fields.

**Correction to the UI-08 report:** ambient motion is gated through `MotionSettings.ambientEnabled`, but nothing in production code sets that flag from the operating system's reduce-motion or animator-duration setting. Only instrumented tests set it. So "reduced motion respected" is **not true today**. Fix: wire Android `ANIMATOR_DURATION_SCALE == 0` and iOS `UIAccessibility.isReduceMotionEnabled` into the flag.

Not re-run on this audit: TalkBack and VoiceOver live passes on a populated backend, 2.0x font scale.

## 15. Gates before `orderIntegration = true` (Audit 15)

1. Backend deployed and healthy in staging, then production (health endpoint, alarms, logs).
2. The six backend deployment gates verified (orders count 0, five Membership gates).
3. Mongo replica set with transactions reachable; Redis reachable; secrets in a managed store.
4. Real OTP provider integrated, DLT approved, rate limits tuned, reviewer path defined.
5. Real catalogue, pricing, inventory and image data loaded; image review passed.
6. Serviceability data for launch PINs.
7. Staging E2E with a real staging customer: OTP, address, serviceability, product, cart, quote, COD, place, confirmation, GET order.
8. Duplicate-tap and replay: exactly one order. PRICE_CHANGED, PAYABLE_CHANGED, ambiguous recovery, zero and positive payable exercised.
9. Order history list contract if history is to be enabled with orders.
10. Operational: support channel and fulfilment process able to receive and deliver real orders; cancellation policy.
11. Observability: request metrics, order-failure alerts, privacy-reviewed logging, crash reporting decision.
12. Rollback plan: server (previous image, schema compatibility) and app (flag flip is build-time, so a rollback is a store release; consider a remote capability flag).
13. Privacy policy, terms, account deletion live.
14. Signed release builds verified on devices.

## 16. Findings and release blockers

Blockers (must fix or provide):

1. No deployed backend in any environment.
2. No production OTP provider.
3. `assembleRelease` fails (lint crash); no release signing; Android target-SDK compliance unverified.
4. No privacy policy / terms URLs; no support contact.
5. No in-app account deletion and no backend endpoint for it.
6. iOS: no privacy manifest, no team/signing, release archive unverified.
7. `orderIntegration` OFF.
8. No valid store screenshots (needs staging data).

Required hygiene before submission (not logic changes): F-1 gate demo flags, F-2 make the default analytics sink silent in release, enable minify/resource shrinking decision, remove MOCK photography and fixtures from release, add secret scanning to CI, wire reduce-motion, declare `ITSAppUsesNonExemptEncryption`, set real version numbers.

Acceptable for V1: search, coins, deals, banners, voice, order history list if confirmation plus order detail suffice, notifications, language selection, membership.
Post-launch: search, coins, Genie voice ordering, order history, delivery slots, notifications.

## 17. Exact next actions

1. Product: provide Privacy Policy, Terms, support contact and FAQ, legal entity, store category, target audience.
2. Engineering/Infra: stand up the backend (staging first): hosting, Mongo replica set, Redis, secrets, DNS for the three API hosts, health checks.
3. Engineering: integrate and configure a real OTP provider; define the reviewer test path.
4. Engineering: add account deletion (backend contract plus app entry).
5. Engineering: fix the Android release build (lint crash), add signing through Play App Signing, confirm target SDK against Play policy, decide minify.
6. Engineering: iOS privacy manifest, team and signing, export-compliance key, release archive on CI with adequate memory.
7. Engineering: hygiene fixes F-1, F-2, reduce-motion wiring, CI secret scan.
8. Run staging E2E and image review; capture store screenshots from staging.
9. Fill Data Safety and App Privacy from the inventory; complete store listings.
10. Decide on `orderIntegration` only after section 15 is green.
