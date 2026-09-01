# Tazzzo — Smart Groceries. Better Prices. 🛒

India-first quick-commerce app (Blinkit-style) with **1-hour delivery**, **Tazzzo Coins**
rewards and **India's first Voice Commerce** (releasing soon 🎙️).

> 📖 **Full reference: [DOCUMENTATION.md](DOCUMENTATION.md)** — architecture, every screen, data layer,
> backend integration, Android enablement, troubleshooting and roadmap.

**One Kotlin codebase → iOS + Android**, built with
[Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) +
[Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/).

## Project layout

```
tazzzo/
├── composeApp/                     # ALL shared Kotlin code (UI + data)
│   └── src/
│       ├── commonMain/kotlin/com/tazzzo/app/
│       │   ├── App.kt              # root composable + navigation host
│       │   ├── AppState.kt         # back stack, cart, session, tabs
│       │   ├── theme/              # brand colors from the flyer
│       │   ├── data/
│       │   │   ├── model/          # Category, Product, Order, Coins…
│       │   │   ├── MockCatalog.kt  # full taxonomy: 19 aisles, 60+ products
│       │   │   ├── repository/     # interfaces + Mock impls (swap → Remote)
│       │   │   └── remote/         # ApiConfig: microservice base URLs
│       │   └── ui/
│       │       ├── splash/         # logo splash
│       │       ├── onboarding/     # marquee + login/skip, phone OTP
│       │       ├── home/           # home, categories, search, cart, orders,
│       │       │                   # coins, help, about, account
│       │       ├── guided/         # first-run guided journey overlay
│       │       ├── voice/          # voice commerce "coming soon" sheet
│       │       └── common/         # cards, marquee, steppers, cart bar…
│       ├── iosMain/                # iOS entry (ComposeUIViewController)
│       └── androidMain/            # Android entry (ready, target disabled)
├── iosApp/                         # Xcode wrapper app
│   └── project.yml                 # XcodeGen spec (regenerates .xcodeproj)
└── gradle/…                        # wrapper + version catalog
```

## Run on iOS

```bash
cd tazzzo
JAVA_HOME=$(/usr/libexec/java_home -v 21) xcodebuild \
  -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
```

or open `iosApp/iosApp.xcodeproj` in Xcode and hit ▶︎. The Xcode build phase
compiles the Kotlin framework automatically via `./gradlew embedAndSignAppleFrameworkForXcode`.

## Enabling Android (when the Android SDK is installed)

1. In `gradle/libs.versions.toml` — already contains AGP + SDK versions.
2. In `build.gradle.kts` (root): add `alias(libs.plugins.androidApplication) apply false`.
3. In `composeApp/build.gradle.kts`: uncomment the `androidApplication` plugin alias,
   `androidTarget()`, the `androidMain.dependencies` block and the `android { … }` block.
4. Uncomment `composeApp/src/androidMain/kotlin/com/tazzzo/app/MainActivity.kt`.
5. `./gradlew :composeApp:assembleDebug`

## Connecting the JavaScript microservices (the plan)

UI never touches the network directly — it talks to interfaces in
`data/repository/Repositories.kt`, currently backed by `Mock*` classes.

1. Set the real gateway/service URLs in `data/remote/ApiConfig.kt`
   (auth / catalog / orders / coins / voice / support services — endpoint
   sketch documented in that file).
2. Add Ktor client to `composeApp/build.gradle.kts` (`ktor-client-core`,
   `ktor-client-darwin` for iOS, `ktor-client-okhttp` for Android).
3. Implement `RemoteCatalogRepository` etc. next to the mocks.
4. Swap one line each in `ServiceLocator`.

## Demo hooks

Two environment variables make demos and screenshot runs painless (inert otherwise):

```bash
# walk every screen automatically (autopilot + guided tour auto-advance)
SIMCTL_CHILD_TAZZZO_DEMO_TOUR=1 xcrun simctl launch booted com.tazzzo.app
```

```bash
# jump straight to Home (skip splash/onboarding)
SIMCTL_CHILD_TAZZZO_DEMO_HOME=1 xcrun simctl launch booted com.tazzzo.app
```

## Brand

| Token | Value | Source |
|---|---|---|
| Green | `#00411C` | flyer headline / logo TA·O |
| Dark green | `#0D321D` | flyer banner |
| Orange | `#FF5002` | logo ZZZ / headline |
| Cream | `#F7F6F2` | flyer background |
| Coin gold | `#F5B301` | Tazzzo Coins |

Logo: `composeApp/src/commonMain/composeResources/drawable/tazzzo_logo.png`
(extracted from `Flyer 2 Pages.pdf`).
