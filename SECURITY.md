# Security Policy

## Supported versions

Until Tracky reaches a stable 1.0 release, security fixes are applied to the
latest release and the `main` branch. Older builds may not receive fixes.

## Reporting a vulnerability

Please do not disclose suspected vulnerabilities in public issues, pull
requests, discussions, or chat logs.

Use the repository's **Security** tab and select **Report a vulnerability** to
open a private security advisory. Include:

- the affected version or commit;
- device model and Android version when relevant;
- clear reproduction steps or a minimal proof of concept;
- the expected and observed behavior;
- the potential impact and any suggested mitigation.

If private vulnerability reporting is unavailable, contact the maintainer
privately through a verified contact method on their GitHub profile. Do not
send live credentials, signing keys, precise personal locations, or data from
third-party devices. Redact logs before attaching them.

We aim to acknowledge complete reports within seven days. A fix and disclosure
timeline will depend on severity and reproducibility. Please allow reasonable
time for remediation before publishing details.

## Sensitive areas

Reports are especially valuable when they involve Bluetooth permissions and
scanning, background execution, device identifiers, proximity automations,
location-derived information, local data storage, exported Android components,
or dependency and build-chain integrity.

## Secrets and release signing

The repository must never contain API tokens, account credentials, production
configuration, private keys, or Android signing material. The CI workflow only
produces a debug APK and does not perform release signing.

Release keystores must be generated and stored outside the repository. If a
signed-release workflow is introduced later, credentials must come from
protected repository or environment secrets, use least-privilege access, and
never be printed to logs or embedded as literal values in workflow files.

If a secret is committed, revoke or rotate it immediately. Removing it from the
latest commit is not sufficient because it remains in Git history.
