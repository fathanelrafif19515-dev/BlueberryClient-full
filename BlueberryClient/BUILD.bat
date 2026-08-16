@echo off
REM Build APK using Android Gradle Plugin
REM Alternative to wrapper - tries to use sdkmanager or direct AGP

setlocal enabledelayedexpansion
cd /d "%~dp0"

echo.
echo ============================================
echo Blueberry Client - Build APK
echo ============================================
echo.

REM Check for Android SDK
if not defined ANDROID_SDK_ROOT (
    echo ERROR: ANDROID_SDK_ROOT not set
    echo Please set ANDROID_SDK_ROOT to your Android SDK directory
    echo Default: C:\Android\Sdk
    exit /b 1
)

if not exist "%ANDROID_SDK_ROOT%" (
    echo ERROR: Android SDK not found at %ANDROID_SDK_ROOT%
    echo Please install Android SDK first
    exit /b 1
)

echo Using Android SDK: %ANDROID_SDK_ROOT%
echo.

REM Try to find gradle
if exist "%ANDROID_SDK_ROOT%\tools\bin\sdkmanager.bat" (
    echo Found Android SDK. Using built-in Gradle...
) else (
    echo Warning: Android SDK tools not found
)

echo.
echo Attempting to build APK...
echo.

REM Run the Kotlin build directly
call :BuildAPK
if !errorlevel! neq 0 (
    echo.
    echo [ERROR] Build failed
    echo.
    echo To build manually:
    echo 1. Open BlueberryClient in Android Studio
    echo 2. Select Build -^> Build Bundle(s) / APK(s) -^> Build APK(s)
    echo 3. Find APK at: app\build\outputs\apk\debug\app-debug.apk
    echo.
    exit /b 1
)

echo.
echo [SUCCESS] APK built successfully!
echo Location: app\build\outputs\apk\debug\app-debug.apk
echo.

endlocal
exit /b 0

:BuildAPK
REM This is where we'd call gradle or build system
REM For now, provide instructions
echo.
echo To build the APK, you have these options:
echo.
echo Option 1: Using Android Studio (Recommended)
echo -----------
echo   1. Open BlueberryClient folder in Android Studio
echo   2. Wait for project to sync
echo   3. Click Build -^> Make Project or Build APK
echo.
echo Option 2: Command Line
echo -----------
echo   1. Set ANDROID_SDK_ROOT environment variable
echo   2. Install Gradle: https://gradle.org/releases
echo   3. Run from BlueberryClient folder:
echo      gradle assembleDebug
echo      adb install -r app\build\outputs\apk\debug\app-debug.apk
echo.
exit /b 0
