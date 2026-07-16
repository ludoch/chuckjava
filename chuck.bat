@echo off
setlocal enabledelayedexpansion

REM Check if java is on PATH
where java >nul 2>nul
if %errorlevel% neq 0 (
    echo Error: java command not found on PATH.
    echo Please install JDK 27 and make sure it is added to your PATH.
    exit /b 1
)

REM Verify java version is 25
set major=0
for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set val=%%g
    set val=!val:"=!
    for /f "delims=." %%a in ("!val!") do set major=%%a
)

if "!major!" neq "25" (
    echo Error: JDK 27 is required to run ChucK-Java, but version !major! was found.
    echo Please install JDK 27 and configure your PATH.
    exit /b 1
)

java --enable-preview --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -jar "%~dp0chuck-cli/target/chuck-cli-1.0-SNAPSHOT-shaded.jar" %*
