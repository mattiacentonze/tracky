# Tracky interactive preview

This directory contains the browser-based product simulator deployed at
[tracky-bluetooth-finder.aloneeagle.chatgpt.site](https://tracky-bluetooth-finder.aloneeagle.chatgpt.site).

It mirrors Tracky's main Android flows with deterministic demo data:

- nearby-device discovery and signal bands;
- a proximity finder with distance/trend simulation;
- local display-name editing;
- enter/leave automation creation and testing;
- sound and haptic feedback controls.

The preview does **not** claim to scan the visitor's Bluetooth radio. Browsers and
the hosted Work environment cannot reproduce Android background scanning, UWB,
vendor GATT behavior, or physical RF conditions. Those capabilities live in the
native app and require real hardware validation.

## Run locally

Requirements: Node.js 22.13 or newer, npm, and a Linux environment.

```bash
npm ci
npm run dev
```

Production checks:

```bash
npm run lint
npm test
```

The preview contains no credentials, analytics, account system, or remote data
store. Simulator state exists only for the current browser session.
