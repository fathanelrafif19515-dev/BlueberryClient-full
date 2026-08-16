# Blueberry Client — Android APK

> **PC users:** see the main [README](../README.md) — double-click `SETUP.bat` then `START.bat` at the project root.

## Build APK (Android Studio)

1. Install [Android Studio](https://developer.android.com/studio)
2. Open the `BlueberryClient` folder
3. Wait for Gradle sync
4. Build → Build APK
5. Install `app/build/outputs/apk/debug/app-debug.apk` on your phone

## Connect to server

1. On PC: run `START.bat` (from project root)
2. Copy the **Android phone URL** from the PC app (e.g. `ws://192.168.1.100:9999/`)
3. On phone: open Blueberry → paste URL → Connect
4. Grant overlay + microphone permissions
5. Start Overlay → Launch Minecraft

Phone and PC must be on the **same Wi-Fi**.

## Features

- HUD overlay (FPS, coords, ping, etc.)
- Replay recording
- Proximity voice chat
- Module toggles (ClickGUI)
