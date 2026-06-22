#!/bin/bash
# Find JDK 27+ in path or system paths (EA builds like "27-ea" are accepted).
JAVA_BIN=""

# Extract the major version from a `java -version` line (handles 27, 27-ea, 27.0.1).
java_major() { "$1" -version 2>&1 | head -n 1 | sed -E 's/.*version "([0-9]+).*/\1/'; }

if command -v java >/dev/null 2>&1; then
    JAVA_VERSION=$(java_major java)
    if [ "$JAVA_VERSION" -ge 27 ] 2>/dev/null; then
        JAVA_BIN="java"
    fi
fi

if [ -z "$JAVA_BIN" ] && [ -x /usr/libexec/java_home ]; then
    JAVA_HOME_27=$(/usr/libexec/java_home -v 27 2>/dev/null)
    if [ -n "$JAVA_HOME_27" ]; then
        VERSION_CHECK=$(java_major "$JAVA_HOME_27/bin/java")
        if [ "$VERSION_CHECK" -ge 27 ] 2>/dev/null; then
            JAVA_BIN="$JAVA_HOME_27/bin/java"
        fi
    fi
fi

if [ -z "$JAVA_BIN" ]; then
    echo "Error: JDK 27 is required to run ChucK-Java but was not found."
    echo "Please install JDK 27 (e.g. via SDKMAN! 'sdk install java 27.ea.25-open' or Homebrew)."
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

"$JAVA_BIN" -XX:+UseZGC --enable-preview --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -jar "$JAR" "${ARGS[@]}"
