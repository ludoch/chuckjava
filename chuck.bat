@echo off
java --enable-preview --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -jar "%~dp0chuck-cli/target/chuck-cli-1.0-SNAPSHOT-shaded.jar" %*
