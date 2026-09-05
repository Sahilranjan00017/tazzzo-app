# Testing Tazzzo

## Three kinds of failure — keep them apart
- **App failure**: a test fails and the behaviour is wrong on device.
- **Test failure**: the assertion or driving strategy is wrong (e.g. `performScrollTo`
  on a lazy grid; a matcher hitting two nodes).
- **Environment failure**: the emulator was OOM-killed or CPU-starved. Symptom:
  a *different* test times out each run while every test passes alone.

Never widen a timeout to make an environment failure disappear. A long timeout
hides a real hang.

## Unit (pure logic, fast, no device)
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :composeApp:iosSimulatorArm64Test
```
Parse `composeApp/build/test-results/iosSimulatorArm64Test/*.xml`; a green build
alone does not prove tests ran.

## On-device — SMOKE (≈1 min, run after every change)
Semantics contract + the two highest-value journeys.
```bash
./gradlew :composeApp:assembleDebug :composeApp:assembleDebugAndroidTest && ./gradlew --stop
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb install -r composeApp/build/outputs/apk/androidTest/debug/composeApp-debug-androidTest.apk
adb shell pm clear com.tazzzo.app
adb shell am instrument -w -r \
  -e class com.tazzzo.app.InteractionSemanticsTest,com.tazzzo.app.CartRowRegressionTest \
  com.tazzzo.app.test/androidx.test.runner.AndroidJUnitRunner
```

## On-device — REGRESSION (≈6 min, run before a commit that touches navigation/cart)
```bash
adb shell pm clear com.tazzzo.app
adb shell am instrument -w -r com.tazzzo.app.test/androidx.test.runner.AndroidJUnitRunner
```

## Environment rules learned the hard way (8 GB host)
- **Stop the Gradle daemon before instrumenting** (`./gradlew --stop`). Daemon +
  emulator together is what swaps the machine.
- Gradle heap is 2 GB (`gradle.properties`); do not raise it to "fix" slowness.
- AVD `tazzzo`: 1536 MB RAM, 2 cores, software GL. Do not add cores.
- Docker Desktop reserves ~3.8 GB for its VM while running. Quit it before a
  regression run if the suite is flaking.
- Launch the emulator with `-no-snapshot-load -no-boot-anim -no-audio`.
- If `adb devices` is empty mid-run, the emulator was OOM-killed: that is an
  environment failure, not a test result.
