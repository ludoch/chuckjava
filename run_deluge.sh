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
    echo "Error: JDK 27 is required to run Deluge but was not found."
    echo "Please install JDK 27 (e.g. via SDKMAN! 'sdk install java 27.ea.25-open')."
    exit 1
fi

JAR="deluge/target/deluge-1.0-SNAPSHOT.jar"

if [ ! -f "$JAR" ]; then
    echo "Deluge JAR not found. Building project..."
    mvn install -DskipTests
fi

# Project Leyden AOT cache (JDK 27) — OPT-IN (DELUGE_AOT=1).
# Measured here: boot is already fast (first audio ~540ms, steady ~1.1s) thanks to compact object
# headers + ZGC, and the cache's biggest lever (archived module graph / CDS heap) is DISABLED
# because we launch with --enable-preview + the jdk.incubator.vector incubator module
# ("CDS heap data is disabled because archived full module graph is not used"). So the gain today is
# marginal and the JVM prints non-fatal [error][aot] lines. Re-evaluate once we can drop
# --enable-preview (preview APIs finalized). First opt-in run records the cache on a clean exit;
# later runs reuse it.
AOT_CACHE="deluge/target/deluge.aot"
AOT_FLAGS=()
if [ "$DELUGE_AOT" = "1" ]; then
    if [ -f "$AOT_CACHE" ] && [ "$AOT_CACHE" -nt "$JAR" ]; then
        AOT_FLAGS=(-XX:AOTCache="$AOT_CACHE")
    else
        echo "[AOT] No up-to-date AOT cache — this run will record one ($AOT_CACHE) on exit."
        AOT_FLAGS=(-XX:AOTCacheOutput="$AOT_CACHE")
    fi
fi

"$JAVA_BIN" -XX:+UseZGC "${AOT_FLAGS[@]}" --enable-preview --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Xlog:gc*:file=gc.log:time,level,tags -jar "$JAR" "$@"
