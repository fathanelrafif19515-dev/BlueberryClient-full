@echo off
REM Install the debug APK to a connected Android device via ADB
pushd %~dp0\..

where adb >nul 2>&1
if errorlevel 1 (
  echo adb not found on PATH. Ensure platform-tools are installed and adb is on PATH.
  popd
  pause
  exit /b 1
)

echo Checking for connected devices...
adb devices

echo Looking for APK...
set APK_PATH=%cd%\app\build\outputs\apk\debug\app-debug.apk
if not exist "%APK_PATH%" (
  echo APK not found at %APK_PATH%.
  echo Run build-apk.bat first.
  popd
  pause
  exit /b 1
)

echo Installing %APK_PATH% to device (replacing existing app)...
adb install -r "%APK_PATH%"

popd
pause
