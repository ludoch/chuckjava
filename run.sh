#!/bin/bash
# Find JDK 25 in path or system paths
JAVA_BIN=""

if command -v java >/dev/null 2>&1; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" = "25" ]; then
        # Check if version check succeeded or falls back to "25"
        JAVA_BIN="java"
    fi
fi

if [ -z "$JAVA_BIN" ] && [ -x /usr/libexec/java_home ]; then
    JAVA_HOME_25=$(/usr/libexec/java_home -v 25 2>/dev/null)
    if [ -n "$JAVA_HOME_25" ]; then
        VERSION_CHECK=$("$JAVA_HOME_25/bin/java" -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [ "$VERSION_CHECK" = "25" ]; then
            JAVA_BIN="$JAVA_HOME_25/bin/java"
        fi
    fi
fi

if [ -z "$JAVA_BIN" ]; then
    echo "Error: JDK 25 is required to run ChucK-Java but was not found."
    echo "Please install JDK 25 (e.g. via SDKMAN! 'sdk install java 25-open' or Homebrew)."
    exit 1
fi

JAR="chuck-ide/target/chuck-ide-1.0-SNAPSHOT.jar"

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

"$JAVA_BIN" --enable-preview --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -jar "$JAR" "${ARGS[@]}"
