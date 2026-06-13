@echo off
setlocal enabledelayedexpansion

REM Check if java is on PATH
where java >nul 2>nul
if %errorlevel% neq 0 (
    echo Error: java command not found on PATH.
    echo Please install JDK 25 and make sure it is added to your PATH.
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
    echo Error: JDK 25 is required to run ChucK-Java, but version !major! was found.
    echo Please install JDK 25 and configure your PATH.
    exit /b 1
)

REM Run ChucK-Java IDE with necessary JVM flags for JDK 25
REM Required: --enable-preview, --add-modules jdk.incubator.vector, --enable-native-access=ALL-UNNAMED

set JAR=chuck-ide\target\chuck-ide-1.0-SNAPSHOT-shaded.jar

if not exist %JAR% (
    echo Shaded JAR not found. Building project...
    call mvn install -DskipTests
)

REM Handle path adjustments for examples and examples_dsl
set ARGS=
:loop
if "%~1"=="" goto endloop
set ARG=%~1
if "%ARG:~0,9%"=="examples/" (
    set ARG=chuck-samples/src/main/resources/%ARG%
) else if "%ARG:~0,13%"=="examples_dsl/" (
    for %%i in ("%ARG%") do set FILENAME=%%~nxi
    set ARG=chuck-samples/src/main/java/org/chuck/samples/dsl/%FILENAME%
)
set ARGS=%ARGS% %ARG%
shift
goto loop
:endloop

java --enable-preview --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -jar %JAR% %ARGS%
