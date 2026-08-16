#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -x ./gradlew ]; then
  echo "Using project Gradle wrapper"
  ./gradlew assembleDebug
else
  echo "Using system gradle"
  gradle assembleDebug
fi

echo "Build finished. APKs are in app/build/outputs/apk/"
