# Tazzzo — Complete Documentation

**Smart Groceries. Better Prices.**
India-first quick-commerce app — 1-hour delivery, Tazzzo Coins rewards, and India's first voice commerce (coming soon).

One Kotlin codebase → **iOS and Android**.

---

## Table of contents

1. [At a glance](#1-at-a-glance)
2. [Tech stack](#2-tech-stack)
3. [Quick start](#3-quick-start)
4. [Project structure](#4-project-structure)
5. [How the app boots](#5-how-the-app-boots)
6. [Navigation and state](#6-navigation-and-state)
7. [Screen reference](#7-screen-reference)
8. [Design system](#8-design-system)
9. [Shared components](#9-shared-components)
10. [Data layer](#10-data-layer)
11. [Business rules](#11-business-rules)
12. [Connecting the backend](#12-connecting-the-backend)
13. [Enabling Android](#13-enabling-android)
14. [Demo hooks](#14-demo-hooks)
15. [Assets and attribution](#15-assets-and-attribution)
16. [Known limitations](#16-known-limitations)
17. [Troubleshooting](#17-troubleshooting)
18. [Roadmap](#18-roadmap)

---

## 1. At a glance

| | |
|---|---|
| **Product** | Quick-commerce grocery app (Blinkit/Zepto category) |
| **Brand** | Tazzzo — "Smart Groceries. Better Prices." |
| **Differentiators** | 1-hour delivery · Tazzzo Coins (2% back) · Voice commerce (coming soon) |
| **Platforms** | iOS (running today) · Android (scaffolded, see §13) |
| **Bundle ID** | `com.tazzzo.app` |
| **Code size** | 30 Kotlin files, 5,420 lines |
| **Shared code** | 5,383 lines (99.3%) in `commonMain` |
| **Demo catalog** | 19 categories · 56 subcategories · 63 products · 6 FAQs |
| **Backend** | Not connected yet — mock data; see §12 |
| **WhatsApp ordering** | Live today: say "HI" to **8050316087** |

---

## 2. Tech stack

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin 2.2.20 | One language for both phones |
| UI | Compose Multiplatform 1.9.0 | One UI codebase for iOS + Android |
| Build | Gradle 8.14 (wrapper included) | Nothing to install but a JDK |
| JDK | **Java 21** (Temurin) | Required — see §17 |
| iOS shell | SwiftUI + XcodeGen | ~20 lines of Swift total |
| iOS target | 15.6+ | |
| Android target | minSdk 24, compileSdk 35 | Config ready, not enabled |
| Networking | *(not added yet)* — Ktor recommended | See §12 |
| Persistence | *(none yet)* | State is in memory only |

No navigation library, no dependency-injection framework, no image-loading library. Everything is plain Compose plus a small hand-written state class — deliberately, so the codebase stays readable.

---

## 3. Quick start

### Run on iOS

```bash
cd tazzzo
JAVA_HOME=$(/usr/libexec/java_home -v 21) xcodebuild \
  -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -derivedDataPath build/DerivedData build CODE_SIGNING_ALLOWED=NO
```

Then install and launch:

```bash
xcrun simctl install booted build/DerivedData/Build/Products/Debug-iphonesimulator/Tazzzo.app
```

```bash
xcrun simctl launch booted com.tazzzo.app
```

Or simply open `iosApp/iosApp.xcodeproj` in Xcode and press ▶︎. The Xcode build phase compiles the Kotlin framework automatically.

### Compile the shared Kotlin only (fast check)

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

### Regenerate the Xcode project

`iosApp/iosApp.xcodeproj` is generated from `iosApp/project.yml`. After editing that file:

```bash
cd iosApp && xcodegen generate
```

---

## 4. Project structure

```
tazzzo/
├── build.gradle.kts             Project-wide build config
├── settings.gradle.kts          Module list + repositories
├── gradle.properties            JVM memory, Kotlin flags
├── gradlew                      The build command (no install needed)
├── gradle/
│   ├── libs.versions.toml       Every library version, one place
│   └── wrapper/                 Gradle wrapper binary + config
│
├── composeApp/                  ◀── ALL YOUR CODE LIVES HERE
│   ├── build.gradle.kts         Module build config (Android blocks commented)
│   └── src/
│       ├── commonMain/          Shared: 27 files, 5,383 lines
│       │   ├── kotlin/com/tazzzo/app/
│       │   │   ├── App.kt               Root composable + routing table
│       │   │   ├── AppState.kt          Navigation, cart, session
│       │   │   ├── DemoTour.kt          Demo autopilot (env-gated)
│       │   │   ├── theme/Theme.kt       Brand colours
│       │   │   ├── data/
│       │   │   │   ├── model/Models.kt        Data shapes
│       │   │   │   ├── MockCatalog.kt         Demo catalogue
│       │   │   │   ├── repository/            Contracts + mock impls
│       │   │   │   └── remote/ (ApiConfig removed; hosts: config/AppEnvironment.kt)
│       │   │   └── ui/
│       │   │       ├── common/Components.kt   Shared UI pieces
│       │   │       ├── splash/                Splash screen
│       │   │       ├── onboarding/            Login screens
│       │   │       ├── home/                  12 shop screens
│       │   │       ├── guided/                Spotlight tour
│       │   │       └── voice/                 Voice sheet
│       │   └── composeResources/drawable/     22 images (shared)
│       │
│       ├── iosMain/             iOS entry point (20 lines)
│       └── androidMain/         Android entry point (17 lines, disabled)
│
├── iosApp/                      Xcode wrapper app
│   ├── project.yml              XcodeGen spec
│   └── iosApp/
│       ├── iOSApp.swift         The only Swift file
│       ├── Info.plist           App metadata
│       └── Assets.xcassets/     App icon
│
└── docs/
    ├── screenshots/             30 current screenshots
    ├── screenshots-v1/          17 pre-redesign screenshots
    └── IMAGE_ATTRIBUTIONS.md    Photo credits and licences
```

---

## 5. How the app boots

Four hops from tapping the icon to the splash screen.

| # | File | What happens |
|---|---|---|
| 1 | `iosApp/iosApp/iOSApp.swift:5` | iOS launches the SwiftUI app |
| 2 | `iosApp/iosApp/iOSApp.swift:16` | Calls `MainViewControllerKt.MainViewController()` |
| 3 | `composeApp/src/iosMain/…/MainViewController.kt` | `ComposeUIViewController { App() }` — the bridge into Kotlin |
| 4 | `composeApp/src/commonMain/…/App.kt:26` | **Real entry point.** Creates state, applies theme, routes to a screen |

On Android, steps 1–3 are replaced by `MainActivity.kt` calling `setContent { App() }`. **Step 4 onward is identical** — that is why one codebase serves both platforms.

---

## 6. Navigation and state

### `App.kt` — the routing table

Every screen is listed once, in a single `when` block (lines 33–44). Reading it tells you the whole app.

```kotlin
when (screen) {
    is Screen.Splash         -> SplashScreen()
    is Screen.Onboarding     -> OnboardingScreen()
    is Screen.Login          -> LoginScreen()
    is Screen.Home           -> MainScaffold()
    is Screen.CategoryDetail -> CategoryDetailScreen(screen.categoryId, screen.subcategoryId)
    is Screen.Search         -> SearchScreen()
    is Screen.Cart           -> CartScreen()
    is Screen.OrderSuccess   -> OrderSuccessScreen(screen.orderId)
    is Screen.Orders         -> OrdersScreen()
    is Screen.Coins          -> CoinsScreen()
    is Screen.Help           -> HelpScreen()
    is Screen.About          -> AboutScreen()
}
```

### `AppState.kt` — the app's memory

A single class, `TazzzoAppState`, provided to every screen through `LocalAppState.current`.

**Navigation** — a back-stack, no library:

```kotlin
app.navigate(Screen.Cart)    // push a screen (back arrow works)
app.back()                   // pop one screen
app.resetTo(Screen.Home)     // clear history (used after login)
app.homeTab = HomeTab.ACCOUNT // switch bottom tab
```

**Session**

| Property | Type | Notes |
|---|---|---|
| `user` | `UserProfile` | name, phone, isGuest, coinBalance, address |
| `guidedJourneyPending` | `Boolean` | Set when the user skips or logs in; triggers the tour |
| `showVoiceSheet` | `Boolean` | Opens the voice "coming soon" sheet |
| `guidedTargets` | `Map<String, Rect>` | On-screen bounds the tour spotlights |

**Cart**

| Call | Purpose |
|---|---|
| `addToCart(product)` / `removeFromCart(product)` | Change quantity |
| `quantityOf(product)` | Current quantity |
| `cartItemCount` | Badge number |
| `cartLines(allProducts)` | Cart as a list of lines |
| `bill(lines)` | Full price breakdown (see §11) |
| `clearCart()` | Empty it |

### Adding a new screen

1. Add an entry to `sealed interface Screen` in `AppState.kt` (lines 9–21).
2. Add one line to the `when` block in `App.kt`.
3. Write the composable.

---

## 7. Screen reference

All paths relative to `composeApp/src/commonMain/kotlin/com/tazzzo/app/`.

| Screen | File | Lines | Contents |
|---|---|---|---|
| Splash | `ui/splash/SplashScreen.kt` | 96 | Logo fade-in, auto-advances after 1.8 s |
| Login (photo wall) | `ui/onboarding/OnboardingScreen.kt` | 382 | Two moving photo rows, logo badge, phone → OTP, Skip |
| Login (standalone) | `ui/onboarding/LoginScreen.kt` | 331 | Secondary phone/OTP screen reachable from Account |
| Main shell | `ui/home/MainScaffold.kt` | 123 | 4 bottom tabs, cart bar, tour + voice overlays |
| Home tab | `ui/home/HomeTabContent.kt` | 399 | Header, search, banner carousel, category grid, 6 product rails |
| Categories tab | `ui/home/CategoriesTabContent.kt` | 95 | All 19 aisles grouped by section |
| Order Again tab | `ui/home/OrderAgainTabContent.kt` | 213 | Past orders with one-tap reorder |
| Account tab | `ui/home/AccountTabContent.kt` | 278 | Profile, quick stats, menu, log out |
| Category detail | `ui/home/CategoryDetailScreen.kt` | 172 | Sidebar of subcategories + 2-column product grid |
| Search | `ui/home/SearchScreen.kt` | 195 | Debounced search, popular chips, trending rail |
| Cart | `ui/home/CartScreen.kt` | 348 | Line items, savings, bill details, Place Order |
| Order success | `ui/home/OrderSuccessScreen.kt` | 116 | Confirmation, ETA, coins earned |
| Orders | `ui/home/OrdersScreen.kt` | 219 | History with 4-step delivery progress, reorder |
| Tazzzo Coins | `ui/home/CoinsScreen.kt` | 168 | Balance hero, how-it-works, transaction ledger |
| Help | `ui/home/HelpScreen.kt` | 153 | WhatsApp card, expandable FAQs, callback |
| About | `ui/home/AboutScreen.kt` | 113 | Brand story and value props |
| Guided tour | `ui/guided/GuidedJourney.kt` | 310 | 7-step spotlight coach marks with pointing arrow |
| Voice sheet | `ui/voice/VoiceSheet.kt` | 161 | Pulsing mic, "coming soon", notify-me |

### The guided tour

A spotlight overlay: the screen dims, a cutout highlights the real element, and an animated arrow points at it. Elements register themselves with `Modifier.guidedTarget("key")`.

| Step | Key | Highlights |
|---|---|---|
| 1 | *(none)* | Welcome card, centred |
| 2 | `location` | Delivery address |
| 3 | `search` | Search bar |
| 4 | `coins` | Tazzzo Coins chip |
| 5 | `mic` | Voice commerce button |
| 6 | `categories` | Category grid |
| 7 | `bottomnav` | Bottom tab bar |

---

## 8. Design system

`theme/Theme.kt` holds every colour. Change it there and it changes everywhere.

| Token | Hex | Used for |
|---|---|---|
| `Green` | `#00411C` | Headlines, primary buttons, logo |
| `GreenDark` | `#0D321D` | Dark banners, voice strip |
| `GreenMid` | `#1B6B3A` | Gradients, secondary accents |
| `GreenSoft` | `#E7F2EA` | Selected states, soft chips |
| `Orange` | `#FF5002` | Discount badges, "SAVE 8–20%", accents |
| `OrangeSoft` | `#FFEFE6` | Orange chip backgrounds |
| `Cream` | `#F7F6F2` | Page background |
| `CoinGold` | `#F5B301` | Tazzzo Coins, ratings |
| `Success` | `#1B8A3E` | ADD buttons, savings |
| `Danger` | `#D93025` | Errors |
| `TextPrimary` | `#1C1C1C` | Body text |
| `TextSecondary` | `#666666` | Captions |
| `CardBorder` | `#EDEAE3` | Hairlines |

All values are sampled from the original Tazzzo flyer.

**Conventions:** 12–20 dp corner radii · soft shadows (`spotColor` at 25–35% black) · cards on white over cream · emoji used as iconography (no icon library) · 90 dp bottom content padding so the floating cart bar never covers content.

---

## 9. Shared components

`ui/common/Components.kt` (772 lines) is the highest-leverage file in the project — editing one component updates every screen.

| Component | Purpose |
|---|---|
| `ProductCard(product)` | The product card used in every rail and grid |
| `QuantityStepper(product)` | ADD button that becomes − 2 + |
| `ProductRail(title, products)` | Horizontal scrolling row of cards |
| `CategoryTile(category, size)` | Photographic aisle tile with label |
| `PhotoChip(category)` | Round-photo chip for marquees |
| `HeroBasketBanner()` | "SAVE 8–20%" with the real flyer basket photo |
| `DeliveryPromoBanner()` | "Delivered in 1 Hour" with animated speed lines |
| `CoinsPromoBanner()` | "Earn Tazzzo Coins" over gold-coin photography |
| `VoiceCommerceBannerV3()` | Pulsing mic + live equaliser bars |
| `CartBar()` | Floating "View Cart" bar (a `BoxScope` extension) |
| `TazTopBar(title, onBack, trailing)` | Standard screen header |
| `CoinChip(balance, onClick)` / `MicButton(size, onClick)` | Header controls |
| `MarqueeRow(reverse, speed) { }` | Auto-scrolling row |
| `PillButton(text, onClick, …)` | Primary/secondary button |
| `LogoImage(height)` | The real Tazzzo logo (transparent PNG) |
| `Modifier.guidedTarget(key)` | Registers bounds for the guided tour |

---

## 10. Data layer

### Models — `data/model/Models.kt`

| Model | Key fields |
|---|---|
| `Category` | id, name, emoji, tint, group, subcategories |
| `Subcategory` | id, name, emoji |
| `Product` | id, name, brand, emoji, unit, price, mrp, categoryId, subcategoryId, rating, ratingCount, etaMinutes, tags, highlights, `discountPercent` |
| `CartLine` | product, quantity, `lineTotal`, `lineMrp` |
| `BillSummary` | itemTotal, itemMrpTotal, deliveryFee, handlingCharge, coinsEarned, grandTotal, `saved` |
| `Order` | id, lines, bill, status, placedAtLabel, address |
| `OrderStatus` | PLACED · PACKED · ON_THE_WAY · DELIVERED |
| `CoinTransaction` | id, title, amount, dateLabel |
| `UserProfile` | name, phone, isGuest, coinBalance, address |
| `PromoBanner`, `FaqItem` | Marketing and help content |

All money is `Int` rupees — no floating-point rounding bugs.

### Demo catalogue — `data/MockCatalog.kt`

19 aisles in four groups, matching Indian quick-commerce taxonomy:

- **Grocery & Kitchen** — Vegetables & Fruits · Dairy, Bread & Eggs · Atta, Rice & Dal · Oil, Masala & Dry Fruits · Chicken, Meat & Fish
- **Snacks & Drinks** — Munchies · Cold Drinks & Juices · Tea, Coffee & More · Instant & Frozen · Sweet Tooth · Bakery & Biscuits
- **Beauty & Personal Care** — Bath & Body · Skin & Face Care · Pharma & Wellness · Baby Care
- **Household & Lifestyle** — Cleaning Essentials · Home & Office · Pet Care · Paan Corner

63 products with real Indian brands (Aashirvaad, Daawat, Fortune, Maggi, Tata Salt, Amul, Haldiram's, Colgate…), realistic prices, MRPs, ratings and review counts.

### Repositories — `data/repository/Repositories.kt`

Screens never fetch data directly. They ask `ServiceLocator`, which returns an interface.

```kotlin
interface CatalogRepository {
    suspend fun getCategories(): List<Category>
    suspend fun getBanners(): List<PromoBanner>
    suspend fun getBestsellers(): List<Product>
    suspend fun getProducts(categoryId: String, subcategoryId: String? = null): List<Product>
    suspend fun search(query: String): List<Product>
}

interface AuthRepository {
    suspend fun requestOtp(phone: String): Boolean
    suspend fun verifyOtp(phone: String, otp: String): UserProfile?
}

interface OrderRepository {
    suspend fun placeOrder(lines: List<CartLine>, bill: BillSummary, address: String): Order
    suspend fun getOrders(): List<Order>
}

interface CoinRepository {
    suspend fun getBalance(): Int
    suspend fun getLedger(): List<CoinTransaction>
    suspend fun credit(amount: Int, title: String)
}
```

Today these are fulfilled by `Mock*` classes holding data in memory with a simulated 350 ms delay. The switchboard is four lines at the bottom of the file:

```kotlin
object ServiceLocator {
    val catalog: CatalogRepository = MockCatalogRepository()
    val auth:    AuthRepository    = MockAuthRepository()
    val orders:  OrderRepository   = MockOrderRepository()
    val coins:   CoinRepository    = MockCoinRepository()
}
```

**That is the seam.** Swap those four lines and the entire app runs on real data with no screen changes.

---

## 11. Business rules

Implemented in `AppState.bill()` (`AppState.kt:76`).

| Rule | Value |
|---|---|
| Delivery fee | **Free** on orders ≥ ₹199, otherwise **₹25** |
| Handling charge | ₹5 per order (₹0 on empty cart) |
| Tazzzo Coins earned | **2%** of item total, rounded down |
| Coin value | 1 Coin = ₹1 at checkout |
| Grand total | item total + delivery + handling |
| Savings shown | sum of MRP − sum of selling price |
| Delivery promise | 59 minutes (displayed per product and in cart) |

Order IDs follow the format `TZ100483`, incrementing per order.

---

## 12. Connecting the backend

### Architecture

```
Screen → Repository interface → HTTPS → your Node microservice → your database
```

> **The phone never talks to the database directly.** Credentials inside an app can be extracted by anyone who downloads it. Only your services hold the database password.

### Planned services — `data/remote/ApiConfig.kt`

| Constant | Handles |
|---|---|
| `GATEWAY` | Base URL (`https://api.tazzzo.com`) |
| `AUTH_SERVICE` | OTP login, tokens |
| `CATALOG_SERVICE` | Categories, products, search |
| `ORDER_SERVICE` | Checkout, order tracking |
| `COIN_SERVICE` | Coin balance and ledger |
| `VOICE_SERVICE` | Voice commerce (future) |
| `SUPPORT_SERVICE` | Help centre, tickets |

### Endpoints your Node services should expose

```
GET  /catalog/v1/categories
GET  /catalog/v1/categories/{id}/products?subcategory=
GET  /catalog/v1/search?q=
POST /auth/v1/otp/request        { phone }
POST /auth/v1/otp/verify         { phone, otp }
POST /orders/v1/orders           { lines[], addressId, payment }
GET  /orders/v1/orders?user=
GET  /coins/v1/balance
GET  /coins/v1/ledger
```

### Migration steps

**1. Point at your servers** — edit the URLs in `ApiConfig.kt`.

**2. Add Ktor** to `composeApp/build.gradle.kts`:

```kotlin
commonMain.dependencies {
    implementation("io.ktor:ktor-client-core:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
}
iosMain.dependencies     { implementation("io.ktor:ktor-client-darwin:3.0.3") }
androidMain.dependencies { implementation("io.ktor:ktor-client-okhttp:3.0.3") }
```

**3. Write real repositories** next to the mocks:

```kotlin
class RemoteCatalogRepository(private val http: HttpClient) : CatalogRepository {
    override suspend fun getCategories(): List<Category> =
        http.get("${ApiConfig.CATALOG_SERVICE}/categories").body()

    override suspend fun search(query: String): List<Product> =
        http.get("${ApiConfig.CATALOG_SERVICE}/search") { parameter("q", query) }.body()
    // …
}
```

**4. Flip the switch** in `ServiceLocator` — one line per service.

> **Keep the mock classes.** Switching back gives you a fully working offline app — invaluable for demos and investor meetings.

### Also worth adding at that point

- Add `kotlinx-serialization` and mark models `@Serializable`.
- Add loading and error states to screens (they currently assume success).
- Store the auth token — Multiplatform Settings or DataStore.
- Add retry/timeout policy to the Ktor client.

---

## 13. Enabling Android

The Android target is fully scaffolded but switched off, because the build machine has no Android SDK.

**Prerequisite:** install Android Studio (brings the SDK).

1. **Root `build.gradle.kts`** — add:
   ```kotlin
   alias(libs.plugins.androidApplication) apply false
   ```
2. **`composeApp/build.gradle.kts`** — uncomment four marked blocks: the `androidApplication` plugin alias, `androidTarget()`, `androidMain.dependencies`, and the `android { … }` block.
3. **`composeApp/src/androidMain/kotlin/com/tazzzo/app/MainActivity.kt`** — uncomment (already written).
4. **Write the missing `actual` functions.** `DemoTour.kt` declares two `expect` functions with actuals only for iOS. **Android will not compile without an `androidMain` version:**
   ```kotlin
   // composeApp/src/androidMain/kotlin/com/tazzzo/app/DemoTour.android.kt
   package com.tazzzo.app

   actual fun isDemoTourEnabled(): Boolean = false
   actual fun isDemoHomeEnabled(): Boolean = false
   ```
5. **Handle the system back button.** iOS has no hardware back, so nothing listens for it. On Android, back would quit the app instead of navigating. Wire `BackHandler` to `app.back()`.
6. Build:
   ```bash
   ./gradlew :composeApp:assembleDebug
   ```

Still to do afterwards: adaptive launcher icon and a splash theme (only the iOS icon exists today).

---

## 14. Demo hooks

Two environment variables make demos and screenshot runs painless. Both are inert unless explicitly set to `1`, `true`, `yes` or `on`.

Walk every screen automatically (autopilot + tour auto-advance):

```bash
SIMCTL_CHILD_TAZZZO_DEMO_TOUR=1 xcrun simctl launch booted com.tazzzo.app
```

Jump straight to Home, skipping splash and login:

```bash
SIMCTL_CHILD_TAZZZO_DEMO_HOME=1 xcrun simctl launch booted com.tazzzo.app
```

Implementation: `DemoTour.kt` (shared script) + `DemoTour.ios.kt` (flag reading).

---

## 15. Assets and attribution

`composeApp/src/commonMain/composeResources/drawable/` — 22 images, shared by both platforms.

| Asset | Source |
|---|---|
| `tazzzo_logo.png` | Extracted from the Tazzzo flyer, background removed |
| `hero_basket.jpg` | The product basket photo from the flyer |
| `cat_*.jpg` (19) | Wikimedia Commons, openly licensed |
| `banner_coins.jpg` | Wikimedia Commons (gold coin stacks) |

Per-image credits and licences: [`docs/IMAGE_ATTRIBUTIONS.md`](docs/IMAGE_ATTRIBUTIONS.md).

> **Before launch:** replace the Commons category photos with your own product photography. They are placeholders chosen for realism, not brand assets.

Screenshots of every screen: `docs/screenshots/` (30 files).

---

## 16. Known limitations

Honest list of what is not done yet.

| Area | Status |
|---|---|
| Backend | Not connected — all data is mock (§12) |
| Persistence | None. Cart, login and coins reset when the app is killed |
| Android | Scaffolded but not compiled or run (§13) |
| System back button | Not handled — required for Android |
| Error handling | Foundations in place (`UiState`/`LoadError`/retry, skeletons); Home + CategoryDetail migrated, remaining screens pending |
| Voice commerce | Teaser UI only; no speech recognition |
| Payments | No payment gateway; Place Order is local |
| Address book | "Coming soon" dialog, not a real picker |
| Location | Hard-coded to "HSR Layout, Bengaluru" |
| Search | Local string match over 63 products — v2 (typo tolerance, synonyms) planned Phase 2 |
| OTP | Demo mode — any 4 digits are accepted |
| Product images | Emoji on cards; photos only on category tiles |
| Tests | None written |
| Accessibility | Content descriptions on images; not audited |
| Dark mode | Light-first brand scheme only |

---

## 17. Troubleshooting

**`Unsupported class file major version` / Gradle fails**
The default JDK on this machine is 27-ea, which Gradle 8.14 rejects. Always:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

**App crashes instantly with `PlistSanityCheck`**
Compose Multiplatform requires `CADisableMinimumFrameDurationOnPhone` in `Info.plist`. It is set in `iosApp/project.yml` — if you regenerate the project, keep it.

**Simulator: "Unable to lookup in current state: Shutdown"**
The device is off. Boot it, then wait:
```bash
xcrun simctl boot <UDID> && xcrun simctl bootstatus <UDID> -b
```

**App opens straight into the guided tour**
A demo flag is set in the launch environment. Launch with it blank:
```bash
SIMCTL_CHILD_TAZZZO_DEMO_TOUR="" xcrun simctl launch booted com.tazzzo.app
```

**Xcode build can't find the Kotlin framework**
Run the Gradle step manually once:
```bash
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
```

---

## 18. Roadmap

**Next up**
1. Connect the JavaScript microservices (§12)
2. Enable and test Android (§13)
3. Persist cart and login across restarts
4. Real product photography for all 63 products

**Then**
5. Payment gateway (Razorpay / UPI)
6. Live order tracking with a map
7. Real address picker with saved addresses
8. Push notifications for order status

**The differentiator**
9. Voice commerce — speech-to-cart in Hindi and English, the feature the whole app is positioned around

---

*Tazzzo v1.0 — built with Kotlin Multiplatform. Made with 💚 in India.*
