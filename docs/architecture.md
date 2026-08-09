# Tracky Architecture

## Overview

Tracky is a single-module Android application built with Kotlin, Jetpack Compose, Hilt, Room, and Android BLE APIs. Packages provide the boundaries that would otherwise be separate Gradle modules. The design keeps Bluetooth and Android effects outside the pure proximity-rule evaluator.

## Product Surfaces

- **Devices** is the start destination. It scans nearby BLE advertisements, shows the best available name and signal estimate, and lets the user save, rename, inspect, or find a device. Saved devices remain accessible in a clearly marked not-currently-seen section when they are not advertising.
- **Finder** is opened for a saved device. It shows filtered RSSI proximity, recent signal, and an improving/stable/weakening trend. Optional sound and haptic feedback can help while moving around.
- **Automations** stores on-device enter/leave rules for saved devices. A rule can request a notification, sound, vibration, the Android Wi-Fi panel, or a WhatsApp/Telegram draft.
- **Settings** reports permission state, build information, privacy behavior, and BLE limitations.
- Device details and diagnostics remain secondary screens for metadata, service discovery, battery reads, GATT validation, and log export.

## Layers

- `domain/model`
  - Tracker, observation, proximity, automation-rule, zone-state, transition, and action models.
- `domain/repository` and `domain/service`
  - Interfaces for tracker and automation persistence, scanning, GATT, monitoring, estimation, action dispatch, and location snapshots.
  - `ProximityRuleEvaluator` is a pure deterministic state machine with no Android dependencies.
- `data/local`
  - Room database, entities, DAOs, converters, and schema migrations for trackers, observations, BLE logs, and automation rules.
- `data/repository`
  - Repository implementations and `ExponentialProximityEstimator`.
- `ble/transport`
  - Android BLE scan callbacks and GATT sequencing.
- `ble/protocol`
  - Generic capability inference, conservative Nut candidate handling, and protocol selection.
- `service`
  - Foreground Finder/monitor coordination and out-of-range evaluation.
- `automation`
  - Android execution of domain action requests.
- `notifications` and `logging`
  - Foreground/alert notifications and Room-backed diagnostic export.
- `ui`
  - Compose screens, navigation, themes, and reusable components.

## BLE Data Flow

1. Devices starts a manual BLE scan after scan/connect permissions are granted.
2. Advertisements are rendered immediately; saving a device persists its resolved identity and latest observation. Saved devices without a current advertisement remain available for Finder and Details without being presented as currently nearby.
3. The repository filters RSSI and stores observations, service metadata, battery snapshots when available, and optional phone location.
4. Finder temporarily owns the scanner for its selected device so a simultaneous background scan does not distort the trend.
5. Leaving Finder restores monitoring for devices with an enabled monitor or automation.
6. GATT refresh and diagnostics perform service discovery and conservative reads/writes through the connection manager.

## Proximity And Automation Flow

1. The automation builder persists a rule and its evaluator state in Room.
2. Monitoring supplies smoothed distance estimates to enabled rules. A completed monitor window in which a device is absent supplies one missing observation, not one event per missing packet.
3. The first stable zone establishes a silent baseline. It does not fire an enter/leave action merely because monitoring started.
4. Enter is confirmed at the selected radius or closer. Leave is confirmed at `radius + hysteresis` or farther.
5. A new rule created by the UI requires three consistent samples, at least 2.5 seconds of dwell, and uses a 60-second cooldown. Its safety margin is the larger of 1 metre and 15% of the selected radius.
6. The evaluator state is stored before any returned event is dispatched, which prevents repeated actions while the device remains on one side of the boundary.
7. The Android action executor performs local effects or posts a notification containing the user-confirmed continuation.

## RSSI And Ranging Limits

RSSI is radio signal strength, not a distance sensor. Devices converts the latest advertisement into an immediate rough estimate so nearby items can be ranked. Finder and automations use the repository's filtered estimate: a base exponential factor of `0.25`, lower weight for large outliers, bounded individual changes, and hysteresis between proximity bands. Finder withholds approximate metres while confidence is low or the signal is considered lost; all displayed values are bounded to the UI's useful range.

Walls, bodies, pockets, antenna orientation, interference, transmit power, and different phone/accessory radios can change RSSI without a matching change in physical distance. The displayed metres and automation radii are therefore heuristics, not measurements or safety guarantees.

Tracky does not currently implement UWB ranging, angle-of-arrival, or direction finding. A direction arrow would require both compatible phone/accessory hardware and a validated ranging API; it cannot be inferred reliably from generic BLE RSSI.

## Android Action Boundaries

- Notifications, sound, and vibration are local actions and remain subject to notification permission, system volume, Do Not Disturb, and vibration settings.
- Android does not allow an ordinary application to silently toggle Wi-Fi. Tracky posts a notification; tapping it opens the system Wi-Fi panel for user confirmation.
- WhatsApp and Telegram actions create a notification that opens a pre-filled draft. Tracky never presses Send. The target application or compatible handler must be installed, and the user reviews the recipient and content.
- Tracker data and automation rules remain in the local Room database. There is no cloud execution service or crowd-tracking network.

## Reconnect And Monitoring Policy

- Finder and background monitoring use a visible foreground-service notification.
- Finder takes exclusive ownership of the BLE scanner and monitoring resumes afterward.
- A sticky service restores state after process recreation; the boot/package-replaced receiver requests restoration when Android permits it and BLE permissions remain granted.
- The legacy out-of-range notification is retained for monitored devices without an enabled automation.
- Background reliability still depends on Android permissions, Bluetooth state, OEM battery policy, and the accessory continuing to advertise.

## Why This Shape

- The single app module keeps bootstrap and CI simple.
- Pure rule evaluation is deterministic and unit-testable.
- Protocol adapters isolate unverified Nut behavior from generic BLE behavior.
- A foreground service makes long-running work visible to the user.
- Persisted rule state, dwell, hysteresis, and cooldown reduce boundary noise and duplicate actions.
- Logs and discovered GATT capabilities remain first-class because real accessory validation is still required.
