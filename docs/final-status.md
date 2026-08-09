# Tracky Current Status

## Implemented In The Current Code

- Android 12+ Kotlin/Compose application with Hilt, Room, a Gradle wrapper, and a versioned schema migration.
- Devices as the start destination, with nearby BLE discovery, best-effort device names, signal-based proximity, save/rename, details, Finder entry points, and an honest not-currently-seen state for saved devices.
- Finder for a selected saved device, with filtered RSSI, approximate range, proximity band, trend, recent-signal visual, optional sound/haptics, refresh, rename, and conservative ring support.
- Automations for enter/leave rules stored entirely on-device.
- Automation actions for notification, sound, vibration, Android's Wi-Fi panel, WhatsApp draft, and Telegram draft.
- Rule-state persistence with a silent initial baseline, hysteresis, repeated samples, minimum dwell, cooldown, stale-observation rejection, and missing-scan-window handling.
- Visible foreground monitoring with process/boot restoration, legacy out-of-range notification fallback, BLE/GATT diagnostics, service inventory, battery read when the standard service exists, and log export.
- Settings that expose permissions, build version, local-data behavior, and BLE/UWB limitations.
- Unit test sources for proximity estimation, automation evaluation, reconnect behavior, and protocol adapters.
- Instrumented test sources for the Devices start destination, navigation, Finder rename persistence, automation-builder options, and persisted automation rendering.
- GitHub Actions configuration for unit tests, lint, debug APK assembly, and artifact/report upload.

## Deliberate Product Limits

- Generic BLE RSSI cannot provide exact metres or a reliable direction. Environmental and hardware effects can dominate the estimate.
- No UWB or angle-of-arrival implementation exists. Tracky must not show a directional arrow unless compatible hardware and a validated ranging path are added.
- Wi-Fi cannot be enabled silently by a normal Android application. The automation opens the system panel after the user taps its notification.
- WhatsApp and Telegram automations prepare drafts only. Sending always requires the user and depends on a compatible installed application.
- Notifications, sound, vibration, and background scanning remain subject to permissions, system settings, Do Not Disturb, Bluetooth state, and OEM battery policy.
- There is no cloud backend, shared finding network, or remote action runner.

## Provisional Or Hardware-Dependent

- Nut ring/beep remains unconfirmed until real service UUIDs, writable characteristics, and payloads are validated on the target accessories.
- Battery is confirmed only when the standard BLE Battery Service is exposed; proprietary Nut behavior is unknown.
- Reconnect currently relies on the observed BLE identity. Address rotation may require a stronger advertisement fingerprint.
- Automation radii are based on RSSI-derived estimates. They require real-device calibration and should not be used for safety-critical actions.
- Background behavior must be checked on the intended phone model with its normal battery settings.

## Verification Required Before A Release

1. Obtain a green GitHub Actions `Test, lint, and build` job for the exact commit.
2. Run `connectedDebugAndroidTest` on a supported emulator/device.
3. Install the generated debug APK on the target Android phone.
4. Complete the real-device Devices → Finder flow with at least two accessories.
5. Verify every automation action and the enter/leave boundary cases in the manual test plan.
6. Confirm foreground monitoring recovery after leaving Finder, process recreation, Bluetooth off/on, and a device reboot.
7. Validate Nut GATT behavior before changing any capability from unconfirmed to supported.

## Evidence Policy

The workflow, test sources, and APK task are implemented, but documentation must not claim a current build or device test passed unless its command output or CI run is available for the same revision. Emulator navigation success also does not validate BLE ranging or accessory commands.
