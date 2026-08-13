# Tracky

Tracky is a privacy-first Android app for discovering nearby Bluetooth devices,
renaming the ones you care about, following their proximity, and running local
enter/leave automations.

[Try the interactive preview](https://tracky-bluetooth-finder.aloneeagle.chatgpt.site)

## Product principles

- **Useful before flashy.** Nearby devices are ordered by signal strength and can
  be saved with a friendly local name.
- **No false precision.** Generic BLE uses filtered RSSI, proximity bands and a
  trend. Approximate metres appear only after stable readings. A direction arrow
  is reserved for a future compatible UWB/ranging implementation.
- **Safe automations.** Rules use hysteresis, repeated samples, minimum dwell and
  cooldowns before they fire.
- **Android-compliant actions.** Tracky can notify, play a sound, vibrate, or post
  an action that opens Wi-Fi controls or a pre-filled WhatsApp/Telegram draft.
  Android does not allow a normal app to silently toggle Wi-Fi or press Send.
- **Local by default.** Device aliases, observations and rules are stored on the
  phone. There is no Tracky account or tracking cloud.

## Current Android features

- one foreground BLE scan while Devices is visible, with Android 12+ permission
  handling and a stable five-second UI refresh;
- live broadcasts only: captured Android-paired devices first, then captured named
  and unnamed devices, with distance or name sorting inside each section;
- system Bluetooth enable consent followed by automatic paired-device loading and
  scanning when the user accepts;
- best-effort advertised/resolved names plus safe unknown-device fallback;
- local save and rename;
- dedicated finder with filtered signal, confidence-aware approximate distance,
  improving/stable/weakening trend, optional sound and haptics;
- Room-backed enter/leave automations with persistent evaluator state;
- notification, sound, vibration, Wi-Fi panel, WhatsApp draft and Telegram draft
  actions;
- one coordinated scanner pipeline, foreground monitoring notification,
  process/boot restoration and missed-scan handling;
- GATT service diagnostics, standard BLE battery reads and provisional Nut
  Findthing protocol adapter;
- bounded local BLE log and observation retention.

## Technical limits

Devices lists only advertisements received by the current scan. A paired, saved,
sleeping, Bluetooth Classic-only, or non-advertising accessory is intentionally
absent until Tracky receives a compatible BLE broadcast. The phone running Tracky does not list itself, and
Fast Pair devices remembered only in a cloud account may not be part of Android's
local paired-device set. Names may be missing, and privacy-address rotation can
make some unbonded devices appear new.

Android 13 and later do not allow a normal app to switch Bluetooth on or off
silently. Tracky uses Android's standard enable dialog; after one confirmation it
continues automatically without sending the user through Settings.

RSSI is affected by walls, bodies, pockets, antenna orientation and radio hardware.
It is good for proximity and movement trends, not guaranteed centimetre-level
ranging. Exact distance requires cooperating Bluetooth Channel Sounding hardware;
distance plus direction normally requires a compatible UWB phone and accessory.

Background BLE behavior and vendor-specific GATT commands must be validated on a
physical Android phone. The browser preview is intentionally a UX simulator.

Tracky requests Android's Nearby devices and precise location permissions for
complete Bluetooth discovery and RSSI proximity. It intentionally does not assert
`neverForLocation`, because Android can filter some BLE beacons in that mode.
Turning on GPS does not increase Bluetooth range or make RSSI distance more
accurate, so Tracky does not sample phone coordinates during scanning.

## Stack

- Kotlin, Coroutines and Flow
- Jetpack Compose / Material 3
- Hilt
- Room + KSP migrations
- WorkManager
- Android minSdk 31, target/compileSdk 36

The native source is under `app/`. The deployed simulator source is under
`preview/`.

## Build

Use JDK 21 and an Android SDK with platform 36 installed.

```bash
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The installable debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Every pull request runs the same tests, lint and APK build in GitHub Actions. A
green workflow verifies the APK signature, publishes a SHA-256 checksum and uploads
both files as temporary build artifacts. The debug APK is a real native build that
uses the phone's Bluetooth hardware; it is not the web simulator. Release signing
is deliberately excluded from source control and requires a maintainer-owned
keystore supplied through protected secrets.

## Repository safety

- Never commit `.env`, `local.properties`, signing properties, keystores, service
  accounts or exported personal BLE logs.
- Device addresses and location-bearing diagnostics should be redacted before
  sharing.
- Raw GATT probing is experimental and should only be used with hardware you own or
  are authorized to test.

See [SECURITY.md](SECURITY.md), [CONTRIBUTING.md](CONTRIBUTING.md), the
[architecture notes](docs/architecture.md), and the
[manual hardware test plan](docs/manual-test-plan.md).
