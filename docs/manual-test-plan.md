# Manual Test Plan

## Test Matrix

Use both environments:

- **Emulator:** install/launch, permissions UI, Devices start destination, bottom navigation, Automations UI, Settings, Room persistence, and dialogs.
- **Physical Android phone:** all emulator checks plus BLE discovery, Finder behavior, foreground monitoring, action delivery, and GATT/Nut validation.

Record the app version, commit SHA, phone model, Android version, tracker model, and whether battery optimization is enabled before testing.

## Build And Install Gate

1. Run:

   ```bash
   ./gradlew --no-daemon --stacktrace --continue \
     testDebugUnitTest \
     lintDebug \
     assembleDebug
   ```

2. Install:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. Launch:

   ```bash
   adb shell am start -n com.aloneagle.tracky.debug/com.aloneagle.tracky.MainActivity
   ```

Do not continue release verification if unit tests, lint, assembly, installation, or launch fails.

## Emulator Smoke Test

1. Confirm **Devices** is the first selected destination and `Find a device` is visible.
2. Deny and then grant nearby-device permission; confirm the explanation and rescan controls behave correctly.
3. Open **Automations**:
   - with no saved device, confirm `Save a device first`;
   - with seeded/test data, open `New automation`;
   - confirm enter/leave, 1–20 m range, notification, sound, vibration, Wi-Fi panel, WhatsApp draft, and Telegram draft choices.
4. Navigate to **Settings** and confirm permission state, build version, BLE limits, and privacy text.
5. Return to **Devices** and confirm navigation state is stable.
6. Run `./gradlew connectedDebugAndroidTest` and retain the report.

An emulator result is not evidence that BLE distance, Finder trend, UWB direction, background scanning, or accessory actions work on hardware.

## Physical Device Setup

1. Enable Bluetooth and unlock the phone.
2. Grant nearby-device and notification permissions. Grant location only if last-seen phone location is being tested.
3. Keep the phone off battery saver for the baseline run; test restricted battery behavior separately.
4. Wake the trackers and place them at known positions.
5. Start with sound at a safe level and review Do Not Disturb/vibration settings.

## Devices And Naming

1. Open **Devices** and rescan.
2. Confirm every advertising tracker appears; note that some devices legitimately expose no friendly name.
3. Compare displayed addresses/names and RSSI updates with a trusted BLE scanner when possible.
4. Save a device, assign a friendly name, leave the screen, and confirm the name remains after returning and after app restart.
5. Stop the tracker's advertisements and confirm the saved card remains under the not-currently-seen heading without being counted as in range.
6. Confirm `Details` opens stored metadata and `Find` opens Finder for a saved device, including from the not-currently-seen card.
7. Confirm unknown or unsaved devices cannot accidentally open a saved device's Finder.

## Finder

1. Place one saved tracker several metres away and open **Find**.
2. Walk slowly toward it, pause, rotate the phone, put it behind the body, and then move away.
3. Confirm the approximate range, proximity band, RSSI, and trend update without freezing or oscillating on every packet.
4. Confirm the UI never claims compass direction or shows a direction arrow.
5. Toggle sound and haptics independently; verify feedback can be disabled and does not run continuously.
6. Background and return to Finder; confirm the visible foreground-service notification and resumed updates.
7. Leave Finder; confirm enabled background monitoring resumes rather than starting a competing scan.
8. Treat large metre errors as expected RSSI limitations, but record prolonged stale state, reversed trend, crashes, or failure to recover.

## Automations

Create separate test rules for enter and leave. Use a large, unobstructed space and repeat each crossing at least three times.

1. Confirm starting the monitor while already inside/outside establishes a baseline without immediately firing.
2. Hover near the selected boundary and confirm minor RSSI noise does not repeatedly trigger the rule.
3. Cross the boundary decisively and remain there long enough for repeated samples and dwell.
4. Confirm one action occurs, not one action per BLE packet.
5. Cross back and repeat within 60 seconds; confirm cooldown suppresses a duplicate trigger for the same rule.
6. Repeat after cooldown and confirm a new valid crossing can trigger.
7. For leave rules, power down or remove the tracker and confirm completed missing scan windows eventually establish outside state.
8. Disable and delete rules; confirm foreground monitoring stops when no other monitor/rule needs it.
9. Restart the application and confirm saved rules and enabled state persist.

### Action Checks

- **Notification:** one local notification with the configured text; denied notification permission must not crash the app.
- **Sound:** one notification sound, respecting volume and Do Not Disturb.
- **Vibration:** one finite pattern, respecting device settings.
- **Wi-Fi panel:** a notification appears; only tapping it opens Android Wi-Fi controls. Tracky must not toggle Wi-Fi silently.
- **WhatsApp draft:** a notification appears; tapping opens pre-filled content for review. Verify nothing is sent automatically.
- **Telegram draft:** same draft-only behavior; verify nothing is sent automatically.
- **Missing target app:** tapping a draft action must not cause Tracky itself to send or expose data through a cloud fallback. Record the Android handler behavior.

## Details, Diagnostics, And Nut Validation

1. Refresh each tracker from Details.
2. Confirm discovered services show characteristic properties such as Read, Write, Write No Response, Notify, and Indicate.
3. Confirm battery appears only when supported by an observed characteristic.
4. Try Ring and record `unsupported`, `unconfirmed`, acknowledged write without reaction, confirmed reaction, or failure.
5. Use Raw GATT Probe only with captured writable characteristics and known-safe payloads.
6. Export diagnostics after each attempt and verify scan, connection, discovery, read/write, and automation events are present.

## Background And Recovery

1. With a rule enabled, lock the phone and move the tracker across its boundary.
2. Repeat with the app removed from recents.
3. Toggle Bluetooth off/on and verify recovery without duplicate actions.
4. Reboot the phone and confirm the visible foreground monitor is restored after unlock when BLE permissions remain granted; record any OEM/background-policy delay.
5. Repeat with the phone's normal OEM battery policy and record delayed/missed triggers separately from foreground results.

## Pass Criteria

- CI-equivalent local tasks pass and the APK installs/launches.
- Devices is the start destination and navigation is stable.
- Finder is useful for closer/farther guidance without claiming direction or precise ranging.
- Automations persist, resist boundary noise, and trigger at most once per confirmed crossing/cooldown window.
- Wi-Fi and messaging actions always preserve explicit user confirmation.
- No supported Nut capability is claimed without real-device evidence.
