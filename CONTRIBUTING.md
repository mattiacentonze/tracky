# Contributing to Tracky

Thank you for helping improve Tracky. Changes should remain focused, reviewable,
and safe for people using the app around real Bluetooth devices.

## Development setup

Install:

- Android Studio with the Android SDK for API 36;
- JDK 21;
- an Android device or emulator supported by the app's minimum SDK.

Use the committed Gradle Wrapper rather than a system Gradle installation:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written below `app/build/outputs/apk/debug/`. Bluetooth behavior
must be verified on physical hardware; an emulator or UI preview cannot prove
radio, ranging, permission, or background-execution behavior.

## Making a change

1. Create a short-lived branch from an up-to-date `main`.
2. Keep each change narrow and avoid unrelated formatting rewrites.
3. Add or update tests for changed behavior.
4. Run unit tests, Android lint, and a debug build locally.
5. Open a pull request that explains the intent, validation performed, user or
   privacy impact, and screenshots for visible UI changes.

Pull requests must pass Android CI. Reviews should pay particular attention to
runtime permissions, background work, exported components, device identifiers,
location inference, data retention, and actions that contact third-party apps.

## Privacy and test data

Do not commit scans, logs, screenshots, addresses, coordinates, device names,
Bluetooth identifiers, or automation payloads that identify real people or
devices. Use synthetic fixtures and redact diagnostic output.

## Credentials and local configuration

Never commit secrets, signing files, `local.properties`, environment files, or
service-account credentials. If local development requires configuration, keep
it in an ignored file and document only placeholder names in a safe example.
Assume any credential exposed in Git history is compromised and rotate it.

## Release signing

Repository CI intentionally builds an unsigned/debug artifact only. Production
release signing is a separate maintainer operation:

1. keep the keystore outside the repository and back it up securely;
2. supply aliases and passwords through local, ignored configuration or through
   protected GitHub environment secrets in a future reviewed release workflow;
3. never hard-code signing values in Kotlin, Gradle, shell scripts, or YAML;
4. verify the resulting APK or App Bundle signature before distribution.

Do not add automated publishing or release signing in a feature pull request
without an explicit security review.
