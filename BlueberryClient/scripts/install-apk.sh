#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found. Ensure Android platform-tools are installed and on PATH."
  exit 1
fi

echo "Connected devices:"
adb devices

APK_PATH="$(pwd)/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
  echo "APK not found at $APK_PATH. Run build-apk.sh first."
  exit 1
fi

echo "Installing $APK_PATH to device..."
adb install -r "$APK_PATH"
