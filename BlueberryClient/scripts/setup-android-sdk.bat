@echo off
REM Setup Android SDK components required to build the project

setlocal ENABLEDELAYEDEXPANSION
where sdkmanager >nul 2>&1
if errorlevel 1 goto NEED_SDK_SETUP
goto AFTER_SDK_CHECK

:NEED_SDK_SETUP
echo sdkmanager not found on PATH. Attempting to download command-line tools to install SDK.
if defined ANDROID_SDK_ROOT (
  set "SDK_ROOT=%ANDROID_SDK_ROOT%"
) else (
  set "SDK_ROOT=C:\Android\Sdk"
)

echo Using SDK root: !SDK_ROOT!
if not exist "!SDK_ROOT!" mkdir "!SDK_ROOT!"

REM If an SDK is already present with sdkmanager, use it directly
if exist "!SDK_ROOT!\cmdline-tools\latest\bin\sdkmanager.bat" (
  echo Found existing cmdline-tools at !SDK_ROOT!\cmdline-tools\latest
  set "PATH=!SDK_ROOT!\cmdline-tools\latest\bin;!PATH!"
  goto AFTER_SDK_CHECK
) else if exist "!SDK_ROOT!\tools\bin\sdkmanager.bat" (
  echo Found existing tools at !SDK_ROOT!\tools\bin
  set "PATH=!SDK_ROOT!\tools\bin;!PATH!"
  goto AFTER_SDK_CHECK
)

REM Check if user placed a ZIP at the SDK root (e.g., C:\android-sdk\cmdline-tools.zip)
if exist "!SDK_ROOT!\cmdline-tools.zip" (
  set "TMPZIP=!SDK_ROOT!\cmdline-tools.zip"
  echo Found ZIP at !TMPZIP!, will extract.
  goto EXTRACT_ZIP
)

REM Check common download locations (Downloads)
for %%F in ("%USERPROFILE%\Downloads\commandlinetools-win-*.zip") do if exist %%~fF (
  set "TMPZIP=%%~fF"
  echo Found downloaded ZIP at %%~fF, will extract.
  goto EXTRACT_ZIP
)

REM Attempt automatic download as a fallback
set "TMPZIP=%TEMP%\cmdline-tools.zip"
echo Attempting to download Android command-line tools...
powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; try { Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-latest.zip' -OutFile '%TMPZIP%'; exit 0 } catch { exit 1 }"
if errorlevel 1 (
  echo Automatic download failed.
  echo Opening browser to the Android command-line tools download page. Please download the Windows command-line tools and save as:
  echo   %TMPZIP%
  start "" "https://developer.android.com/studio#command-tools"
  pause
  if not exist "%TMPZIP%" (
    echo File not found: %TMPZIP%
    echo Aborting. You can set ANDROID_SDK_ROOT to an existing SDK and re-run this script.
    pause
    endlocal
    exit /b 1
  )
)

:EXTRACT_ZIP
echo Extracting command-line tools to !SDK_ROOT!\cmdline-tools\latest ...
if not exist "!SDK_ROOT!\cmdline-tools" mkdir "!SDK_ROOT!\cmdline-tools"
powershell -Command "Expand-Archive -Path '%TMPZIP%' -DestinationPath '!SDK_ROOT!\\cmdline-tools\\latest' -Force"
if errorlevel 1 (
  echo Extraction failed.
  pause
  endlocal
  exit /b 1
)

set "PATH=!SDK_ROOT!\cmdline-tools\latest\bin;!PATH!"

:AFTER_SDK_CHECK

echo Installing common SDK components (platform-tools, build-tools, platforms, cmdline-tools)...
sdkmanager "platform-tools" "platforms;android-33" "build-tools;33.0.2" "cmdline-tools;latest"

echo Accepting licenses...
echo y | sdkmanager --licenses

echo Done. Ensure %ANDROID_SDK_ROOT% is set to "%SDK_ROOT%" (or set it globally) and `adb` is on PATH.
pause
endlocal
