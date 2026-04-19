#!/bin/bash
# generate_cds.sh - Generate a CDS archive for ChucK-Java instant-on performance.

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

echo "Building project..."
mvn clean package -DskipTests -pl chuck-core,chuck-cli -am

JAR="chuck-cli/target/chuck-cli-1.0-SNAPSHOT-shaded.jar"

if [ ! -f "$JAR" ]; then
    echo "Error: Shaded JAR not found at $JAR"
    exit 1
fi

echo "Performing training run to collect loaded classes..."
# Run a simple script for a short duration in silent mode
java --enable-preview --add-modules jdk.incubator.vector \
     -XX:DumpLoadedClassList=chuck.classlist \
     -jar "$JAR" --silent --timeout:1 chuck-samples/src/main/resources/examples/basic/demo0.ck || true

echo "Dumping CDS archive..."
java --enable-preview --add-modules jdk.incubator.vector \
     -Xshare:dump \
     -XX:SharedClassListFile=chuck.classlist \
     -XX:SharedArchiveFile=chuck.jsa \
     -jar "$JAR"

echo "---------------------------------------------------"
echo "CDS archive generated: chuck.jsa"
echo "To use it for faster startup:"
echo "java -Xshare:on -XX:SharedArchiveFile=chuck.jsa --enable-preview --add-modules jdk.incubator.vector -jar $JAR ..."
echo "---------------------------------------------------"
