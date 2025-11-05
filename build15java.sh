#!/bin/bash
set -euo pipefail

PLUGIN_NAME="TabPrefixPlugin15java"
SRC_DIR="src"
BIN_DIR="bin"
LIBS_DIR="libs"
DIST_DIR="dist"
PLUGIN_YML="plugin.yml"

echo "📦 Сборка плагина $PLUGIN_NAME (target Java 15)..."

if [ ! -d "$SRC_DIR" ]; then
  echo "❌ Ошибка: не найдена папка $SRC_DIR"; exit 1
fi

if [ ! -f "$PLUGIN_YML" ]; then
  echo "❌ Ошибка: $PLUGIN_YML не найден!"; exit 1
fi

if [ ! -d "$LIBS_DIR" ]; then
  echo "❌ Ошибка: папка $LIBS_DIR не найдена"; exit 1
fi

LIB_COUNT=$(find "$LIBS_DIR" -maxdepth 1 -name "*.jar" | wc -l)
if [ "$LIB_COUNT" -eq 0 ]; then
  echo "❌ Ошибка: в папке $LIBS_DIR нет JAR-файлов"; exit 1
fi
echo "✅ Найдено библиотек: $LIB_COUNT"

mkdir -p "$BIN_DIR"
mkdir -p "$DIST_DIR"

echo "🧹 Очистка предыдущей сборки..."
rm -rf "$BIN_DIR"/*
rm -f "$DIST_DIR/$PLUGIN_NAME.jar"

# строим classpath (для компиляции)
CLASSPATH=""
for jar in "$LIBS_DIR"/*.jar; do
  if [ -z "$CLASSPATH" ]; then
    CLASSPATH="$jar"
  else
    CLASSPATH="$CLASSPATH:$jar"
  fi
done

echo "📚 Classpath: $CLASSPATH"

# Собираем список исходников
SRC_FILES=$(find "$SRC_DIR" -name "*.java")
if [ -z "$SRC_FILES" ]; then
  echo "❌ Не найдено .java файлов в $SRC_DIR"
  exit 1
fi

# Компиляция с целевой версией Java 15
# используем --release 15 (советую) — оно гарантирует совместимость API/байткода с Java 15
echo "🧩 Компиляция (javac --release 15)..."
javac -encoding UTF-8 --release 15 -cp "$CLASSPATH" -d "$BIN_DIR" $SRC_FILES

echo "📄 Копирование plugin.yml..."
cp "$PLUGIN_YML" "$BIN_DIR/"

echo "🧰 Создание JAR..."
cd "$BIN_DIR"
jar cf "../$DIST_DIR/$PLUGIN_NAME.jar" .
cd - >/dev/null

echo "🔍 Содержимое JAR (первые строки):"
jar tf "$DIST_DIR/$PLUGIN_NAME.jar" | head -50

JAR_SIZE=$(stat -f%z "$DIST_DIR/$PLUGIN_NAME.jar" 2>/dev/null || stat -c%s "$DIST_DIR/$PLUGIN_NAME.jar" 2>/dev/null)
echo "✅ Сборка завершена: $DIST_DIR/$PLUGIN_NAME.jar ($JAR_SIZE байт)"
if [ "$JAR_SIZE" -lt 1000 ]; then
  echo "⚠️ JAR слишком маленький — проверь классы и plugin.yml"
fi
