#!/bin/bash
set -e

PLUGIN_NAME="TabPrefixPlugin"
SRC_DIR="src"
BIN_DIR="bin"
LIBS_DIR="libs"
DIST_DIR="dist"
PLUGIN_YML="plugin.yml"

echo "📦 Building $PLUGIN_NAME..."

if [ ! -d "$SRC_DIR" ]; then echo "❌ src dir missing"; exit 1; fi
if [ ! -f "$PLUGIN_YML" ]; then echo "❌ plugin.yml missing"; exit 1; fi
if [ ! -d "$LIBS_DIR" ]; then echo "❌ libs dir missing"; exit 1; fi

mkdir -p "$BIN_DIR"
mkdir -p "$DIST_DIR"

rm -rf "$BIN_DIR"/*
rm -f "$DIST_DIR/$PLUGIN_NAME.jar"

# build classpath
CLASSPATH=""
for jar in "$LIBS_DIR"/*.jar; do
  CLASSPATH="$CLASSPATH:$jar"
done

echo "Classpath:$CLASSPATH"
find "$SRC_DIR" -name "*.java" > /tmp/sources.txt

javac -encoding UTF-8 -cp "$CLASSPATH" -d "$BIN_DIR" @/tmp/sources.txt
if [ $? -ne 0 ]; then echo "❌ javac failed"; exit 1; fi

cp "$PLUGIN_YML" "$BIN_DIR/"

cd "$BIN_DIR"
jar cf "../$DIST_DIR/$PLUGIN_NAME.jar" .
cd ..

JAR_SIZE=$(stat -f%z "$DIST_DIR/$PLUGIN_NAME.jar" 2>/dev/null || stat -c%s "$DIST_DIR/$PLUGIN_NAME.jar" 2>/dev/null)
echo "✅ Built $DIST_DIR/$PLUGIN_NAME.jar ($JAR_SIZE bytes)"
