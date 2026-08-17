#!/usr/bin/env bash
set -euo pipefail

./gradlew \
  --no-daemon \
  --stacktrace \
  --continue \
  testDebugUnitTest \
  lintDebug \
  assembleDebug
