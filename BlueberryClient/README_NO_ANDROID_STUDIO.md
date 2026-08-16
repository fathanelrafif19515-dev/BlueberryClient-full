Build & Install without Android Studio

This guide shows how to build and install the Blueberry Android app using the command line only (no Android Studio required).

Prerequisites (one-time):
- Java JDK 17+ installed and `java` on PATH.
- Gradle installed and `gradle` on PATH (or add a Gradle wrapper to the project).
- Android SDK command-line tools installed and `sdkmanager`, `adb` available (set `ANDROID_SDK_ROOT`).

Quick steps (Windows):
1. Install required SDK components:
   - Run `BlueberryClient\\scripts\\setup-android-sdk.bat` and follow prompts.
2. Build an APK:
   - Run `BlueberryClient\\scripts\\build-apk.bat`.
3. Install to a connected phone via USB (enable USB debugging):
   - Run `BlueberryClient\\scripts\\install-apk.bat`.

Notes:
- The scripts use `gradle` if `gradlew` is not present. If you prefer a project-local Gradle wrapper, run `gradle wrapper` in `BlueberryClient` once and commit the generated wrapper files.
- For production release signing, set up a keystore and update the Gradle signing config. The scripts build a debug APK by default which is suitable for quick installs and testing.

More details are printed by each script when run.
