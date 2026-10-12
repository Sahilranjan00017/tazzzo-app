# UAT build (Android and iOS)

A UAT binary is a **release-shaped** build (REMOTE catalogue, no dev tooling, no developer logging, not debuggable) that talks
to the UAT API instead of production, for internal acceptance testing. Production is unchanged: the default release command
still produces a production binary and still cannot be pointed anywhere else.

Status: pure logic verified in the scratch JVM harness; Gradle/Android/iOS behaviour is **verified only by CI on the PR head**
(no Android SDK or Xcode locally). Anything that needs a device, an Apple account or the UAT backend is **NOT RUN**.

## How the target is selected

One build-time selector, `tazzzo.env` = `prod` (default) or `uat`, read by `composeApp/build.gradle.kts`:

| Input | Where it comes from | Meaning |
| --- | --- | --- |
| `-Ptazzzo.env=uat` | Gradle property (Android command line, CI) | UAT target. Anything other than `prod`/`uat` fails the build. |
| `TAZZZO_ENV=uat` | environment variable (used by the Xcode path, see iOS) | same; the Gradle property wins if both are set |
| `-Ptazzzo.apiBase=https://...` / `TAZZZO_API_BASE` | optional, **only valid with `uat`** | UAT API origin. Default `https://api-uat.tazzzo.com`. |

Gradle writes the choice to a generated `commonMain` source file (`build/generated/tazzzo/.../GeneratedBuildTarget.kt`,
not committed), so Android and iOS compile the same constant and **nothing in the Xcode project or the manifest holds an
environment switch**. The app never reads an environment variable, intent extra or setting to choose its backend.

### Guarantees (and what enforces them)

| Guarantee | Enforced by |
| --- | --- |
| A default build is production and carries no UAT address | `UAT_API_BASE` is `""` unless the target is `uat` (CI step "Default build target is production"); `BuildTargetTest` |
| A release build cannot select another backend at runtime | unchanged: `AppEnvironment.resolve` ignores overrides in release (`ReleaseHardeningTest`, `BuildTargetTest`) |
| A UAT binary cannot reach the production API | `resolve(release, UAT)` is always `UAT`, a debug UAT build ignores a `PROD` override; the UAT address must not be the production host (Gradle + runtime guard, `UatApiBase`) |
| A production binary cannot reach UAT | `resolve(.., PROD)` never returns `UAT`; the UAT environment's address is empty there and would throw if used |
| UAT address is https-only, an origin, not the production host | `tazzzoValidateUatApiBase` (build) and `UatApiBase.check` (runtime, same rules); CI step "Wrong or production-pointing settings are refused" |
| `tazzzo.apiBase` cannot redirect a production build | the build fails if `tazzzo.apiBase` / `TAZZZO_API_BASE` is set without `uat` |
| A UAT APK is not shipped as the release one (or reverse) | task-graph guard: `assembleRelease`/`bundleRelease` with `uat` fails, `assembleUat` without `uat` fails |
| Dev tooling and the MOCK catalogue are unreachable in UAT | the `uat` build type is not debuggable (`BuildConfig.DEBUG=false`, so `allowsDevTooling` is false); iOS Release is a non-debug binary; `allowsDevTooling` is also false for any UAT-target build |
| A UAT build is visibly marked | Profile and About show `Version 1.0 (1) · UAT`; Android label `Tazzzo UAT`, application id `com.tazzzo.app.uat` (installs next to production) |

Honest limits: the production API URL string is still compiled into a UAT binary (it is the constant the guard compares
against and what `BuildEnvironment.PROD` holds); it is unreachable by selection, not absent. Production binaries contain
no UAT address.

