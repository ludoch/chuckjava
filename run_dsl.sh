#!/bin/bash
# Helper to run ChucK-Java DSL examples without manual compilation

JAR="chuck-cli/target/chuck-cli-1.0-SNAPSHOT-shaded.jar"

if [ -z "$1" ]; then
    echo "Usage: ./run_dsl.sh examples_dsl/SineDSL.java"
    echo "   or: ./run_dsl.sh --machine [dir] (Starts the hot-reloading Java Machine)"
    exit 1
fi

# 1. Build if jar is missing
if [ ! -f "$JAR" ]; then
    echo "Building modules..."
    mvn install -DskipTests
fi

# 2. Handle Machine Mode
if [ "$1" == "--machine" ]; then
    shift
    java --enable-preview \
         --add-modules jdk.incubator.vector \
         --enable-native-access=ALL-UNNAMED \
         -cp "$JAR" \
         org.chuck.core.JavaMachine "$@"
    exit 0
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

# 3. Run with modern JVM flags
java --enable-preview \
     --add-modules jdk.incubator.vector \
     --enable-native-access=ALL-UNNAMED \
     -cp "$JAR" \
     "${ARGS[@]}"
