#!/bin/bash
# Run ChucK-Java IDE with necessary JVM flags for JDK 25
# Required: --enable-preview, --add-modules jdk.incubator.vector, --enable-native-access=ALL-UNNAMED

JAR="chuck-ide/target/chuck-ide-1.0-SNAPSHOT-shaded.jar"

if [ ! -f "$JAR" ]; then
    echo "Shaded JAR not found. Building project..."
    mvn install -DskipTests
fi

# Adjust paths if they start with examples/ or examples_dsl/
ARGS=()
for arg in "$@"; do
    if [[ $arg == examples/* ]] || [[ $arg == examples_dsl/* ]]; then
        ARGS+=("chuck-core/$arg")
    else
        ARGS+=("$arg")
    fi
done

java --enable-preview --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -jar "$JAR" "${ARGS[@]}"
