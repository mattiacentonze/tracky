# Tracky privacy notice

_Last updated: 9 August 2026_

Tracky is designed to operate locally on the Android device. The current app has
no Tracky account, analytics SDK, advertising SDK, telemetry service, or cloud
backend.

## Data processed on the phone

Depending on the permissions and features you use, Tracky may process:

- nearby Bluetooth advertisements, device names, addresses, service identifiers,
  manufacturer data and signal strength;
- friendly names and proximity automations you create;
- timestamps, recent signal observations and diagnostic GATT events;
- the phone's approximate location at the time of a sighting, only when location
  permission is granted and that feature is used;
- notification, sound and vibration preferences.

This information is stored in the app's private local database. Android backup is
disabled for the application. Maintenance work bounds stored observations and
diagnostic logs.

## External apps and system panels

An automation can prepare an action for another app. Wi-Fi actions open Android's
system controls. WhatsApp and Telegram actions create a notification which, after
you tap it, opens a pre-filled draft. Tracky does not send personal messages or
change Wi-Fi state without your confirmation. The destination app's own privacy
terms apply after it is opened.

## Sharing and exports

Tracky does not upload data automatically. If you explicitly export and share a
diagnostic log, Android's share sheet sends the selected file to the destination
you choose. Logs can contain device identifiers; review and redact them first.

## Permissions

- Nearby devices: scan for and connect to supported Bluetooth devices.
- Notifications: display monitoring state, alerts and action prompts.
- Location: optional last-seen phone location when enabled.

You can revoke permissions or clear all app data in Android settings. Uninstalling
Tracky deletes its private local data, subject to Android device-management rules.

## Contact

For privacy or security questions, use the repository's private vulnerability
reporting path described in [SECURITY.md](SECURITY.md). Do not include live device
addresses, precise locations or other people's data in a public issue.