## Android

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"      # Java 21 (see .java-version); CI uses Temurin
./gradlew :composeApp:assembleUat -Ptazzzo.env=uat
# output: composeApp/build/outputs/apk/uat/composeApp-uat.apk
```

Override the API host (for the final hostname):

```bash
./gradlew :composeApp:assembleUat -Ptazzzo.env=uat -Ptazzzo.apiBase=https://api-uat.example.org
```

Install: `adb install -r composeApp/build/outputs/apk/uat/composeApp-uat.apk` (application id `com.tazzzo.app.uat`, label
"Tazzzo UAT", side by side with the production app).

**Signing.** The `uat` build type is signed with the machine's **debug keystore** (`~/.android/debug.keystore`, created by
the Android tooling on first use). That is acceptable only for **internal UAT installs**: a debug-signed APK must never go
to a store or to customers, and different machines produce different signatures (an update over an APK signed on another
machine needs an uninstall first). The production `release` build type is untouched: it has no signing configuration in this
repository. No key, password or keystore is added; CI's `secrets` job fails if one is committed.

CI builds this APK on every PR (job `uat-android`) and uploads it as the artifact `tazzzo-uat-apk-debug-signed` (14 days).

Production, as before: `./gradlew :composeApp:assembleRelease` (no `tazzzo.*` input) produces the production binary.

## iOS

The Xcode "Compile Kotlin Framework" script phase runs `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`. Gradle
reads `TAZZZO_ENV` / `TAZZZO_API_BASE` from the environment, and `xcodebuild` exports build settings given on its command
line to that script, so no Xcode project edit is needed:

```bash
# UAT, unsigned archive (compile/link check, as the CI iOS release job does for production)
xcodebuild archive -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
  -destination 'generic/platform=iOS' -archivePath build/Tazzzo-UAT.xcarchive \
  TAZZZO_ENV=uat PRODUCT_BUNDLE_IDENTIFIER=com.tazzzo.app.uat \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=""
# final hostname: add  TAZZZO_API_BASE=https://api-uat.example.org
```

- `-configuration Release` is what makes it a non-debug binary (`Platform.isDebugBinary` is false). Without `TAZZZO_ENV=uat`
  the same command produces the production binary.
- `PRODUCT_BUNDLE_IDENTIFIER=com.tazzzo.app.uat` is the suggested separate bundle id so a UAT build can sit next to
  production. The home-screen name stays "Tazzzo" because `Info.plist` holds a literal `CFBundleDisplayName`; the UAT build is
  recognised by the `· UAT` marker in Profile and About.
- Gradle caches by the generated constant: switching `TAZZZO_ENV` regenerates it and rebuilds the framework.
- **NOT AVAILABLE: an installable iOS UAT build.** Installing on a device or TestFlight needs an Apple Developer account,
  a development/ad-hoc/distribution certificate and a provisioning profile for the UAT bundle id. None exists here, and none
  is added. Until then iOS UAT is verified only as far as compiling (CI `macos` job, step "UAT build target compiles for
  iOS": `TAZZZO_ENV=uat` + `compileKotlinIosSimulatorArm64`, plus a generated-file check) and by the shared logic tests.
  A full UAT archive (command above) was not added to CI because it would repeat the 25-minute release link; run it
  manually when needed.

## Network security

- Android: `android:usesCleartextTraffic="false"` in the manifest (all variants) and no network-security-config exception;
  CI inspects the built UAT APK and fails if cleartext is not disabled.
- iOS: no `NSAppTransportSecurity` exception exists in `Info.plist`, so App Transport Security's https requirement stays on.
- The UAT address is https-only by construction (build guard and runtime guard); `http://`, IP literals, `localhost`, user
  info, paths, queries and the production host are refused.

## What was not verified

- Any Android or iOS compile, the UAT APK, and the CI jobs are verified only by the CI run on the PR head.
- Installing and running a UAT build on a device or emulator against the real UAT API: NOT RUN (the UAT backend host
  `api-uat.tazzzo.com` is taken from the change request; its existence, certificate and CORS-independent behaviour are not
  checked here).
- The Xcode `TAZZZO_ENV` pass-through for a real `xcodebuild archive` on macOS: relied on from Xcode's documented behaviour of
  exporting build settings to run-script phases; the CI step proves the Gradle side only.
- Android `lintVital` on the `uat` build type is exercised by `assembleUat` in CI; any pre-existing lint finding on a
  non-debuggable build would show there first.
