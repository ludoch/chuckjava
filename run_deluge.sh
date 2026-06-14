#!/bin/bash
# Find JDK 25 in path or system paths
JAVA_BIN=""

if command -v java >/dev/null 2>&1; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" = "25" ]; then
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
    echo "Error: JDK 25 is required to run Deluge but was not found."
    echo "Please install JDK 25."
    exit 1
fi

JAR="deluge/target/deluge-1.0-SNAPSHOT.jar"

if [ ! -f "$JAR" ]; then
    echo "Deluge JAR not found. Building project..."
    mvn install -DskipTests
fi

"$JAVA_BIN" -XX:+UseZGC --enable-preview --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Xlog:gc*:file=gc.log:time,level,tags -jar "$JAR" "$@"
