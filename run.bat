@echo off
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
