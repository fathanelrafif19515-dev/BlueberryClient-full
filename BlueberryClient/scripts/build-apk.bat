@echo off
REM Build the Android APK without Android Studio
pushd %~dp0\..

if exist gradlew.bat (
  echo Using project Gradle wrapper (gradlew.bat)
  call gradlew.bat assembleDebug
) else (
  echo Using system Gradle
  gradle assembleDebug
)

if errorlevel 1 (
  echo Build failed.
  popd
  pause
  exit /b 1
)

echo Build succeeded. APKs can be found under app\build\outputs\apk\
popd
pause
