#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

# Provision JDK 27-ea per machine; sets JAVA_HOME + JAVA_EXEC.
# shellcheck source=scripts/ensure-jdk27.sh
. scripts/ensure-jdk27.sh

# Locate the fat jar: root directory inside a release zip, or chuck-ide/target/ inside a git repository.
if [ -f "chuck-ide.jar" ]; then
  JAR="chuck-ide.jar"
elif ls chuck-ide/target/chuck-ide-*.jar >/dev/null 2>&1; then
  JAR=$(ls chuck-ide/target/chuck-ide-*.jar | head -n 1)
else
  JAR="chuck-ide/target/chuck-ide-1.0-SNAPSHOT.jar"
fi

# Build the self-contained IDE fat jar if it's missing OR any source changed since it was built
# (so edits actually reach the launched app instead of reusing a stale jar).
if [ ! -f "$JAR" ] || [ -n "$(find chuck-core/src chuck-ide/src pom.xml -newer "$JAR" \( -name '*.java' -o -name pom.xml \) -print -quit 2>/dev/null)" ]; then
  echo "Building ChucK-Java IDE ($JAR missing or sources changed)..."
  if [ -f "./mvnw" ]; then
    ./mvnw -q clean package -DskipTests
  else
    mvn -q clean package -DskipTests
  fi
  if ls chuck-ide/target/chuck-ide-*.jar >/dev/null 2>&1; then
    JAR=$(ls chuck-ide/target/chuck-ide-*.jar | head -n 1)
  fi
fi

echo "Launching ChucK-Java IDE ($JAR)..."
case "$(uname -s)" in
  Linux*) UI_SCALE="${CHUCK_UI_SCALE:-2}" ;; # Crostini/X11: no DPI auto-detect
  *) UI_SCALE="${CHUCK_UI_SCALE:-}" ;;       # macOS auto-detects Retina; don't force
esac
SCALE_FLAGS=()
if [ -n "$UI_SCALE" ]; then
  SCALE_FLAGS=(-Dsun.java2d.uiScale="$UI_SCALE" -Dsun.java2d.uiScale.enabled=true)
fi

"$JAVA_EXEC" --enable-preview --enable-native-access=ALL-UNNAMED --add-modules jdk.incubator.vector \
  "${SCALE_FLAGS[@]}" \
  -Dawt.useSystemAAFontSettings=on -Dswing.aatext=true \
  -jar "$JAR" "$@"
