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
    if [[ $arg == examples/* ]]; then
        ARGS+=("chuck-samples/src/main/resources/$arg")
    elif [[ $arg == examples_dsl/* ]]; then
        # Map examples_dsl/X.java to the new location in chuck-samples
        filename=$(basename "$arg")
        ARGS+=("chuck-samples/src/main/java/org/chuck/samples/dsl/$filename")
    else
        ARGS+=("$arg")
    fi
done

java --enable-preview --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -jar "$JAR" "${ARGS[@]}"
