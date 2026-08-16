@echo off
REM Gradle wrapper batch script for Windows
setlocal enabledelayedexpansion
set DIRNAME=%~dp0
set APP_HOME=%DIRNAME:~0,-1%

if not defined JAVA_HOME (
  echo JAVA_HOME not set. Installing requires Java 11+.
  echo Download from: https://www.oracle.com/java/technologies/downloads/#java11
  exit /b 1
)

if not exist "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" (
  echo Gradle wrapper not found. Run SETUP_GRADLE_WRAPPER_MANUAL.bat first.
  exit /b 1
)

"%JAVA_HOME%\bin\java.exe" -Xmx1024m -Xms256m -classpath "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
