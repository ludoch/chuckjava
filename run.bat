@echo off
REM Run ChucK-Java IDE with necessary JVM flags for JDK 25
REM Required: --enable-preview, --add-modules jdk.incubator.vector, --enable-native-access=ALL-UNNAMED

java --enable-preview --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -jar target\chuck-java-1.0-SNAPSHOT.jar %*
