@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

REM Provision JDK 27-ea per machine, then launch.
if exist "chuck-ide.jar" (
    set "JAR=chuck-ide.jar"
) else (
    for %%f in (chuck-ide\target\chuck-ide-*.jar) do set "JAR=%%f"
)

if not defined JAR set "JAR=chuck-ide\target\chuck-ide-1.0-SNAPSHOT.jar"

if exist jdk27\bin\java.exe (
    set "JAVA_EXEC=jdk27\bin\java.exe"
) else (
    java -version 2>&1 | findstr /C:"version \"27" >nul
    if !errorlevel! equ 0 (
        set "JAVA_EXEC=java"
    ) else (
        echo Java 27 is required but not found.
        echo Downloading OpenJDK 27 ^(early-access^) from Adoptium...

        set ARCH=x64
        if /I "%PROCESSOR_ARCHITECTURE%"=="ARM64" set ARCH=aarch64

        set "URL=https://api.adoptium.net/v3/binary/latest/27/ea/windows/!ARCH!/jdk/hotspot/normal/eclipse?project=jdk"
        echo Downloading JDK 27 for Windows ^(!ARCH!^)...
        powershell -Command "[System.Net.ServicePointManager]::SecurityProtocol=[System.Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '!URL!' -OutFile 'openjdk27.zip'"

        echo Extracting JDK 27...
        if exist jdk27_temp rd /s /q jdk27_temp
        mkdir jdk27_temp
        powershell -Command "Expand-Archive -Path 'openjdk27.zip' -DestinationPath 'jdk27_temp' -Force"
        for /d %%i in (jdk27_temp\*) do move "%%i" jdk27 >nul
        rd /s /q jdk27_temp
        del openjdk27.zip

        set "JAVA_EXEC=jdk27\bin\java.exe"
    )
)

if not exist "%JAR%" (
    echo %JAR% not found -- building it ^(first run^)...
    if exist "mvnw.cmd" (
        call mvnw.cmd -q clean package -DskipTests
    ) else (
        call mvn -q clean package -DskipTests
    )
    for %%f in (chuck-ide\target\chuck-ide-*.jar) do set "JAR=%%f"
)

echo Launching ChucK-Java IDE (%JAR%)...
set "SCALE_FLAGS="
if defined CHUCK_UI_SCALE set "SCALE_FLAGS=-Dsun.java2d.uiScale=%CHUCK_UI_SCALE% -Dsun.java2d.uiScale.enabled=true"
"!JAVA_EXEC!" --enable-preview --enable-native-access=ALL-UNNAMED --add-modules jdk.incubator.vector %SCALE_FLAGS% -Dawt.useSystemAAFontSettings=on -Dswing.aatext=true -jar "%JAR%" %*

pause
