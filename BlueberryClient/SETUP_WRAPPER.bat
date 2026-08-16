@echo off
REM Setup Gradle Wrapper
REM This script creates minimal Gradle wrapper files for offline builds

setlocal enabledelayedexpansion
cd /d "%~dp0\BlueberryClient"

echo.
echo ============================================
echo Blueberry Client - Gradle Wrapper Setup
echo ============================================
echo.

REM Create gradle/wrapper directory
if not exist gradle mkdir gradle
if not exist gradle\wrapper mkdir gradle\wrapper

echo Step 1: Creating gradle-wrapper.properties...
(
echo distributionBase=GRADLE_USER_HOME
echo distributionPath=wrapper/dists
echo distributionUrl=https\://services.gradle.org/distributions/gradle-7.4.2-all.zip
echo zipStoreBase=GRADLE_USER_HOME
echo zipStorePath=wrapper/dists
) > gradle\wrapper\gradle-wrapper.properties

if exist gradle\wrapper\gradle-wrapper.properties (
    echo [OK] gradle-wrapper.properties created
) else (
    echo [ERROR] Failed to create gradle-wrapper.properties
    exit /b 1
)

echo.
echo Step 2: Downloading gradle-wrapper.jar (this may take a minute)...
echo.

powershell -Command "^
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; ^
$url = 'https://services.gradle.org/distributions/gradle-7.4.2-all.zip'; ^
$tmpZip = '%TEMP%\gradle-7.4.2.zip'; ^
$wrapperJar = '%CD%\gradle\wrapper\gradle-wrapper.jar'; ^
try { ^
  Write-Host 'Downloading gradle 7.4.2...'; ^
  Invoke-WebRequest -Uri $url -OutFile $tmpZip -UseBasicParsing; ^
  Write-Host 'Extracting gradle-wrapper.jar...'; ^
  Add-Type -AssemblyName System.IO.Compression.FileSystem; ^
  $zip = [IO.Compression.ZipFile]::OpenRead($tmpZip); ^
  foreach ($entry in $zip.Entries) { ^
    if ($entry.FullName -match 'gradle-7.4.2/lib/gradle-wrapper-.*\.jar$') { ^
      [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $wrapperJar, $true); ^
      break; ^
    } ^
  } ^
  $zip.Dispose(); ^
  Remove-Item $tmpZip -Force; ^
  Write-Host '[OK] gradle-wrapper.jar extracted'; ^
} catch { ^
  Write-Host '[ERROR]' $_.Exception.Message; ^
  exit 1; ^
}^
"

if not exist gradle\wrapper\gradle-wrapper.jar (
    echo [ERROR] Failed to download/extract gradle-wrapper.jar
    echo.
    echo MANUAL FALLBACK:
    echo 1. Download: https://services.gradle.org/distributions/gradle-7.4.2-all.zip
    echo 2. Extract gradle-7.4.2/lib/gradle-wrapper-*.jar to: !CD!\gradle\wrapper\gradle-wrapper.jar
    echo 3. Then run: gradlew.bat assembleDebug
    echo.
    exit /b 1
)

echo.
echo Step 3: Creating gradlew.bat...
(
echo @echo off
echo setlocal enabledelayedexpansion
echo set DIRNAME=%%~dp0
echo set APP_HOME=%%DIRNAME:~0,-1%%
echo if not exist "%%APP_HOME%%\gradle\wrapper\gradle-wrapper.jar" (
echo   echo ERROR: gradle-wrapper.jar not found
echo   exit /b 1
echo ^)
echo if not defined JAVA_HOME (
echo   echo ERROR: JAVA_HOME not set
echo   exit /b 1
echo ^)
echo "%%JAVA_HOME%%\bin\java.exe" -Xmx1024m -Xms256m -classpath "%%APP_HOME%%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %%*
) > gradlew.bat

if exist gradlew.bat (
    echo [OK] gradlew.bat created
) else (
    echo [ERROR] Failed to create gradlew.bat
    exit /b 1
)

echo.
echo ============================================
echo Gradle Wrapper Setup Complete!
echo ============================================
echo.
echo Next: Run this command to build the APK
echo   gradlew.bat assembleDebug
echo.
echo Then install with:
echo   adb install -r app/build/outputs/apk/debug/app-debug.apk
echo.

endlocal