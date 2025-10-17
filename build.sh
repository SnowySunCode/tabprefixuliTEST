#!/bin/bash
set -e
PLUGIN_NAME="TabPrefixPlugin"
SRC_DIR="src"
BIN_DIR="bin"
LIBS_DIR="libs"
DIST_DIR="dist"
PLUGIN_YML="plugin.yml"

mkdir -p "$BIN_DIR" "$DIST_DIR"

CLASSPATH=""
for jar in "$LIBS_DIR"/*.jar; do
  CLASSPATH="$CLASSPATH:$jar"
done

echo "Classpath:$CLASSPATH"
find "$BIN_DIR" -type f -delete || true

# Compile with Java 15 --release, adjust if you use other JDK
javac --release 15 -encoding UTF-8 -cp "$CLASSPATH" -d "$BIN_DIR" $(find "$SRC_DIR" -name "*.java")

cp "$PLUGIN_YML" "$BIN_DIR/"

cd "$BIN_DIR"
jar cf "../$DIST_DIR/$PLUGIN_NAME.jar" .
cd ..

echo "Built: $DIST_DIR/$PLUGIN_NAME.jar"
