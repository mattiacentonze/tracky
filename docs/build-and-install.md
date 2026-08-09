# Build And Install

## Requirements

- JDK 21.
- Android SDK platform 36.
- Android SDK build tools and platform tools (`adb`).
- An Android 12 or newer device/emulator; a physical BLE-capable phone is required for meaningful discovery and Finder testing.

Confirm the local tools before building:

```bash
java -version
adb version
adb devices
```

## Local Verification

Run the same JVM checks and APK build used by CI:

```bash
./gradlew --no-daemon --stacktrace --continue \
  testDebugUnitTest \
  lintDebug \
  assembleDebug
```

Run instrumented UI tests separately when an API-compatible device or emulator is connected:

```bash
./gradlew connectedDebugAndroidTest
```

Instrumented tests cover the Devices start destination, bottom navigation, Finder rename persistence for a seeded saved device, the automation builder, and stored-rule rendering. Live BLE discovery, Finder accuracy, background delivery, and accessory-specific GATT behavior still require a physical phone and tracker.

## APK Output And Installation

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install and launch it with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.aloneagle.tracky.debug/com.aloneagle.tracky.MainActivity
```

Alternatively:

```bash
./gradlew installDebug
```

Application IDs:

- base/release: `com.aloneagle.tracky`
- debug: `com.aloneagle.tracky.debug`

## GitHub Actions CI

`.github/workflows/android.yml` runs on pushes to `main`, pull requests, and manual dispatch. The workflow:

1. checks out the repository with persisted credentials disabled;
2. installs Temurin JDK 21;
3. validates the Gradle wrapper and enables dependency caching;
4. runs `testDebugUnitTest`, `lintDebug`, and `assembleDebug`;
5. verifies the APK signature and writes a portable SHA-256 checksum;
6. uploads the debug APK and checksum for 30 days;
7. uploads test, lint, and build reports for 14 days.

Instrumented tests are intentionally not part of the hosted CI job because it does not provision a BLE-capable Android device. A change is build-verified only after the GitHub Actions job is green; the presence of a workflow file alone is not proof of a passing build.

The CI artifact is a fully native, installable debug build and can scan real BLE
advertisements on a physical phone after Android permissions are granted. It is
signed with a CI-generated debug key, not a long-lived production key; a later CI
build may therefore require uninstalling the previous debug build before installation.

## Troubleshooting

If `adb` cannot see the device:

```bash
adb kill-server
adb start-server
adb wait-for-device
adb devices
```

If Android reports an unknown API level or `ddmlib` times out, restart `adb`, unlock the emulator/device, and retry `connectedDebugAndroidTest`. Direct APK installation can verify packaging and launch, but it does not replace the test task.

An emulator can verify navigation, Room persistence, dialogs, and permission UI. It cannot provide representative BLE RSSI, UWB/direction capability, Nut ring behavior, or real background radio conditions.
