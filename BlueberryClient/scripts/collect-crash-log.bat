@echo off
REM Installs the debug APK, launches the app, captures recent logs, and extracts fatal exceptions.

pushd %~dp0\..

set "APK=%cd%\app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" (
  echo APK not found at %APK% - build first with build-apk.bat
  popd
  pause
  exit /b 1
)

where adb >nul 2>&1
if errorlevel 1 (
  echo adb not found on PATH. Ensure platform-tools are installed.
  popd
  pause
  exit /b 1
)

echo Clearing device logs...
adb logcat -c

echo Installing APK...
adb install -r "%APK%"
if errorlevel 1 (
  echo adb install failed.
  popd
  pause
  exit /b 1
)

echo Launching app (com.blueberry.client/.ui.LauncherActivity)...
adb shell am start -n com.blueberry.client/.ui.LauncherActivity

echo Waiting 8 seconds for app to start/crash...
timeout /t 8 /nobreak >nul

set "OUT=%TEMP%\blueberry_log.txt"
echo Dumping logs to %OUT% ...
adb logcat -d > "%OUT%"

echo Extracting fatal exceptions to %OUT%.fatal.txt
findstr /C:"FATAL EXCEPTION" "%OUT%" > "%OUT%.fatal.txt" || (echo No fatal exceptions found.)

echo Logs saved to: %OUT%
echo Fatal lines (if any) saved to: %OUT%.fatal.txt
popd
pause
