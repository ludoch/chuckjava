#!/bin/bash
# Helper to run ChucK-Java DSL examples without manual compilation

if [ -z "$1" ]; then
    echo "Usage: ./run_dsl.sh examples_dsl/SineDSL.java"
    echo "   or: ./run_dsl.sh --machine [dir] (Starts the hot-reloading Java Machine)"
    exit 1
fi

# 1. Build if jar is missing
if [ ! -f target/chuck-java-1.0-SNAPSHOT.jar ]; then
    echo "Building core library..."
    mvn package -DskipTests
fi

# 2. Handle Machine Mode
if [ "$1" == "--machine" ]; then
    shift
    java --enable-preview \
         --add-modules jdk.incubator.vector \
         --enable-native-access=ALL-UNNAMED \
         -cp target/chuck-java-1.0-SNAPSHOT.jar \
         org.chuck.core.JavaMachine "$@"
    exit 0
fi

# 3. Run with modern JVM flags
java --enable-preview \
     --add-modules jdk.incubator.vector \
     --enable-native-access=ALL-UNNAMED \
     -cp target/chuck-java-1.0-SNAPSHOT.jar \
     "$@"
